package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.dto.NocPacketDto;
import com.cloudfuze.onboarding.dto.NocRecipientViewDto;
import com.cloudfuze.onboarding.dto.OfferFieldDto;
import com.cloudfuze.onboarding.dto.PageResponse;
import com.cloudfuze.onboarding.email.EmailMessage;
import com.cloudfuze.onboarding.email.EmailService;
import com.cloudfuze.onboarding.email.NocMailComposer;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.InvalidPortalTokenException;
import com.cloudfuze.onboarding.exception.PortalTokenExpiredException;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.model.NocPacket;
import com.cloudfuze.onboarding.model.NocStatus;
import com.cloudfuze.onboarding.model.OfferField;
import com.cloudfuze.onboarding.repository.NocPacketRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.security.PortalTokenService;
import com.cloudfuze.onboarding.storage.FileStorageService;
import com.cloudfuze.onboarding.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The NDA + NOC packet: two documents combined into one, signed once, returned
 * as one.
 *
 * <p>The merge happens at upload, before anything else, because every field
 * position HR places is expressed against pages of the combined document. The
 * two originals are not kept - the merged file is the document from that point
 * on, so what HR previews, what the recipient signs and what comes back are
 * guaranteed to be the same pages.
 */
@Service
public class NocService {

    private static final Logger log = LoggerFactory.getLogger(NocService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NocPacketRepository repository;
    private final PdfMergeService mergeService;
    private final DocumentConversionService conversionService;
    private final OfferSigningService signingService;
    private final FileStorageService storageService;
    private final FileUploadValidator uploadValidator;
    private final EmailService emailService;
    private final NocMailComposer mailComposer;
    private final PortalTokenService portalTokenService;
    private final AppProperties appProperties;
    private final HrNotifier hrNotifier;

    public NocService(NocPacketRepository repository, PdfMergeService mergeService,
                      DocumentConversionService conversionService,
                      OfferSigningService signingService, FileStorageService storageService,
                      FileUploadValidator uploadValidator, EmailService emailService,
                      NocMailComposer mailComposer, PortalTokenService portalTokenService,
                      AppProperties appProperties, HrNotifier hrNotifier) {
        this.repository = repository;
        this.mergeService = mergeService;
        this.conversionService = conversionService;
        this.signingService = signingService;
        this.storageService = storageService;
        this.uploadValidator = uploadValidator;
        this.emailService = emailService;
        this.mailComposer = mailComposer;
        this.portalTokenService = portalTokenService;
        this.appProperties = appProperties;
        this.hrNotifier = hrNotifier;
    }

    // ---------------------------------------------------------------- HR side

    /** Uploads the two documents, combines them, and files the result as a draft. */
    @Transactional
    public NocPacketDto create(MultipartFile nda, MultipartFile noc, String recipientName,
                               String recipientEmail, String title, HrPrincipal hrUser, String ipAddress) {
        // Checked before the files are read: converting and merging two
        // documents is expensive, and an address that can never be sent to
        // should fail immediately rather than after that work.
        if (!appProperties.isAllowedNocRecipient(recipientEmail)) {
            throw new BusinessRuleException("RECIPIENT_NOT_ALLOWED",
                    "NDA and NOC go to company Microsoft accounts only ("
                            + String.join(", ", appProperties.getNocRecipientDomains()) + ").");
        }

        uploadValidator.validate(nda);
        uploadValidator.validate(noc);

        // Word uploads become PDF here, so the merge and every field position
        // downstream only ever deal with one format.
        byte[] merged = mergeService.merge(List.of(conversionService.toPdf(nda), conversionService.toPdf(noc)));
        StoredFile stored = storageService.store("noc", "nda-noc-combined.pdf", "application/pdf", merged);

        NocPacket packet = new NocPacket();
        packet.setRecipientName(recipientName.trim());
        packet.setRecipientEmail(recipientEmail.trim().toLowerCase());
        packet.setTitle(title == null || title.isBlank() ? null : title.trim());
        packet.setStorageKey(stored.key());
        packet.setNdaFilename(nda.getOriginalFilename());
        packet.setNocFilename(noc.getOriginalFilename());
        packet.setPageCount(mergeService.pageCount(merged));
        packet.setSizeBytes(stored.sizeBytes());
        packet.setStatus(NocStatus.DRAFT);
        packet.setCreatedBy(hrUser.getEmail());
        repository.save(packet);

        log.info("HR {} combined {} + {} into one {}-page packet for {}", hrUser.getEmail(),
                packet.getNdaFilename(), packet.getNocFilename(), packet.getPageCount(),
                packet.getRecipientEmail());
        return toDto(packet);
    }

    /** Places (or replaces) the fields the recipient has to complete. */
    @Transactional
    public NocPacketDto saveFields(UUID id, List<OfferFieldDto> fields, HrPrincipal hrUser, String ipAddress) {
        NocPacket packet = require(id);
        requireEditable(packet);

        packet.getFields().clear();
        fields.forEach(dto -> packet.getFields().add(new OfferField(dto.type(), dto.page(), dto.xPct(),
                dto.yPct(), dto.widthPct(), dto.heightPct(), dto.prefill(), dto.textColor(), dto.textFont())));
        repository.save(packet);

        log.info("HR {} placed {} field(s) on NOC packet {}", hrUser.getEmail(), fields.size(), id);
        return toDto(packet);
    }

    /** Issues the recipient's link and emails it. */
    @Transactional
    public NocPacketDto send(UUID id, HrPrincipal hrUser, String ipAddress) {
        NocPacket packet = require(id);
        requireEditable(packet);
        if (packet.getFields().isEmpty()) {
            throw new BusinessRuleException("NO_FIELDS_PLACED",
                    "Place at least one field on the document before sending it.");
        }
        if (!packet.hasSignatureField()) {
            throw new BusinessRuleException("NO_SIGNATURE_FIELD",
                    "Add a signature field so the recipient can sign the document.");
        }

        String rawToken = newToken();
        packet.setAccessTokenHash(portalTokenService.hash(rawToken));
        packet.setAccessTokenCipher(portalTokenService.encrypt(rawToken));
        packet.setTokenExpiresAt(Instant.now().plus(appProperties.getNocTokenTtl()));
        packet.setStatus(NocStatus.SENT);
        packet.setSentAt(Instant.now());
        repository.save(packet);

        String url = appProperties.buildNocUrl(rawToken);
        EmailMessage message = mailComposer.compose(packet, url, packet.getTokenExpiresAt());
        try {
            emailService.send(message);
        } catch (RuntimeException e) {
            log.error("NDA + NOC email to {} could not be delivered: {}", packet.getRecipientEmail(),
                    e.getMessage());
            // Rolls the send back rather than leaving a packet marked sent that
            // nobody ever received.
            throw new BusinessRuleException("EMAIL_NOT_SENT",
                    "The email could not be sent right now. Please try again.");
        }

        log.info("HR {} sent the NDA + NOC packet {} to {}", hrUser.getEmail(), id, packet.getRecipientEmail());
        return withSigningUrl(toDto(packet), url);
    }

    @Transactional(readOnly = true)
    public PageResponse<NocPacketDto> list(NocStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200));
        Page<NocPacket> result = status == null
                ? repository.findAllByOrderByCreatedAtDesc(pageRequest)
                : repository.findByStatusOrderByCreatedAtDesc(status, pageRequest);
        return PageResponse.from(result, this::toDto);
    }

    /**
     * The live signing link, for HR to copy or re-send. Recovered from the
     * stored cipher - the raw token itself is never persisted.
     */
    @Transactional(readOnly = true)
    public String signingUrl(UUID id) {
        NocPacket packet = require(id);
        if (packet.getAccessTokenCipher() == null) {
            throw new BusinessRuleException("NOT_SENT_YET",
                    "This document has not been sent yet, so there is no link to show.");
        }
        return portalTokenService.decrypt(packet.getAccessTokenCipher())
                .map(appProperties::buildNocUrl)
                .orElseThrow(() -> new BusinessRuleException("LINK_NOT_RECOVERABLE",
                        "This link cannot be recovered. Send the document again to issue a new one."));
    }

    @Transactional(readOnly = true)
    public NocPacketDto detail(UUID id) {
        return toDto(require(id));
    }

    /** The bytes HR downloads: the signed version once it exists, else the merged original. */
    @Transactional(readOnly = true)
    public byte[] documentBytes(UUID id) {
        NocPacket packet = require(id);
        String key = packet.getSignedStorageKey() != null ? packet.getSignedStorageKey() : packet.getStorageKey();
        return storageService.readAllBytes(key);
    }

    @Transactional(readOnly = true)
    public String documentFilename(UUID id) {
        NocPacket packet = require(id);
        String who = packet.getRecipientName().replaceAll("[^A-Za-z0-9]+", "-");
        return (packet.isSigned() ? "NDA-NOC-signed-" : "NDA-NOC-") + who + ".pdf";
    }

    @Transactional
    public void delete(UUID id, HrPrincipal hrUser) {
        NocPacket packet = require(id);
        if (packet.isSigned()) {
            throw new BusinessRuleException("NOC_ALREADY_SIGNED",
                    "This packet has been signed and forms part of the record, so it cannot be deleted.");
        }
        repository.delete(packet);
        log.info("HR {} deleted NOC packet {}", hrUser.getEmail(), id);
    }

    // --------------------------------------------------------- recipient side

    /** Resolves a recipient link, enforcing expiry. */
    @Transactional
    public NocRecipientViewDto view(String rawToken) {
        NocPacket packet = authenticate(rawToken);
        if (packet.getStatus() == NocStatus.SENT) {
            packet.setStatus(NocStatus.VIEWED);
            packet.setViewedAt(Instant.now());
            repository.save(packet);
        }
        return new NocRecipientViewDto(
                packet.getRecipientName(),
                packet.getTitle(),
                packet.getPageCount(),
                toFieldDtos(packet),
                packet.isSigned(),
                packet.getSignedAt(),
                packet.getSignedByName(),
                packet.getTokenExpiresAt(),
                "/api/noc/" + rawToken + "/file");
    }

    @Transactional(readOnly = true)
    public byte[] recipientBytes(String rawToken) {
        NocPacket packet = authenticate(rawToken);
        String key = packet.getSignedStorageKey() != null ? packet.getSignedStorageKey() : packet.getStorageKey();
        return storageService.readAllBytes(key);
    }

    /**
     * The recipient completes every field and submits once. Their answers are
     * stamped into the combined document, which is what HR gets back.
     */
    @Transactional
    public NocRecipientViewDto sign(String rawToken, String signedByName, Map<Integer, String> values,
                                    String ipAddress) {
        NocPacket packet = authenticate(rawToken);
        if (packet.isSigned()) {
            throw new BusinessRuleException("NOC_ALREADY_SIGNED",
                    "You have already signed and submitted this document.");
        }

        List<OfferField> fields = packet.getFields();
        Map<Integer, String> answers = values == null ? Map.of() : values;
        for (int i = 0; i < fields.size(); i++) {
            String answer = answers.get(i);
            if (answer == null || answer.isBlank()) {
                throw new BusinessRuleException("FIELD_INCOMPLETE",
                        "Please complete every field before submitting.");
            }
            if (fields.get(i).getType() != null && fields.get(i).getType().isSignature()) {
                // Fails here rather than producing a document with a blank box.
                signingService.decodeSignatureImage(answer);
            }
        }

        byte[] stamped = signingService.stamp(storageService.readAllBytes(packet.getStorageKey()), fields, answers);
        StoredFile stored = storageService.store("noc", "nda-noc-signed.pdf", "application/pdf", stamped);

        packet.setSignedStorageKey(stored.key());
        packet.setSignedByName(signedByName.trim());
        packet.setSignedAt(Instant.now());
        packet.setStatus(NocStatus.SIGNED);
        repository.save(packet);

        log.info("{} signed the NDA + NOC packet {}", packet.getRecipientEmail(), packet.getId());
        hrNotifier.nocSigned(packet);
        return view(rawToken);
    }

    // ------------------------------------------------------------- internals

    private NocPacket authenticate(String rawToken) {
        if (rawToken == null || rawToken.length() < 20) {
            throw new InvalidPortalTokenException();
        }
        NocPacket packet = repository.findByAccessTokenHash(portalTokenService.hash(rawToken))
                .orElseThrow(InvalidPortalTokenException::new);
        Instant expiresAt = packet.getTokenExpiresAt();
        if (expiresAt == null) {
            throw new InvalidPortalTokenException();
        }
        if (!expiresAt.isAfter(Instant.now())) {
            throw new PortalTokenExpiredException(expiresAt);
        }
        return packet;
    }

    private void requireEditable(NocPacket packet) {
        if (packet.isSigned()) {
            throw new BusinessRuleException("NOC_ALREADY_SIGNED",
                    "This packet has already been signed and can no longer be changed.");
        }
    }

    private NocPacket require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No NOC packet with id " + id));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }


    private List<OfferFieldDto> toFieldDtos(NocPacket packet) {
        return packet.getFields().stream()
                .map(f -> new OfferFieldDto(f.getType(), f.getPage(), f.getXPct(), f.getYPct(), f.getWidthPct(),
                        f.getHeightPct(), f.getPrefill(), f.getTextColor(), f.getTextFont()))
                .toList();
    }

    /** The one moment the raw link is returned - so HR can copy it if needed. */
    private static NocPacketDto withSigningUrl(NocPacketDto dto, String signingUrl) {
        return new NocPacketDto(dto.id(), dto.recipientName(), dto.recipientEmail(), dto.title(), dto.status(),
                dto.statusLabel(), dto.ndaFilename(), dto.nocFilename(), dto.pageCount(), dto.sizeBytes(),
                dto.fields(), dto.signedCopyAvailable(), dto.sentAt(), dto.viewedAt(), dto.signedAt(),
                dto.signedByName(), dto.tokenExpiresAt(), dto.linkActive(), dto.createdBy(), dto.createdAt(),
                dto.downloadUrl(), signingUrl);
    }

    private NocPacketDto toDto(NocPacket packet) {
        return new NocPacketDto(
                packet.getId(),
                packet.getRecipientName(),
                packet.getRecipientEmail(),
                packet.getTitle(),
                packet.getStatus(),
                packet.getStatus().getLabel(),
                packet.getNdaFilename(),
                packet.getNocFilename(),
                packet.getPageCount(),
                packet.getSizeBytes(),
                toFieldDtos(packet),
                packet.getSignedStorageKey() != null,
                packet.getSentAt(),
                packet.getViewedAt(),
                packet.getSignedAt(),
                packet.getSignedByName(),
                packet.getTokenExpiresAt(),
                packet.isTokenActive(Instant.now()),
                packet.getCreatedBy(),
                packet.getCreatedAt(),
                "/api/hr/noc/" + packet.getId() + "/file",
                null);
    }

}
