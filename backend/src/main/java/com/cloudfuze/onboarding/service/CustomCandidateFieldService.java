package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.CustomCandidateFieldDto;
import com.cloudfuze.onboarding.dto.SaveCustomFieldRequest;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.FieldValidationException;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.model.CandidateField;
import com.cloudfuze.onboarding.model.CustomCandidateField;
import com.cloudfuze.onboarding.repository.CustomCandidateFieldRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detail fields an administrator invented.
 *
 * <p>The fourteen built-in fields are columns on the candidate profile, so they
 * can only be switched on and off. These are created at runtime and their
 * answers live in the profile's custom-value map, which is what lets an admin
 * add a field without a schema change or a deploy.
 */
@Service
public class CustomCandidateFieldService {

    private static final Logger log = LoggerFactory.getLogger(CustomCandidateFieldService.class);

    /** A cap, so a runaway form cannot be built by accident. */
    private static final int MAX_FIELDS = 40;

    /** Matches the column the answers are stored in. */
    private static final int MAX_ANSWER_LENGTH = 2000;

    private final CustomCandidateFieldRepository repository;

    public CustomCandidateFieldService(CustomCandidateFieldRepository repository) {
        this.repository = repository;
    }

    /** Live fields, in form order - what candidates are actually asked. */
    @Transactional(readOnly = true)
    public List<CustomCandidateField> active() {
        return repository.findByArchivedFalseOrderByPositionAscCreatedAtAsc();
    }

    /** Live fields for the candidate's form and HR's editor. */
    @Transactional(readOnly = true)
    public List<CustomCandidateFieldDto> list() {
        return active().stream().map(CustomCandidateFieldDto::from).toList();
    }

    /**
     * Everything including archived fields, for the admin screen. Archived ones
     * are still listed so an admin can see what was removed and bring it back.
     */
    @Transactional(readOnly = true)
    public List<CustomCandidateFieldDto> listForAdmin() {
        return repository.findAllByOrderByPositionAscCreatedAtAsc().stream()
                .map(CustomCandidateFieldDto::from)
                .toList();
    }

    @Transactional
    public List<CustomCandidateFieldDto> create(SaveCustomFieldRequest request, HrPrincipal actor) {
        if (repository.count() >= MAX_FIELDS) {
            throw new BusinessRuleException("TOO_MANY_FIELDS",
                    "There is a limit of " + MAX_FIELDS + " extra fields. Remove one before adding another.");
        }

        String code = CustomCandidateField.toCode(request.label());
        if (code.isEmpty()) {
            throw new FieldValidationException("Request validation failed.",
                    Map.of("label", "Give the field a label with letters or numbers in it"));
        }
        if (CustomCandidateField.isReserved(code)) {
            throw new FieldValidationException("Request validation failed.",
                    Map.of("label", "There is already a built-in field called that. Choose another label."));
        }
        if (repository.existsByCode(code)) {
            throw new FieldValidationException("Request validation failed.",
                    Map.of("label", "There is already a field called that."));
        }

        CustomCandidateField field = new CustomCandidateField(code, request.label().trim(), request.type());
        applyEditable(field, request);
        // New fields go to the end rather than jumping the queue in front of
        // fields candidates are already used to seeing.
        field.setPosition((int) repository.count());
        field.setCreatedBy(actor.getEmail());
        repository.save(field);

        log.info("Admin {} added candidate field {} ({})", actor.getEmail(), code, field.getType().getCode());
        return listForAdmin();
    }

    @Transactional
    public List<CustomCandidateFieldDto> update(UUID id, SaveCustomFieldRequest request, HrPrincipal actor) {
        CustomCandidateField field = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No custom field with id " + id));

        // The label can change freely; the code cannot, because it is what every
        // answer already collected is filed under.
        field.setLabel(request.label().trim());
        field.setType(request.type());
        applyEditable(field, request);
        repository.save(field);

        log.info("Admin {} updated candidate field {}", actor.getEmail(), field.getCode());
        return listForAdmin();
    }

    /**
     * Stops asking for a field without discarding what it has already collected
     * - the same promise the built-in fields make when switched off.
     */
    @Transactional
    public List<CustomCandidateFieldDto> setArchived(UUID id, boolean archived, HrPrincipal actor) {
        CustomCandidateField field = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No custom field with id " + id));
        field.setArchived(archived);
        if (archived) {
            // An archived field that is still required would block every
            // submission on a question nobody is being asked.
            field.setEnabled(false);
            field.setRequired(false);
        }
        repository.save(field);
        log.info("Admin {} {} candidate field {}", actor.getEmail(),
                archived ? "removed" : "restored", field.getCode());
        return listForAdmin();
    }

    private void applyEditable(CustomCandidateField field, SaveCustomFieldRequest request) {
        field.setHelpText(blankToNull(request.helpText()));
        field.setGroup(request.group() == null ? CandidateField.Group.ADDITIONAL : request.group());

        boolean enabled = request.enabled() == null || request.enabled();
        field.setEnabled(enabled);
        // Same rule as the built-in fields: off but required is unsatisfiable,
        // so it is corrected rather than allowed to block every submission.
        field.setRequired(enabled && Boolean.TRUE.equals(request.required()));

        if (field.getType().hasOptions()) {
            List<String> options = (request.options() == null ? "" : request.options()).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .distinct()
                    .toList();
            if (options.size() < 2) {
                throw new FieldValidationException("Request validation failed.",
                        Map.of("options", "List at least two choices, one per line"));
            }
            field.setOptions(String.join("\n", options));
        } else {
            field.setOptions(null);
        }
    }

    /**
     * Checks the answers to custom fields on a submission.
     *
     * <p>Required-but-empty and badly formatted are both reported here, keyed the
     * way the form names the input, so they land beside the built-in field errors
     * in one response rather than arriving as a second round of complaints.
     */
    public Map<String, String> validate(Map<String, String> submitted) {
        Map<String, String> answers = submitted == null ? Map.of() : submitted;
        Map<String, String> errors = new LinkedHashMap<>();

        for (CustomCandidateField field : active()) {
            if (!field.isEnabled()) {
                continue;
            }
            String value = answers.get(field.getCode());
            String trimmed = value == null ? null : value.trim();
            String key = "customFields." + field.getCode();

            if (trimmed == null || trimmed.isEmpty()) {
                if (field.isRequired()) {
                    errors.put(key, field.getLabel() + " is required");
                }
                continue;
            }
            if (trimmed.length() > MAX_ANSWER_LENGTH) {
                errors.put(key, field.getLabel() + " is too long");
                continue;
            }

            String typeError = field.getType().validate(trimmed);
            if (typeError != null) {
                errors.put(key, typeError);
                continue;
            }
            if (field.getType().hasOptions() && !field.optionList().contains(trimmed)) {
                errors.put(key, "Choose one of the listed options");
            }
        }
        return errors;
    }

    /**
     * The answers worth storing: trimmed, and only for fields still being asked.
     *
     * <p>Answers to fields that were since switched off or removed are left
     * untouched on the profile rather than wiped, so turning a field back on
     * shows what was already collected.
     */
    public Map<String, String> sanitise(Map<String, String> submitted) {
        Map<String, String> answers = submitted == null ? Map.of() : submitted;
        Map<String, String> clean = new LinkedHashMap<>();
        for (CustomCandidateField field : active()) {
            if (!field.isEnabled()) {
                continue;
            }
            String value = answers.get(field.getCode());
            if (value != null && !value.isBlank()) {
                clean.put(field.getCode(), value.trim());
            }
        }
        return clean;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
