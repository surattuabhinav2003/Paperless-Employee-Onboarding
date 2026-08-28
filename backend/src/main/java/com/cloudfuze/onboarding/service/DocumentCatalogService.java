package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.CustomDocumentTypeDto;
import com.cloudfuze.onboarding.dto.SaveCustomDocumentTypeRequest;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.FieldValidationException;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.model.CustomDocumentType;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.cloudfuze.onboarding.repository.CustomDocumentTypeRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place that knows what document types exist.
 *
 * <p>There are two kinds and the rest of the application should not care which
 * it is holding: the twenty-five built-in {@link DocumentType} values, which
 * carry behaviour like education-course selection, and the plain ones an
 * administrator created. Both are resolved here into the same {@link Entry},
 * keyed by a code string.
 *
 * <p>That code is why documents are matched by string rather than by enum
 * identity: a runtime type has no enum constant to be.
 */
@Service
public class DocumentCatalogService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCatalogService.class);

    /** A cap, so a runaway checklist cannot be built by accident. */
    private static final int MAX_TYPES = 60;

    /**
     * Built-in types sort by their declared order; custom ones follow, after
     * every built-in, so the canonical Class 10 → identity → employment
     * sequence a candidate works through is never interrupted.
     */
    private static final int CUSTOM_SORT_BASE = 10_000;

    private final CustomDocumentTypeRepository repository;

    public DocumentCatalogService(CustomDocumentTypeRepository repository) {
        this.repository = repository;
    }

    /**
     * One document type, whichever kind it is.
     *
     * @param builtIn the enum constant, or null for an admin-created type
     */
    public record Entry(
            String code,
            String label,
            DocumentType.Group group,
            boolean selectable,
            boolean custom,
            String description,
            DocumentType builtIn,
            int sortOrder
    ) {
        public boolean requiresCourse() {
            return builtIn != null && builtIn.requiresCourse();
        }

        public List<EducationCourse> courseOptions() {
            return builtIn == null ? List.of() : builtIn.courseOptions();
        }

        static Entry of(DocumentType type) {
            return new Entry(type.getCode(), type.getLabel(), type.group(), type.isSelectable(),
                    false, null, type, type.ordinal());
        }

        static Entry of(CustomDocumentType type) {
            return new Entry(type.getCode(), type.getLabel(), type.getGroup(),
                    type.isEnabled() && !type.isArchived(), true, type.getDescription(), null,
                    CUSTOM_SORT_BASE + type.getPosition());
        }
    }

    /* ---- reading the catalogue ---- */

    @Transactional(readOnly = true)
    public List<Entry> all() {
        List<Entry> entries = new ArrayList<>(Arrays.stream(DocumentType.values()).map(Entry::of).toList());
        repository.findAllByOrderByPositionAscCreatedAtAsc().forEach(t -> entries.add(Entry.of(t)));
        return entries;
    }

    /** Everything HR may still choose, in the order the checklist presents it. */
    @Transactional(readOnly = true)
    public List<Entry> selectable() {
        return all().stream()
                .filter(Entry::selectable)
                .sorted(java.util.Comparator.comparingInt(Entry::sortOrder))
                .toList();
    }

    /**
     * Resolves a code to its type.
     *
     * <p>An unknown code returns a placeholder rather than throwing: a type may
     * be hard-deleted from the database by hand, and a candidate whose record
     * mentions it should still load, showing an unfamiliar name rather than a
     * blank page.
     */
    @Transactional(readOnly = true)
    public Entry resolve(String code) {
        return find(code).orElseGet(() -> new Entry(code, humanise(code), DocumentType.Group.OTHER,
                false, true, null, null, CUSTOM_SORT_BASE));
    }

    @Transactional(readOnly = true)
    public Optional<Entry> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Optional<DocumentType> builtIn = DocumentType.fromCodeOrEmpty(code);
        if (builtIn.isPresent()) {
            return builtIn.map(Entry::of);
        }
        return repository.findByCode(code).map(Entry::of);
    }

    /** Refuses a code HR may not ask for - unknown, retired, or withdrawn. */
    @Transactional(readOnly = true)
    public Entry requireSelectable(String code) {
        return find(code)
                .filter(Entry::selectable)
                .orElseThrow(() -> new FieldValidationException("Request validation failed.",
                        Map.of("type", "That document type is not available")));
    }

    /* ---- administering the custom ones ---- */

    @Transactional(readOnly = true)
    public List<CustomDocumentTypeDto> listForAdmin() {
        return repository.findAllByOrderByPositionAscCreatedAtAsc().stream()
                .map(CustomDocumentTypeDto::from)
                .toList();
    }

    @Transactional
    public List<CustomDocumentTypeDto> create(SaveCustomDocumentTypeRequest request, HrPrincipal actor) {
        if (repository.count() >= MAX_TYPES) {
            throw new BusinessRuleException("TOO_MANY_DOCUMENT_TYPES",
                    "There is a limit of " + MAX_TYPES + " extra document types. Remove one before adding another.");
        }

        String code = CustomDocumentType.toCode(request.label());
        if (code.isEmpty()) {
            throw new FieldValidationException("Request validation failed.",
                    Map.of("label", "Give the document a name with letters or numbers in it"));
        }
        if (CustomDocumentType.isReserved(code)) {
            throw new FieldValidationException("Request validation failed.",
                    Map.of("label", "There is already a built-in document called that. Choose another name."));
        }
        if (repository.existsByCode(code)) {
            throw new FieldValidationException("Request validation failed.",
                    Map.of("label", "There is already a document type called that."));
        }

        CustomDocumentType type = new CustomDocumentType(code, request.label().trim(), group(request));
        applyEditable(type, request);
        type.setPosition((int) repository.count());
        type.setCreatedBy(actor.getEmail());
        repository.save(type);

        log.info("Admin {} added document type {}", actor.getEmail(), code);
        return listForAdmin();
    }

    @Transactional
    public List<CustomDocumentTypeDto> update(UUID id, SaveCustomDocumentTypeRequest request, HrPrincipal actor) {
        CustomDocumentType type = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No custom document type with id " + id));

        // The name can change freely; the code cannot, because it is what every
        // requirement and uploaded file is already filed under.
        type.setLabel(request.label().trim());
        type.setGroup(group(request));
        applyEditable(type, request);
        repository.save(type);

        log.info("Admin {} updated document type {}", actor.getEmail(), type.getCode());
        return listForAdmin();
    }

    /**
     * Withdraws a type from the picker without touching what has been collected
     * under it - the same promise made everywhere else a thing is removed.
     */
    @Transactional
    public List<CustomDocumentTypeDto> setArchived(UUID id, boolean archived, HrPrincipal actor) {
        CustomDocumentType type = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No custom document type with id " + id));
        type.setArchived(archived);
        if (archived) {
            type.setEnabled(false);
        }
        repository.save(type);
        log.info("Admin {} {} document type {}", actor.getEmail(),
                archived ? "withdrew" : "restored", type.getCode());
        return listForAdmin();
    }

    private void applyEditable(CustomDocumentType type, SaveCustomDocumentTypeRequest request) {
        type.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        type.setEnabled(request.enabled() == null || request.enabled());
    }

    private static DocumentType.Group group(SaveCustomDocumentTypeRequest request) {
        return request.group() == null ? DocumentType.Group.OTHER : request.group();
    }

    /** A readable fallback for a code with no type behind it any more. */
    private static String humanise(String code) {
        if (code == null || code.isBlank()) {
            return "Unknown document";
        }
        String spaced = code.replace('_', ' ').trim();
        return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
    }
}
