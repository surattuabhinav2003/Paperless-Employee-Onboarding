package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateDocumentRepository;
import com.cloudfuze.onboarding.repository.CandidateProfileRepository;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.OfferRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Permanent removal of records, for administrators only.
 *
 * <p>Onboarding collects Aadhaar numbers, PAN numbers, bank details and scans of
 * the documents behind them. Somebody has to be able to remove that - a
 * candidate who withdrew, a record created in error, or a person exercising
 * their right to have their data erased. Without this the only options were
 * leaving it forever or opening a database client, and the second is how a
 * cleanup query takes more than it meant to.
 *
 * <h3>What deletion does and does not touch</h3>
 * <p>Deleting a candidate takes their documents, their stored files, their
 * personal details and their offer letter. It deliberately leaves the audit
 * trail: those rows carry no foreign key precisely so they outlive the record,
 * and the deletion itself is written into them. What was destroyed, by whom,
 * and when survives the destruction - otherwise removing a candidate would also
 * remove the evidence that anyone ever did.
 *
 * <p>Signed documents are not exempt. They are the most consequential thing
 * here, so the caller has to be an administrator and the interface asks them to
 * type the name back; but a rule that says a signed record can never be removed
 * cannot honour an erasure request, and that is not a position this can take.
 */
@Service
public class RecordDeletionService {

    private static final Logger log = LoggerFactory.getLogger(RecordDeletionService.class);

    private final CandidateRepository candidateRepository;
    private final CandidateDocumentRepository documentRepository;
    private final CandidateProfileRepository profileRepository;
    private final OfferRepository offerRepository;
    private final FileStorageService storageService;
    private final AuditService auditService;

    public RecordDeletionService(CandidateRepository candidateRepository,
                                 CandidateDocumentRepository documentRepository,
                                 CandidateProfileRepository profileRepository,
                                 OfferRepository offerRepository,
                                 FileStorageService storageService,
                                 AuditService auditService) {
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.profileRepository = profileRepository;
        this.offerRepository = offerRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    /** What removing this candidate would destroy, so it can be said out loud first. */
    @Transactional(readOnly = true)
    public Map<String, Object> candidatePreview(UUID candidateId) {
        Candidate candidate = require(candidateId);
        List<CandidateDocument> documents = documentRepository
                .findByCandidateIdOrderByUploadedAtAsc(candidateId);
        Offer offer = offerRepository.findByCandidateId(candidateId).orElse(null);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("candidateId", candidateId.toString());
        preview.put("name", candidate.getName());
        preview.put("email", candidate.getEmail());
        preview.put("stage", candidate.getStage().getCode());
        preview.put("documents", documents.size());
        preview.put("hasProfile", profileRepository.existsByCandidateId(candidateId));
        preview.put("hasOffer", offer != null);
        preview.put("offerSigned", offer != null && offer.getSignedStorageKey() != null);
        return preview;
    }

    /**
     * Removes a candidate and everything that belongs only to them.
     *
     * <p>Children go first: the database has foreign keys pointing at the
     * candidate, so the order is not cosmetic.
     */
    @Transactional
    public void deleteCandidate(UUID candidateId, HrPrincipal hrUser, String ipAddress) {
        Candidate candidate = require(candidateId);
        List<CandidateDocument> documents = documentRepository
                .findByCandidateIdOrderByUploadedAtAsc(candidateId);
        Offer offer = offerRepository.findByCandidateId(candidateId).orElse(null);

        /*
         * Written before the rows go, while there is still something to describe.
         * Recorded against a null candidate for the same reason it is recorded at
         * all: the candidate is about to stop existing, and the entry has to
         * outlive them.
         */
        auditService.record(null, AuditEventType.CANDIDATE_DELETED, hrUser.getEmail(),
                com.cloudfuze.onboarding.model.ActorType.HR, ipAddress, null,
                Map.of("candidateId", candidateId.toString(),
                        "name", String.valueOf(candidate.getName()),
                        "email", String.valueOf(candidate.getEmail()),
                        "documents", documents.size(),
                        "offerSigned", offer != null && offer.getSignedStorageKey() != null));

        documents.forEach(document -> deleteStored(document.getStorageKey()));
        documentRepository.deleteAll(documents);

        if (offer != null) {
            deleteOfferFiles(offer);
            offerRepository.delete(offer);
        }
        profileRepository.findByCandidateId(candidateId).ifPresent(profileRepository::delete);
        candidateRepository.delete(candidate);

        log.info("Administrator {} deleted candidate {} <{}> and {} document(s)",
                hrUser.getEmail(), candidate.getName(), candidate.getEmail(), documents.size());
    }

    /**
     * Removes only the offer letter, leaving the candidate and their documents.
     *
     * <p>For an offer sent in error, or one that has to be reissued. A candidate
     * whose offer was accepted goes back to being approved and awaiting one -
     * leaving them "complete" with no letter behind it would be a record that
     * contradicts itself.
     */
    @Transactional
    public void deleteOffer(UUID candidateId, HrPrincipal hrUser, String ipAddress) {
        Candidate candidate = require(candidateId);
        Offer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "There is no offer letter on this candidate to delete."));

        boolean wasSigned = offer.getSignedStorageKey() != null;
        auditService.recordHrEvent(candidateId, AuditEventType.OFFER_DELETED, hrUser.getEmail(),
                ipAddress, offer.getOriginalFilename(), Map.of("wasSigned", wasSigned));

        deleteOfferFiles(offer);
        offerRepository.delete(offer);

        if (candidate.getStage() == Stage.OFFER_ACCEPTED) {
            candidate.setStage(Stage.DOCS_APPROVED);
            candidate.setCompletedAt(null);
            candidateRepository.save(candidate);
        }

        log.info("Administrator {} deleted the {} offer letter for {}",
                hrUser.getEmail(), wasSigned ? "signed" : "unsigned", candidate.getEmail());
    }

    private void deleteOfferFiles(Offer offer) {
        deleteStored(offer.getStorageKey());
        deleteStored(offer.getSignedStorageKey());
    }

    /**
     * A file that has already gone must not stop the record going.
     *
     * <p>Storage and the database can disagree - a file removed by hand, or a
     * local disk wiped by a redeploy. Failing here would leave a candidate who
     * cannot be deleted at all.
     */
    private void deleteStored(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not remove stored file {}: {}", key, e.getMessage());
        }
    }

    private Candidate require(UUID candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> ResourceNotFoundException.candidate(candidateId));
    }
}
