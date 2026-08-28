package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.AuditLogRepository;
import com.cloudfuze.onboarding.repository.CandidateDocumentRepository;
import com.cloudfuze.onboarding.repository.CandidateProfileRepository;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.HrUserRepository;
import com.cloudfuze.onboarding.repository.OfferRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the gated onboarding workflow, driven through the real
 * HTTP API: HR authentication, portal tokens, document review, and the offer
 * gate whose acceptance completes onboarding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingWorkflowIntegrationTest {

    private static final String HR_EMAIL = "hr.tester@cloudfuze.com";
    private static final String HR_PASSWORD = "TestPassw0rd!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private HrUserRepository hrUserRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloudfuze.onboarding.repository.CandidateFieldSettingRepository candidateFieldSettingRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloudfuze.onboarding.repository.CustomCandidateFieldRepository customCandidateFieldRepository;
    @Autowired
    private CandidateRepository candidateRepository;
    @Autowired
    private CandidateDocumentRepository documentRepository;
    @Autowired
    private CandidateProfileRepository profileRepository;
    @Autowired
    private OfferRepository offerRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        profileRepository.deleteAll();
        documentRepository.deleteAll();
        offerRepository.deleteAll();
        candidateRepository.deleteAll();
        hrUserRepository.deleteAll();
        // Admin field settings are global, so one test switching a field off
        // would otherwise change what every later test considers required.
        candidateFieldSettingRepository.deleteAll();
        customCandidateFieldRepository.deleteAll();

        hrUserRepository.save(new HrUser(HR_EMAIL, passwordEncoder.encode(HR_PASSWORD),
                "HR Tester", "HR Operations"));
        hrToken = login(HR_EMAIL, HR_PASSWORD);
    }

    // ------------------------------------------------------------------
    // 1-2. Candidate creation and secure token generation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("HR login issues a JWT and rejects wrong credentials")
    void hrAuthentication() throws Exception {
        assertThat(hrToken).isNotBlank();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"WrongPassw0rd!"}""".formatted(HR_EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(get("/api/hr/candidates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Creating a candidate stores only the token hash and starts at docs_pending")
    void candidateCreationAndTokenGeneration() throws Exception {
        Onboarding onboarding = createCandidate("token.hash@example.com");

        Candidate candidate = candidateRepository.findById(onboarding.candidateId()).orElseThrow();
        assertThat(candidate.getStage()).isEqualTo(Stage.DOCS_PENDING);
        assertThat(candidate.getInviteTokenHash())
                .isNotBlank()
                .isNotEqualTo(onboarding.token())
                .hasSize(64)
                .doesNotContain(onboarding.token());
        // The recoverable copy is ciphertext, so a database dump still cannot be
        // replayed against the portal.
        assertThat(candidate.getInviteTokenCipher())
                .isNotBlank()
                .isNotEqualTo(onboarding.token())
                .doesNotContain(onboarding.token());
        assertThat(candidate.getTokenExpiresAt()).isAfter(Instant.now());
        assertThat(onboarding.token().length()).isGreaterThanOrEqualTo(40);
        assertThat(candidate.getInvitationCount()).isEqualTo(1);

        // Duplicate email is rejected.
        mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("token.hash@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("Validation rejects an incomplete candidate form")
    void candidateValidation() throws Exception {
        mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","role":"","department":"",
                                 "requiredDocuments":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.requiredDocuments").exists());
    }

    @Test
    @DisplayName("Pipeline listing filters by search text and by stage")
    void pipelineSearchAndStageFilter() throws Exception {
        Onboarding first = createCandidate("pipeline.one@example.com");
        createCandidate("pipeline.two@example.com");

        mockMvc.perform(get("/api/hr/candidates").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].documentsRequired").value(4));

        mockMvc.perform(get("/api/hr/candidates").param("q", "pipeline.one")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("pipeline.one@example.com"));

        mockMvc.perform(get("/api/hr/candidates").param("q", "no-such-candidate")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/hr/candidates").param("stage", "docs_pending")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        approveAllDocuments(first);

        mockMvc.perform(get("/api/hr/candidates").param("stage", "docs_approved")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("pipeline.one@example.com"));

        mockMvc.perform(get("/api/hr/candidates").param("stage", "docs_pending")
                        .param("q", "pipeline")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ------------------------------------------------------------------
    // 3-4. Token validity
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Unknown portal tokens are rejected")
    void invalidTokenRejected() throws Exception {
        createCandidate("invalid.token@example.com");

        mockMvc.perform(get("/api/portal/{token}", "this-token-was-never-issued-000000000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PORTAL_TOKEN"));
    }

    @Test
    @DisplayName("Expired portal tokens are rejected")
    void expiredTokenRejected() throws Exception {
        Onboarding onboarding = createCandidate("expired.token@example.com");

        Candidate candidate = candidateRepository.findById(onboarding.candidateId()).orElseThrow();
        candidate.setTokenExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        candidateRepository.saveAndFlush(candidate);

        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PORTAL_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("Regenerating the portal link invalidates the previous token")
    void tokenRegenerationInvalidatesOldToken() throws Exception {
        Onboarding onboarding = createCandidate("regenerate@example.com");

        mockMvc.perform(get("/api/portal/{token}", onboarding.token())).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/hr/candidates/{id}/regenerate-token",
                        onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        String newToken = tokenFromUrl(json(result).path("portalUrl").asText());

        assertThat(newToken).isNotEqualTo(onboarding.token());
        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PORTAL_TOKEN"));
        mockMvc.perform(get("/api/portal/{token}", newToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HR can re-read an active portal link without invalidating it")
    void hrCanShowTheActiveLink() throws Exception {
        Onboarding onboarding = createCandidate("show.link@example.com");

        MvcResult shown = mockMvc.perform(get("/api/hr/candidates/{id}/portal-link", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailedTo").value("show.link@example.com"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();

        // Exactly the link the candidate was sent, and it still works.
        String shownToken = tokenFromUrl(json(shown).path("portalUrl").asText());
        assertThat(shownToken).isEqualTo(onboarding.token());
        mockMvc.perform(get("/api/portal/{token}", shownToken)).andExpect(status().isOk());

        // Showing it is recorded, since it exposes the credential.
        MvcResult audit = mockMvc.perform(get("/api/hr/candidates/{id}/audit", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode viewed = null;
        for (JsonNode entry : json(audit)) {
            if ("portal_link_viewed".equals(entry.path("eventType").asText())) {
                viewed = entry;
                break;
            }
        }
        assertThat(viewed).as("portal_link_viewed audit entry").isNotNull();
        assertThat(viewed.path("actorType").asText()).isEqualTo("hr");
        assertThat(viewed.path("actor").asText()).isEqualTo(HR_EMAIL);

        // An expired link is not shown - it has to be replaced.
        Candidate candidate = candidateRepository.findById(onboarding.candidateId()).orElseThrow();
        candidate.setTokenExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        candidateRepository.saveAndFlush(candidate);
        mockMvc.perform(get("/api/hr/candidates/{id}/portal-link", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));

        // And a link with no recoverable copy says so rather than failing blindly.
        candidate.setTokenExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        candidate.setInviteTokenCipher(null);
        candidateRepository.saveAndFlush(candidate);
        mockMvc.perform(get("/api/hr/candidates/{id}/portal-link", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINK_NOT_RECOVERABLE"));
    }

    // ------------------------------------------------------------------
    // 5-8. Documents: upload, verify, reject, re-upload
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Candidate sees only requested documents and can upload them")
    void documentUpload() throws Exception {
        Onboarding onboarding = createCandidate("upload@example.com");

        mockMvc.perform(get("/api/portal/{token}/documents", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(4))
                // Canonical order: Class 10, higher education, then identity.
                .andExpect(jsonPath("$.documents[0].type").value("ssc_certificate"))
                .andExpect(jsonPath("$.documents[1].type").value("higher_education_provisional"))
                .andExpect(jsonPath("$.documents[2].type").value("aadhaar_id"))
                .andExpect(jsonPath("$.documents[3].type").value("pan_card"))
                .andExpect(jsonPath("$.documents[0].status").value("pending"))
                .andExpect(jsonPath("$.uploadAllowed").value(true));

        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.submitted").value(1));

        // A document that was not requested cannot be uploaded.
        upload(onboarding.token(), "passport_photo", "photo.png")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_REQUESTED"));

        // Before the pack is handed to HR, the candidate may fix a mistake by
        // replacing what they uploaded - it stays one submitted document.
        upload(onboarding.token(), "aadhaar_id", "aadhaar-again.pdf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.submitted").value(1))
                .andExpect(jsonPath("$.documents[2].version").value(2));
    }

    @Test
    @DisplayName("Unsupported file types and oversized files are rejected")
    void documentUploadValidation() throws Exception {
        Onboarding onboarding = createCandidate("filetype@example.com");

        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", onboarding.token(), "aadhaar_id")
                        .file(new MockMultipartFile("file", "malware.exe",
                                "application/octet-stream", "binary".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));

        byte[] oversized = new byte[11 * 1024 * 1024];
        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", onboarding.token(), "aadhaar_id")
                        .file(new MockMultipartFile("file", "huge.pdf", "application/pdf", oversized)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("HR verification and rejection update document state; rejection needs a reason")
    void documentVerificationAndRejection() throws Exception {
        Onboarding onboarding = createCandidate("review@example.com");
        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "pan_card", "pan.pdf").andExpect(status().isOk());

        UUID aadhaarId = documentId(onboarding.candidateId(), "aadhaar_id");
        UUID panId = documentId(onboarding.candidateId(), "pan_card");

        mockMvc.perform(post("/api/hr/documents/{id}/verify", aadhaarId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());
        assertThat(documentRepository.findById(aadhaarId).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.VERIFIED);
        assertThat(documentRepository.findById(aadhaarId).orElseThrow().getReviewedBy()).isEqualTo(HR_EMAIL);

        // Rejection without a reason is refused.
        mockMvc.perform(post("/api/hr/documents/{id}/reject", panId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        reject(panId, "The PAN card scan is blurred. Please upload a clearer copy.")
                .andExpect(status().isOk());
        assertThat(documentRepository.findById(panId).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.REJECTED);

        // Verifying an already-verified document is refused.
        mockMvc.perform(post("/api/hr/documents/{id}/verify", aadhaarId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_REVIEWABLE"));
    }

    @Test
    @DisplayName("Only the rejected document reopens for re-upload, and it comes back as submitted")
    void rejectedDocumentCanBeReuploaded() throws Exception {
        Onboarding onboarding = createCandidate("reupload@example.com");
        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "pan_card", "pan.pdf").andExpect(status().isOk());

        UUID aadhaarId = documentId(onboarding.candidateId(), "aadhaar_id");
        UUID panId = documentId(onboarding.candidateId(), "pan_card");
        verify(aadhaarId);
        reject(panId, "Blurred scan - please re-upload a clearer copy.").andExpect(status().isOk());

        // Canonical order puts the identity documents after education, so Aadhaar
        // and PAN are entries 3 and 4.
        mockMvc.perform(get("/api/portal/{token}/documents", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[2].type").value("aadhaar_id"))
                .andExpect(jsonPath("$.documents[2].status").value("verified"))
                .andExpect(jsonPath("$.documents[2].uploadAllowed").value(false))
                .andExpect(jsonPath("$.documents[3].type").value("pan_card"))
                .andExpect(jsonPath("$.documents[3].status").value("rejected"))
                .andExpect(jsonPath("$.documents[3].uploadAllowed").value(true))
                .andExpect(jsonPath("$.documents[3].rejectReason").isNotEmpty());

        // The verified document stays locked.
        upload(onboarding.token(), "aadhaar_id", "aadhaar-v2.pdf")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_VERIFIED"));

        upload(onboarding.token(), "pan_card", "pan-clear.pdf").andExpect(status().isOk());
        var reuploaded = documentRepository.findById(panId).orElseThrow();
        assertThat(reuploaded.getStatus()).isEqualTo(DocumentStatus.SUBMITTED);
        assertThat(reuploaded.getVersion()).isEqualTo(2);
        assertThat(reuploaded.getRejectReason()).isNull();
        assertThat(candidateRepository.findById(onboarding.candidateId()).orElseThrow().getStage())
                .isEqualTo(Stage.DOCS_PENDING);
    }

    @Test
    @DisplayName("Verifying every mandatory document never moves the stage by itself - only HR's explicit approval does")
    void stageOnlyAdvancesWhenHrExplicitlyApproves() throws Exception {
        Onboarding onboarding = createCandidate("approval@example.com");
        upload(onboarding.token(), "ssc_certificate", "class10.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "higher_education_provisional", "provisional.pdf", "b_tech")
                .andExpect(status().isOk());
        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "pan_card", "pan.pdf").andExpect(status().isOk());

        verify(documentId(onboarding.candidateId(), "ssc_certificate"));
        verify(documentId(onboarding.candidateId(), "higher_education_provisional"));
        verify(documentId(onboarding.candidateId(), "aadhaar_id"));
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_PENDING);

        // Everything verified, but the candidate has neither filled in their details
        // nor handed the pack to HR, so there is nothing to approve yet.
        verify(documentId(onboarding.candidateId(), "pan_card"));
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_PENDING);
        mockMvc.perform(post("/api/hr/candidates/{id}/notify", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_SUBMITTED"));

        submitProfile(onboarding.token()).andExpect(status().isOk());
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_PENDING);

        // Every mandatory document is verified and the pack is with HR - the
        // candidate is now eligible, but still not approved until HR acts.
        submitForReview(onboarding.token()).andExpect(status().isOk());
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_PENDING);
        mockMvc.perform(get("/api/hr/candidates/{id}", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(jsonPath("$.candidate.readyForApproval").value(true));

        // HR can still change their mind on a document at this point - nothing
        // is locked until they approve.
        UUID pan = documentId(onboarding.candidateId(), "pan_card");
        mockMvc.perform(post("/api/hr/documents/{id}/reopen", pan)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_PENDING);
        mockMvc.perform(get("/api/hr/candidates/{id}", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(jsonPath("$.candidate.readyForApproval").value(false));
        verify(pan);

        // Now HR deliberately approves - this is the only thing that moves the stage.
        approve(onboarding.candidateId());
        assertThat(candidateRepository.findById(onboarding.candidateId()).orElseThrow().getDocsApprovedAt())
                .isNotNull();

        // Uploads and document review both close once documents are approved.
        upload(onboarding.token(), "aadhaar_id", "another.pdf")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAGE_FORBIDDEN"));
        mockMvc.perform(post("/api/hr/documents/{id}/reopen", pan)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_REVIEW_CLOSED"));

        // Approval is one-shot - there is no resending the approval email.
        mockMvc.perform(post("/api/hr/candidates/{id}/notify", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_APPROVED"));
    }

    // ------------------------------------------------------------------
    // Personal and education details
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Candidate submits personal and education details, and HR can read them")
    void candidateDetails() throws Exception {
        Onboarding onboarding = createCandidate("details@example.com");

        mockMvc.perform(get("/api/portal/{token}/profile", onboarding.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.profileSubmitted").value(false))
                .andExpect(jsonPath("$.steps[0].title").value("Details & Documents"))
                .andExpect(jsonPath("$.steps[0].statusText").value("Your details are needed"));

        submitProfile(onboarding.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullNameAsPerAadhaar").value("Priya Sharma"))
                .andExpect(jsonPath("$.bloodGroupLabel").value("O+"))
                .andExpect(jsonPath("$.revision").value(1));

        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.profileSubmitted").value(true));

        // Re-submitting while documents are open is an update, not a duplicate.
        submitProfile(onboarding.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));

        mockMvc.perform(get("/api/hr/candidates/{id}/profile", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalEmail").value("priya.personal@example.com"))
                .andExpect(jsonPath("$.contactNumber").value("9876543210"))
                .andExpect(jsonPath("$.fathersName").value("Rakesh Sharma"))
                .andExpect(jsonPath("$.permanentAddress")
                        .value("12-4-56 Banjara Hills, Hyderabad, Telangana 500034"));

        mockMvc.perform(get("/api/hr/candidates/{id}", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(jsonPath("$.profile.dateOfBirth").value("2001-04-17"))
                .andExpect(jsonPath("$.profile.genderLabel").value("Female"));
    }

    @Test
    @DisplayName("Details validation rejects bad numbers, future birth dates and missing fields")
    void candidateDetailsValidation() throws Exception {
        Onboarding onboarding = createCandidate("details.validation@example.com");

        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullNameAsPerAadhaar":"","personalEmail":"not-an-email",
                                 "contactNumber":"12","dateOfBirth":"2099-01-01",
                                 "permanentAddress":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fullNameAsPerAadhaar").exists())
                .andExpect(jsonPath("$.fieldErrors.personalEmail").exists())
                .andExpect(jsonPath("$.fieldErrors.contactNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").exists())
                .andExpect(jsonPath("$.fieldErrors.gender").exists())
                .andExpect(jsonPath("$.fieldErrors.bloodGroup").exists())
                .andExpect(jsonPath("$.fieldErrors.permanentAddress").exists())
                .andExpect(jsonPath("$.fieldErrors.fathersName").exists())
                .andExpect(jsonPath("$.fieldErrors.aadhaarNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.panNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.emergencyContactName").exists())
                .andExpect(jsonPath("$.fieldErrors.emergencyContactRelation").exists())
                .andExpect(jsonPath("$.fieldErrors.emergencyContactNumber").exists());
    }

    @Test
    @DisplayName("Emergency contact number is refused when it matches the candidate's own numbers")
    void emergencyContactNumberMustDifferFromOwnNumbers() throws Exception {
        Onboarding onboarding = createCandidate("emergency.contact@example.com");

        // Same as the primary contact number.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload().replace("\"9123456780\"", "\"9876543210\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.emergencyContactNumber").exists());

        // Same as the alternate contact number.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload().replace("\"9123456780\"", "\"9876500000\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.emergencyContactNumber").exists());

        // A genuinely different number is accepted.
        submitProfile(onboarding.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyContactNumber").value("9123456780"))
                .andExpect(jsonPath("$.emergencyContactRelationLabel").value("Mother"))
                .andExpect(jsonPath("$.aadhaarNumber").value("234567890123"))
                .andExpect(jsonPath("$.panNumber").value("ABCDE1234F"));
    }

    @Test
    @DisplayName("Details are frozen once HR has approved the document stage")
    void detailsLockedAfterApproval() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("details.locked@example.com"));

        submitProfile(onboarding.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_LOCKED"));
    }

    @Test
    @DisplayName("Documents are offered in checklist order with retired types hidden")
    void documentChecklistOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode meta = json(result);

        List<String> offered = new ArrayList<>();
        meta.path("documentTypes").forEach(option -> offered.add(option.path("value").asText()));

        // Exactly the sequence HR works through: Class 10, secondary, higher
        // education (provisional, then the original degree once it arrives,
        // then the marksheet), Aadhaar, PAN, then the rest.
        assertThat(offered).startsWith("ssc_certificate", "secondary_education_certificate",
                "higher_education_provisional", "higher_education_original_degree",
                "higher_education_marksheet", "aadhaar_id", "pan_card");
        assertThat(offered).doesNotContain("education_certificate", "intermediate_certificate",
                "diploma_certificate", "ug_degree_certificate", "pg_degree_certificate");

        Map<String, String> labels = optionLabels(meta, "documentTypes");
        assertThat(labels)
                .containsEntry("ssc_certificate", "Class 10 (SSC) Certificate")
                .containsEntry("secondary_education_certificate", "Secondary Education Certificate")
                .containsEntry("higher_education_provisional", "Higher Education - Provisional Certificate")
                .containsEntry("higher_education_original_degree", "Higher Education - Original Degree Certificate")
                .containsEntry("higher_education_marksheet", "Higher Education - Semester Marksheet");

        assertThat(optionLabels(meta, "educationCourses"))
                .containsEntry("intermediate", "Class 12 / Intermediate")
                .containsEntry("diploma", "Diploma")
                .containsEntry("polytechnic", "Polytechnic")
                .containsEntry("b_tech", "B.Tech")
                .containsEntry("m_tech", "M.Tech");
        assertThat(optionGroups(meta, "educationCourses"))
                .containsEntry("diploma", "Secondary education")
                .containsEntry("b_tech", "Undergraduate")
                .containsEntry("mba", "Postgraduate");
    }

    @Test
    @DisplayName("Education documents carry a course selection, validated per level")
    void educationDocumentsCarryCourse() throws Exception {
        Onboarding onboarding = createCandidate("course@example.com");

        MvcResult listed = mockMvc.perform(get("/api/portal/{token}/documents", onboarding.token()))
                .andExpect(status().isOk())
                // Class 10 needs no selection; higher education does.
                .andExpect(jsonPath("$.documents[0].requiresCourse").value(false))
                .andExpect(jsonPath("$.documents[0].courseOptions").isEmpty())
                .andExpect(jsonPath("$.documents[1].requiresCourse").value(true))
                .andExpect(jsonPath("$.documents[2].requiresCourse").value(false))
                .andReturn();

        List<String> higherEducationCourses = new ArrayList<>();
        json(listed).path("documents").path(1).path("courseOptions")
                .forEach(option -> higherEducationCourses.add(option.path("value").asText()));
        assertThat(higherEducationCourses)
                .contains("b_tech", "b_e", "m_tech", "mba", "phd")
                .doesNotContain("diploma", "polytechnic", "intermediate");

        // Class 10 takes no course.
        upload(onboarding.token(), "ssc_certificate", "class10.pdf", "b_tech")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_APPLICABLE"));

        // Higher education must say which course.
        upload(onboarding.token(), "higher_education_provisional", "provisional.pdf")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_REQUIRED"));

        // A secondary course is not valid for a higher-education document.
        upload(onboarding.token(), "higher_education_provisional", "provisional.pdf", "diploma")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_ALLOWED"));

        upload(onboarding.token(), "higher_education_provisional", "provisional.pdf", "m_tech")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[1].course").value("m_tech"))
                .andExpect(jsonPath("$.documents[1].courseLabel").value("M.Tech"));

        // HR sees the selection on the review list.
        mockMvc.perform(get("/api/hr/candidates/{id}/documents", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].courseLabel").value("M.Tech"));
    }

    @Test
    @DisplayName("Candidate reviews and submits the pack, and it locks afterwards")
    void reviewAndSubmit() throws Exception {
        Onboarding onboarding = createCandidate("submit@example.com");

        // Nothing reaches HR until the checklist is complete, and the API says
        // exactly what is still outstanding.
        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.readyToSubmit").value(false))
                .andExpect(jsonPath("$.submittedForReview").value(false))
                .andExpect(jsonPath("$.outstandingItems.length()").value(5));

        submitForReview(onboarding.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBMISSION_INCOMPLETE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Fill in your personal details")));

        submitProfile(onboarding.token()).andExpect(status().isOk());
        upload(onboarding.token(), "ssc_certificate", "class10.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "higher_education_provisional", "provisional.pdf", "b_tech")
                .andExpect(status().isOk());
        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf").andExpect(status().isOk());

        // One mandatory document short: still not submittable.
        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.readyToSubmit").value(false))
                .andExpect(jsonPath("$.outstandingItems[0]").value("Upload PAN Card"));

        upload(onboarding.token(), "pan_card", "pan.pdf").andExpect(status().isOk());
        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.readyToSubmit").value(true))
                .andExpect(jsonPath("$.outstandingItems").isEmpty())
                .andExpect(jsonPath("$.steps[0].statusText").value("Ready to submit"));

        submitForReview(onboarding.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedForReview").value(true))
                .andExpect(jsonPath("$.submittedForReviewAt").isNotEmpty())
                .andExpect(jsonPath("$.readyToSubmit").value(false))
                .andExpect(jsonPath("$.profileEditable").value(false))
                .andExpect(jsonPath("$.steps[0].statusText").value("Submitted - with HR"));

        // Submitting twice or editing the details afterwards are both refused.
        submitForReview(onboarding.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
        submitProfile(onboarding.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_LOCKED"));

        // A document HR sends back is the one thing that may still be replaced.
        UUID aadhaarId = documentId(onboarding.candidateId(), "aadhaar_id");
        reject(aadhaarId, "The Aadhaar scan is cut off. Please upload the full card.")
                .andExpect(status().isOk());
        upload(onboarding.token(), "aadhaar_id", "aadhaar-v2.pdf").andExpect(status().isOk());

        // HR can see when the pack was handed over.
        mockMvc.perform(get("/api/hr/candidates/{id}", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(jsonPath("$.candidate.submittedForReviewAt").isNotEmpty());
    }

    @Test
    @DisplayName("Turning a candidate detail field off stops it being required")
    void candidateFieldsAreConfigurable() throws Exception {
        Onboarding onboarding = createCandidate("field.config@example.com");
        // Strip the field from an otherwise complete payload, so the only
        // variable between the two attempts below is the admin setting.
        String withoutBloodGroup = profilePayload()
                .replaceAll(",\\s*\"bloodGroup\"\\s*:\\s*\"[a-z_]+\"", "");

        // Required by default, so omitting it is refused - and the error is
        // attributed to the field, not a generic failure.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withoutBloodGroup))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bloodGroup").exists());

        // An admin turns it off.
        HrUser admin = hrUserRepository.findByEmailIgnoreCase(HR_EMAIL).orElseThrow();
        admin.setRole(com.cloudfuze.onboarding.model.HrRole.ADMIN);
        hrUserRepository.save(admin);
        mockMvc.perform(put("/api/hr/admin/candidate-fields/{field}", "blood_group")
                        .header("Authorization", "Bearer " + login(HR_EMAIL, HR_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false,"required":false}"""))
                .andExpect(status().isOk());

        // The very same submission is now accepted.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withoutBloodGroup))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloodGroup").doesNotExist());
    }

    @Test
    @DisplayName("Admin settings are refused to ordinary HR, and admins cannot lock themselves out")
    void adminAccessIsGatedAndCannotBeLostAccidentally() throws Exception {
        // The seeded user is a plain HR account in tests.
        mockMvc.perform(get("/api/hr/admin/users").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Ordinary work is untouched by the new gate.
        mockMvc.perform(get("/api/hr/candidates").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());

        // Promote them and sign in again so the token carries the new role.
        HrUser user = hrUserRepository.findByEmailIgnoreCase(HR_EMAIL).orElseThrow();
        user.setRole(com.cloudfuze.onboarding.model.HrRole.ADMIN);
        hrUserRepository.save(user);
        String adminToken = login(HR_EMAIL, HR_PASSWORD);

        MvcResult listed = mockMvc.perform(get("/api/hr/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(listed).findValuesAsText("email")).contains(HR_EMAIL);

        // Removing your own admin is refused - it would lock you out of the
        // very screen you are standing on.
        mockMvc.perform(put("/api/hr/admin/users/{id}/role", user.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"hr"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DEMOTE_SELF"));

        assertThat(hrUserRepository.findByEmailIgnoreCase(HR_EMAIL).orElseThrow().isAdmin()).isTrue();
    }

    @Test
    @DisplayName("An admin can add someone as an admin before they have ever signed in")
    void adminCanAddOtherAdmins() throws Exception {
        // Adding is admin-only, like every other setting. Checked first, while
        // the seeded user is still a plain HR account.
        mockMvc.perform(post("/api/hr/admin/users")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"erik@cloudfuze.com","role":"admin"}"""))
                .andExpect(status().isForbidden());
        assertThat(hrUserRepository.findByEmailIgnoreCase("erik@cloudfuze.com")).isEmpty();

        HrUser user = hrUserRepository.findByEmailIgnoreCase(HR_EMAIL).orElseThrow();
        user.setRole(com.cloudfuze.onboarding.model.HrRole.ADMIN);
        hrUserRepository.save(user);
        String adminToken = login(HR_EMAIL, HR_PASSWORD);

        mockMvc.perform(post("/api/hr/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Erik@CloudFuze.com","role":"admin"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("erik@cloudfuze.com"))
                .andExpect(jsonPath("$.role").value("admin"))
                // Never signed in, so the screen can mark them as pending.
                .andExpect(jsonPath("$.lastLoginAt").doesNotExist());

        HrUser added = hrUserRepository.findByEmailIgnoreCase("erik@cloudfuze.com").orElseThrow();
        assertThat(added.isAdmin()).isTrue();
        // The stored password must be unusable, or adding a person would quietly
        // open the email/password path for an SSO-only account.
        assertThat(passwordEncoder.matches("", added.getPasswordHash())).isFalse();

        // The same address twice is refused rather than creating a second row.
        mockMvc.perform(post("/api/hr/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"erik@cloudfuze.com","role":"hr"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));

        // A malformed address never reaches the database.
        mockMvc.perform(post("/api/hr/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","role":"hr"}"""))
                .andExpect(status().isBadRequest());
        assertThat(hrUserRepository.findByEmailIgnoreCase("not-an-email")).isEmpty();
    }

    @Test
    @DisplayName("An admin can invent a detail field, and removing it keeps the answers already given")
    void adminCanCreateCustomCandidateFields() throws Exception {
        HrUser admin = hrUserRepository.findByEmailIgnoreCase(HR_EMAIL).orElseThrow();
        admin.setRole(com.cloudfuze.onboarding.model.HrRole.ADMIN);
        hrUserRepository.save(admin);
        String adminToken = login(HR_EMAIL, HR_PASSWORD);

        // A choice field, required, with the options written one per line.
        mockMvc.perform(post("/api/hr/admin/custom-fields")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"T-shirt size","type":"select",
                                 "options":"Small\\nMedium\\nLarge",
                                 "group":"additional","enabled":true,"required":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].code").value("t_shirt_size"))
                .andExpect(jsonPath("$[0].options.length()").value(3));

        // It reaches the candidate's form through the same metadata the
        // built-in fields use, so nothing has to be wired up per field.
        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customCandidateFields[0].label").value("T-shirt size"));

        Onboarding onboarding = createCandidate("custom.field@example.com");

        // Required, so an otherwise complete submission is refused - and the
        // error is attributed to the field rather than the request as a whole.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['customFields.t_shirt_size']").exists());

        // An answer that is not one of the choices is refused too, or the list
        // would be a suggestion rather than a constraint.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayloadWithCustom("{\"t_shirt_size\":\"Enormous\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['customFields.t_shirt_size']").exists());

        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayloadWithCustom("{\"t_shirt_size\":\"Medium\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customFields.t_shirt_size").value("Medium"));

        // Removing the field takes it off the form...
        String fieldId = customCandidateFieldRepository.findByCode("t_shirt_size").orElseThrow()
                .getId().toString();
        mockMvc.perform(delete("/api/hr/admin/custom-fields/{id}", fieldId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].archived").value(true));

        mockMvc.perform(get("/api/meta"))
                .andExpect(jsonPath("$.customCandidateFields.length()").value(0));

        // ...so the submission that was refused above is now accepted, and the
        // answer already collected is still on the record.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customFields.t_shirt_size").value("Medium"));
    }

    @Test
    @DisplayName("The NDA + NOC list can be filtered by every status the UI offers")
    void nocListFiltersByStatus() throws Exception {
        // Every chip on the dashboard sends its status code as a query param.
        // Without a registered converter Spring cannot turn "draft" into
        // NocStatus.DRAFT and the whole page fails with a 400.
        for (String status : new String[]{"draft", "sent", "viewed", "signed"}) {
            mockMvc.perform(get("/api/hr/noc").param("status", status)
                            .header("Authorization", "Bearer " + hrToken))
                    .andExpect(status().isOk());
        }

        // No filter at all is the default view.
        mockMvc.perform(get("/api/hr/noc").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An NDA + NOC can only be sent to a company Microsoft account")
    void nocRecipientMustBeACompanyAccount() throws Exception {
        // These are internal documents, so a personal address is refused before
        // the files are even read.
        mockMvc.perform(multipart("/api/hr/noc")
                        .file(new MockMultipartFile("nda", "nda.pdf", "application/pdf", realPdfBytes(1)))
                        .file(new MockMultipartFile("noc", "noc.pdf", "application/pdf", realPdfBytes(1)))
                        .param("recipientName", "Personal Address")
                        .param("recipientEmail", "someone@gmail.com")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_ALLOWED"));

        mockMvc.perform(multipart("/api/hr/noc")
                        .file(new MockMultipartFile("nda", "nda.pdf", "application/pdf", realPdfBytes(1)))
                        .file(new MockMultipartFile("noc", "noc.pdf", "application/pdf", realPdfBytes(1)))
                        .param("recipientName", "Company Account")
                        .param("recipientEmail", "priya.sharma@cloudfuze.com")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientEmail").value("priya.sharma@cloudfuze.com"));
    }

    @Test
    @DisplayName("A Word NDA and a PDF NOC still combine into one signable document")
    void wordUploadIsConvertedAndCombined() throws Exception {
        // Mixed formats in one packet - the Word half must be converted before
        // it can be merged, or the field coordinates mean nothing.
        MvcResult created = mockMvc.perform(multipart("/api/hr/noc")
                        .file(new MockMultipartFile("nda", "nda.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                wordDocumentBytes("Non-Disclosure Agreement")))
                        .file(new MockMultipartFile("noc", "noc.pdf", "application/pdf", realPdfBytes(2)))
                        .param("recipientName", "Word Upload")
                        .param("recipientEmail", "word.upload@cloudfuze.com")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ndaFilename").value("nda.docx"))
                .andReturn();

        UUID packetId = UUID.fromString(json(created).path("id").asText());
        int pages = json(created).path("pageCount").asInt();
        // 1 converted page + the 2-page PDF.
        assertThat(pages).isEqualTo(3);

        // The combined file is a real PDF and kept the Word document's words.
        byte[] combined = mockMvc.perform(get("/api/hr/noc/{id}/file", packetId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(combined)) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document))
                    .contains("Non-Disclosure Agreement");
        }
    }

    /** A genuine .docx, built rather than checked in as a fixture. */
    private byte[] wordDocumentBytes(String heading) throws Exception {
        org.docx4j.openpackaging.packages.WordprocessingMLPackage pkg =
                org.docx4j.openpackaging.packages.WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().addStyledParagraphOfText("Title", heading);
        pkg.getMainDocumentPart().addParagraphOfText("Signed by the recipient.");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        pkg.save(out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("NDA and NOC are combined into one document, signed once, and returned as one")
    void ndaAndNocAreCombinedAndSignedAsOne() throws Exception {
        // Two source documents of different lengths, so the merge is provable.
        MvcResult created = mockMvc.perform(multipart("/api/hr/noc")
                        .file(new MockMultipartFile("nda", "nda.pdf", "application/pdf", realPdfBytes(2)))
                        .file(new MockMultipartFile("noc", "noc.pdf", "application/pdf", realPdfBytes(3)))
                        .param("recipientName", "Nikhil Menon")
                        .param("recipientEmail", "nikhil.menon@cloudfuze.com")
                        .param("title", "NDA and NOC 2026")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.ndaFilename").value("nda.pdf"))
                .andExpect(jsonPath("$.nocFilename").value("noc.pdf"))
                // 2 + 3 pages in, one 5-page document out.
                .andExpect(jsonPath("$.pageCount").value(5))
                .andReturn();
        UUID packetId = UUID.fromString(json(created).path("id").asText());

        // Sending is refused until there is something to sign.
        mockMvc.perform(post("/api/hr/noc/{id}/send", packetId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_FIELDS_PLACED"));

        // Fields spanning both halves of the combined document: page 1 came from
        // the NDA, page 4 from the NOC.
        mockMvc.perform(put("/api/hr/noc/{id}/fields", packetId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields":[
                                  {"type":"name","page":1,"xPct":10,"yPct":70,"widthPct":30,"heightPct":5},
                                  {"type":"signature","page":4,"xPct":10,"yPct":80,"widthPct":25,"heightPct":8}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(2));

        MvcResult sent = mockMvc.perform(post("/api/hr/noc/{id}/send", packetId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.linkActive").value(true))
                .andReturn();

        // The link is returned exactly once, on sending - same rule as a portal link.
        String signingUrl = json(sent).path("signingUrl").asText();
        assertThat(signingUrl).isNotBlank();
        String token = signingUrl.substring(signingUrl.lastIndexOf('/') + 1);

        // The recipient sees one document of five pages, not two documents.
        mockMvc.perform(get("/api/noc/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Nikhil Menon"))
                .andExpect(jsonPath("$.pageCount").value(5))
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.signed").value(false));

        // Every field must be answered.
        mockMvc.perform(post("/api/noc/{token}/sign", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signedByName":"Nikhil Menon","fieldValues":{"0":"Nikhil Menon"}}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIELD_INCOMPLETE"));

        mockMvc.perform(post("/api/noc/{token}/sign", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signedByName":"Nikhil Menon","fieldValues":{"0":"Nikhil Menon","1":"%s"}}"""
                                .formatted(signatureDataUri())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signed").value(true))
                .andExpect(jsonPath("$.signedByName").value("Nikhil Menon"));

        // Signing is one-shot.
        mockMvc.perform(post("/api/noc/{token}/sign", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signedByName":"Nikhil Menon","fieldValues":{"0":"Nikhil Menon","1":"%s"}}"""
                                .formatted(signatureDataUri())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOC_ALREADY_SIGNED"));

        // HR gets back one combined signed document, still five pages, and the
        // typed name really was stamped onto it.
        byte[] signed = mockMvc.perform(get("/api/hr/noc/{id}/file", packetId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(signed)) {
            assertThat(document.getNumberOfPages()).isEqualTo(5);
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertThat(text).contains("Nikhil Menon");
        }

        mockMvc.perform(get("/api/hr/noc/{id}", packetId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(jsonPath("$.status").value("signed"))
                .andExpect(jsonPath("$.signedCopyAvailable").value(true));
    }


    @Test
    @DisplayName("HR invites a batch sharing one checklist, and a bad row does not sink the good ones")
    void bulkInviteCandidates() throws Exception {
        // A duplicate of an existing candidate, a repeat inside the batch, and
        // two good rows - all in one request.
        createCandidate("batch.existing@example.com");

        MvcResult result = mockMvc.perform(post("/api/hr/candidates/bulk")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidates": [
                                    {"name":"Batch One","email":"batch.one@example.com",
                                     "role":"Engineer","department":"Engineering"},
                                    {"name":"Batch Two","email":"batch.two@example.com",
                                     "role":"Engineer","department":"Engineering"},
                                    {"name":"Already There","email":"batch.existing@example.com",
                                     "role":"Engineer","department":"Engineering"},
                                    {"name":"Repeat","email":"batch.one@example.com",
                                     "role":"Engineer","department":"Engineering"}
                                  ],
                                  "requiredDocuments": [
                                    {"type":"aadhaar_id","mandatory":true},
                                    {"type":"pan_card","mandatory":false}
                                  ]
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.rows[0].success").value(true))
                .andExpect(jsonPath("$.rows[1].success").value(true))
                .andExpect(jsonPath("$.rows[2].success").value(false))
                .andExpect(jsonPath("$.rows[3].success").value(false))
                .andExpect(jsonPath("$.rows[3].errorCode").value("DUPLICATE_IN_BATCH"))
                .andReturn();

        // The two good rows really were committed, despite the failures after them.
        assertThat(candidateRepository.existsByEmailIgnoreCase("batch.one@example.com")).isTrue();
        assertThat(candidateRepository.existsByEmailIgnoreCase("batch.two@example.com")).isTrue();

        // Both carry the shared checklist, mandatory flags intact.
        UUID createdId = UUID.fromString(json(result).path("rows").get(0).path("candidate").path("id").asText());
        mockMvc.perform(get("/api/hr/candidates/{id}", createdId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(jsonPath("$.requiredDocuments.length()").value(2))
                .andExpect(jsonPath("$.candidate.documentsRequired").value(2));

        // An empty list is a request error, not a batch with zero rows.
        mockMvc.perform(post("/api/hr/candidates/bulk")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"candidates": [], "requiredDocuments": [{"type":"aadhaar_id","mandatory":true}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("HR can correct the candidate's details, including after they are locked")
    void hrCanEditCandidateDetails() throws Exception {
        Onboarding onboarding = createCandidate("hr.edit@example.com");

        // Name, role and department are HR's to correct; email is not offered.
        mockMvc.perform(put("/api/hr/candidates/{id}", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Priya S Sharma","role":"Staff Engineer","department":"Platform"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya S Sharma"))
                .andExpect(jsonPath("$.role").value("Staff Engineer"))
                .andExpect(jsonPath("$.department").value("Platform"))
                .andExpect(jsonPath("$.email").value("hr.edit@example.com"));

        mockMvc.perform(put("/api/hr/candidates/{id}", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","role":"","department":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // HR can enter the personal details before the candidate ever has.
        mockMvc.perform(put("/api/hr/candidates/{id}/profile", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullNameAsPerAadhaar").value("Priya Sharma"));

        // Lock the candidate out entirely, the way a real correction request arises.
        approveAllDocuments(onboarding);
        submitProfile(onboarding.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_LOCKED"));

        // HR is still able to fix it - that is the whole point of the lock message.
        mockMvc.perform(put("/api/hr/candidates/{id}/profile", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload().replace("Priya Sharma", "Priya S Sharma")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullNameAsPerAadhaar").value("Priya S Sharma"));

        // Invalid input is refused for HR exactly as it is for the candidate.
        mockMvc.perform(put("/api/hr/candidates/{id}/profile", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload().replace("\"panNumber\": \"ABCDE1234F\"",
                                "\"panNumber\": \"nope\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.panNumber").exists());

        // Both edits are attributed to the HR user, not the candidate.
        MvcResult audit = mockMvc.perform(get("/api/hr/candidates/{id}/audit", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(audit).findValuesAsText("eventType"))
                .contains("candidate_updated", "profile_corrected");
    }

    @Test
    @DisplayName("HR can toggle an existing document between mandatory and optional")
    void hrCanToggleDocumentMandatory() throws Exception {
        Onboarding onboarding = createCandidate("toggle@example.com");

        mockMvc.perform(patch("/api/hr/candidates/{id}/required-documents/{type}",
                        onboarding.candidateId(), "pan_card")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandatory": false}"""))
                .andExpect(status().isOk());
        assertThat(mandatoryFlagFor(onboarding.candidateId(), "pan_card")).isFalse();

        // Flipping it back works too.
        mockMvc.perform(patch("/api/hr/candidates/{id}/required-documents/{type}",
                        onboarding.candidateId(), "pan_card")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandatory": true}"""))
                .andExpect(status().isOk());
        assertThat(mandatoryFlagFor(onboarding.candidateId(), "pan_card")).isTrue();

        // A type this candidate was never asked for is refused.
        mockMvc.perform(patch("/api/hr/candidates/{id}/required-documents/{type}",
                        onboarding.candidateId(), "address_proof")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandatory": true}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_REQUESTED"));
    }

    @Test
    @DisplayName("HR can add a document requirement after the candidate has already submitted")
    void hrCanAddRequiredDocumentAfterSubmission() throws Exception {
        Onboarding onboarding = createCandidate("add-doc@example.com");
        submitProfile(onboarding.token()).andExpect(status().isOk());
        upload(onboarding.token(), "ssc_certificate", "class10.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "higher_education_provisional", "provisional.pdf", "b_tech")
                .andExpect(status().isOk());
        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "pan_card", "pan.pdf").andExpect(status().isOk());
        submitForReview(onboarding.token()).andExpect(status().isOk());

        // A type already required is not duplicated.
        mockMvc.perform(post("/api/hr/candidates/{id}/required-documents", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requiredDocuments":[{"type":"pan_card","mandatory":true}]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_ADD"));

        // HR asks for something new even though the pack is already with them.
        mockMvc.perform(post("/api/hr/candidates/{id}/required-documents", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requiredDocuments":[{"type":"address_proof","mandatory":true}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[4].type").value("address_proof"))
                .andExpect(jsonPath("$[4].uploadAllowed").value(true));

        // The candidate's own checklist shows it as uploadable too.
        mockMvc.perform(get("/api/portal/{token}/documents", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[4].type").value("address_proof"))
                .andExpect(jsonPath("$.documents[4].uploadAllowed").value(true));

        // This is the exact case that used to fail with ALREADY_SUBMITTED.
        upload(onboarding.token(), "address_proof", "address.pdf").andExpect(status().isOk());

        // A document the candidate already provided still cannot be freely replaced.
        upload(onboarding.token(), "pan_card", "pan-v2.pdf")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    }

    // ------------------------------------------------------------------
    // 9-11. The offer gate
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The offer API is forbidden while documents are pending")
    void offerLockedBeforeDocumentApproval() throws Exception {
        Onboarding onboarding = createCandidate("offer.locked@example.com");
        uploadOffer(onboarding.candidateId());

        mockMvc.perform(get("/api/portal/{token}/offer", onboarding.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAGE_FORBIDDEN"))
                .andExpect(jsonPath("$.details.requiredStage").value("docs_approved"));

        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/portal/{token}/offer/file", onboarding.token()))
                .andExpect(status().isForbidden());

        // The candidate is only ever shown the step they are on: no padlocked
        // offer tile, and no explanation of a stage they cannot reach.
        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.offerAvailable").value(false))
                .andExpect(jsonPath("$.currentStep").value("documents"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].key").value("documents"))
                .andExpect(jsonPath("$.steps[0].state").value("current"))
                .andExpect(jsonPath("$.steps[*].key").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("offer"))));
    }

    @Test
    @DisplayName("Approved documents disappear from the portal, leaving only the offer step")
    void approvedDocumentsAreReplacedByTheOfferStep() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("only.offer@example.com"));

        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("offer"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].key").value("offer"))
                .andExpect(jsonPath("$.steps[*].key").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("documents"))));

        // And once the offer is accepted the same step remains, now completed -
        // offer acceptance is the end of the workflow.
        uploadOffer(onboarding.candidateId());
        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.currentStep").value("offer"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].key").value("offer"))
                .andExpect(jsonPath("$.steps[0].state").value("completed"));
    }

    @Test
    @DisplayName("A drafted offer stays hidden from the candidate until HR sends it")
    void offerDraftIsHiddenUntilSent() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("offer.draft@example.com"));

        // Upload only - the letter is a draft and must not be visible yet.
        mockMvc.perform(multipart("/api/hr/candidates/{id}/offer", onboarding.candidateId())
                        .file(new MockMultipartFile("file", "offer-letter.pdf", "application/pdf",
                                realPdfBytes(1)))
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("draft"));

        mockMvc.perform(get("/api/portal/{token}/offer", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepared").value(false));

        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isConflict());

        // Cannot be sent with nowhere for the candidate to sign.
        mockMvc.perform(post("/api/hr/candidates/{id}/offer/send", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNATURE_FIELDS_REQUIRED"));

        // Once a signature field is placed and it is sent, the candidate can see and sign it.
        saveSignatureField(onboarding.candidateId());
        sendOffer(onboarding.candidateId());
        mockMvc.perform(get("/api/portal/{token}/offer", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepared").value(true))
                .andExpect(jsonPath("$.canAccept").value(true));

        // Sending a second time is refused - it is already out.
        mockMvc.perform(post("/api/hr/candidates/{id}/offer/send", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_ALREADY_SENT"));
    }

    @Test
    @DisplayName("Offer becomes readable, viewable and acceptable once documents are approved")
    void offerAccessAndAcceptance() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("offer.flow@example.com"));
        uploadOffer(onboarding.candidateId());

        mockMvc.perform(get("/api/portal/{token}/offer", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepared").value(true))
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.canAccept").value(true));

        mockMvc.perform(post("/api/portal/{token}/offer/view", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("viewed"));
        assertThat(offerRepository.findByCandidateId(onboarding.candidateId()).orElseThrow().getViewedAt())
                .isNotNull();

        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.OFFER_ACCEPTED);
        var offer = offerRepository.findByCandidateId(onboarding.candidateId()).orElseThrow();
        assertThat(offer.getAcceptedAt()).isNotNull();
        assertThat(offer.getAcceptedByName()).isEqualTo("Priya Sharma");
        assertThat(offer.getSignedStorageKey()).isNotBlank();

        // Signing twice is refused.
        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_ALREADY_ACCEPTED"));
    }

    @Test
    @DisplayName("Signing requires a signature image")
    void offerAcceptanceValidation() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("offer.validation@example.com"));
        uploadOffer(onboarding.candidateId());

        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldValues":{}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // A value that is not a real image must not reach the PDF writer.
        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldValues":{"0":"not-an-image"}}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE_IMAGE"));
    }

    @Test
    @DisplayName("HR can place name, title, date and text fields, and all of them are stamped")
    void offerSupportsEveryFieldType() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("offer.fields@example.com"));

        mockMvc.perform(multipart("/api/hr/candidates/{id}/offer", onboarding.candidateId())
                        .file(new MockMultipartFile("file", "offer-letter.pdf", "application/pdf",
                                realPdfBytes(2)))
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());

        // One of each type, including a text field with its own colour and font.
        mockMvc.perform(put("/api/hr/candidates/{id}/offer/fields", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields":[
                                  {"type":"signature","page":1,"xPct":10,"yPct":70,"widthPct":25,"heightPct":8},
                                  {"type":"name","page":1,"xPct":40,"yPct":70,"widthPct":25,"heightPct":4},
                                  {"type":"title","page":1,"xPct":10,"yPct":80,"widthPct":20,"heightPct":4},
                                  {"type":"date","page":2,"xPct":10,"yPct":85,"widthPct":16,"heightPct":4},
                                  {"type":"text","page":2,"xPct":40,"yPct":85,"widthPct":30,"heightPct":6,
                                   "prefill":"Joining on the agreed date",
                                   "textColor":"#dc2626","textFont":"times"}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(5))
                .andExpect(jsonPath("$.fields[4].type").value("text"))
                .andExpect(jsonPath("$.fields[4].textColor").value("#dc2626"))
                .andExpect(jsonPath("$.fields[4].textFont").value("times"))
                .andExpect(jsonPath("$.fields[4].prefill").value("Joining on the agreed date"));

        sendOffer(onboarding.candidateId());

        // The candidate sees every field, with HR's pre-filled text intact.
        mockMvc.perform(get("/api/portal/{token}/offer", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(5))
                .andExpect(jsonPath("$.fields[3].type").value("date"));

        // Leaving any single field blank is refused, even though the signature is present.
        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldValues":{"0":"%s","1":"Priya Sharma"}}"""
                                .formatted(signatureDataUri())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIELD_INCOMPLETE"));

        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fieldValues":{
                                  "0":"%s",
                                  "1":"Priya Sharma",
                                  "2":"Software Engineer",
                                  "3":"2026-09-01",
                                  "4":"Joining on the agreed date"
                                }}""".formatted(signatureDataUri())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        var offer = offerRepository.findByCandidateId(onboarding.candidateId()).orElseThrow();
        assertThat(offer.getSignedStorageKey()).isNotBlank();
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.OFFER_ACCEPTED);

        // The signed copy is a real, still-readable PDF with both pages intact.
        MvcResult signed = mockMvc.perform(get("/api/hr/candidates/{id}/offer/file", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        byte[] signedBytes = signed.getResponse().getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument document =
                     org.apache.pdfbox.Loader.loadPDF(signedBytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertThat(text).contains("Priya Sharma", "Software Engineer", "2026-09-01",
                    "Joining on the agreed date");
        }
    }

    // ------------------------------------------------------------------
    // 12-16. Offer acceptance completes onboarding
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Accepting the offer completes onboarding and is only possible once")
    void offerAcceptanceCompletesOnboarding() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("offer.complete@example.com"));
        uploadOffer(onboarding.candidateId());

        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.canAccept").value(false));

        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.OFFER_ACCEPTED);
        assertThat(candidateRepository.findById(onboarding.candidateId()).orElseThrow().getCompletedAt())
                .isNotNull();

        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.onboardingComplete").value(true))
                .andExpect(jsonPath("$.headline").value("Onboarding Complete"))
                .andExpect(jsonPath("$.currentStep").value("offer"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].key").value("offer"))
                .andExpect(jsonPath("$.steps[0].state").value("completed"));

        // Signing twice is refused.
        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_ALREADY_ACCEPTED"));
    }

    @Test
    @DisplayName("A file disguised with a .pdf name is refused")
    void disguisedFileIsRejected() throws Exception {
        Onboarding onboarding = createCandidate("disguised@example.com");

        // HTML wearing a .pdf extension and a PDF content type - both of which
        // the client controls, which is why the bytes are what get checked.
        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", onboarding.token(), "aadhaar_id")
                        .file(new MockMultipartFile("file", "aadhaar.pdf", "application/pdf",
                                "<html><script>alert(1)</script></html>".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));

        // A PNG renamed to .pdf is refused for the same reason.
        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", onboarding.token(), "aadhaar_id")
                        .file(new MockMultipartFile("file", "aadhaar.pdf", "application/pdf",
                                fileBytes("real.png"))))
                .andExpect(status().isBadRequest());

        // The genuine article still goes through.
        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Personal details are refused without an alternate contact number")
    void alternateContactNumberIsRequired() throws Exception {
        Onboarding onboarding = createCandidate("alt.contact@example.com");

        // Omitted entirely.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload().replace("\"alternateContactNumber\": \"9876500000\",\n", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.alternateContactNumber").exists());

        // Present but blank.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload().replace("\"9876500000\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.alternateContactNumber").exists());

        // And it still has to look like a phone number.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload().replace("\"9876500000\"", "\"nope\"")))
                .andExpect(status().isBadRequest());

        // A valid one is accepted.
        mockMvc.perform(put("/api/portal/{token}/profile", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternateContactNumber").value("9876500000"));
    }

    @Test
    @DisplayName("The bond feature is gone: no bond route exists on either API")
    void bondEndpointsNoLongerExist() throws Exception {
        Onboarding onboarding = signOffer(approveAllDocuments(createCandidate("no.bond@example.com")));

        // Candidate portal - a token that authenticates fine still has no bond route.
        mockMvc.perform(get("/api/portal/{token}/bond", onboarding.token()))
                .andExpect(status().isNotFound());

        // HR side.
        mockMvc.perform(get("/api/hr/candidates/{id}/bond", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNotFound());

        // And the stage vocabulary no longer advertises one.
        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[*].value").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("bond_signed"))));
    }

    @Test
    @DisplayName("HR sees the full audit trail and the completed pipeline row")
    void auditTrailAndPipelineReflectCompletion() throws Exception {
        Onboarding onboarding = signOffer(approveAllDocuments(createCandidate("audit@example.com")));

        MvcResult auditResult = mockMvc.perform(get("/api/hr/candidates/{id}/audit", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode audit = json(auditResult);
        List<String> events = audit.findValuesAsText("eventType");
        assertThat(events).contains("candidate_created", "invitation_generated", "document_uploaded",
                "document_verified", "documents_approved", "offer_uploaded", "offer_accepted",
                "onboarding_completed");
        assertThat(audit.get(0).has("ipAddress")).isTrue();

        mockMvc.perform(get("/api/hr/candidates/{id}", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.stage").value("offer_accepted"))
                .andExpect(jsonPath("$.offer.status").value("accepted"))
                .andExpect(jsonPath("$.documents.length()").value(4));

        mockMvc.perform(get("/api/hr/dashboard/stats").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingComplete").value(1))
                .andExpect(jsonPath("$.activeCandidates").value(0))
                .andExpect(jsonPath("$.stageBreakdown.offer_accepted").value(1));
    }

    @Test
    @DisplayName("Document download paths never leak storage keys")
    void downloadPathsAreApiPaths() throws Exception {
        Onboarding onboarding = createCandidate("download@example.com");
        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf").andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/portal/{token}/documents", onboarding.token()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("candidates/").doesNotContain("storage");

        UUID documentId = documentId(onboarding.candidateId(), "aadhaar_id");
        mockMvc.perform(get("/api/portal/{token}/documents/{id}/file", onboarding.token(), documentId))
                .andExpect(status().isOk());

        // Another candidate cannot reach it with their own valid token.
        Onboarding other = createCandidate("other.candidate@example.com");
        mockMvc.perform(get("/api/portal/{token}/documents/{id}/file", other.token(), documentId))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private record Onboarding(UUID candidateId, String token) {
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return json(result).path("token").asText();
    }

    private Onboarding createCandidate(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidate.stage").value("docs_pending"))
                .andReturn();
        JsonNode body = json(result);
        return new Onboarding(UUID.fromString(body.path("candidate").path("id").asText()),
                tokenFromUrl(body.path("invitation").path("portalUrl").asText()));
    }

    private String createPayload(String email) {
        return """
                {
                  "name": "Priya Sharma",
                  "email": "%s",
                  "role": "Software Engineer",
                  "department": "Engineering",
                  "requiredDocuments": [
                    {"type": "ssc_certificate", "mandatory": true},
                    {"type": "higher_education_provisional", "mandatory": true},
                    {"type": "aadhaar_id", "mandatory": true},
                    {"type": "pan_card", "mandatory": true}
                  ]
                }""".formatted(email);
    }

    private org.springframework.test.web.servlet.ResultActions submitProfile(String token) throws Exception {
        return mockMvc.perform(put("/api/portal/{token}/profile", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(profilePayload()));
    }

    private org.springframework.test.web.servlet.ResultActions submitForReview(String token) throws Exception {
        return mockMvc.perform(post("/api/portal/{token}/submit", token));
    }

    private String profilePayload() {
        return """
                {
                  "fullNameAsPerAadhaar": "Priya Sharma",
                  "personalEmail": "priya.personal@example.com",
                  "contactNumber": "9876543210",
                  "alternateContactNumber": "9876500000",
                  "dateOfBirth": "2001-04-17",
                  "gender": "female",
                  "fathersName": "Rakesh Sharma",
                  "permanentAddress": "12-4-56 Banjara Hills, Hyderabad, Telangana 500034",
                  "bloodGroup": "o_positive",
                  "aadhaarNumber": "234567890123",
                  "panNumber": "ABCDE1234F",
                  "emergencyContactName": "Sunita Sharma",
                  "emergencyContactRelation": "mother",
                  "emergencyContactNumber": "9123456780"
                }""";
    }

    /** The standard profile payload with admin-created answers spliced in. */
    private String profilePayloadWithCustom(String customJson) {
        String base = profilePayload().trim();
        return base.substring(0, base.length() - 1) + ", \"customFields\": " + customJson + "}";
    }

    private String signPayload() {
        return """
                {"fieldValues":{"0":"%s"}}""".formatted(signatureDataUri());
    }

    /** A tiny real PNG, generated rather than hand-encoded, so it always decodes cleanly. */
    private String signatureDataUri() {
        try {
            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(60, 20, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setColor(java.awt.Color.BLUE);
            g.drawLine(2, 15, 58, 5);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A structurally real, loadable PDF - fileBytes()'s fake header is not enough once PDFBox parses it. */
    private byte[] realPdfBytes(int pages) {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4));
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, String type, String filename)
            throws Exception {
        return upload(token, type, filename, null);
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, String type, String filename,
                                                                      String course) throws Exception {
        var request = multipart("/api/portal/{token}/documents/{type}", token, type)
                .file(new MockMultipartFile("file", filename, contentTypeFor(filename),
                        fileBytes(filename)));
        if (course != null) {
            request.param("course", course);
        }
        return mockMvc.perform(request);
    }


    /*
     * Real header bytes for the extension. The validator checks these, so a
     * fixture that merely claims to be a PDF is not a useful test - and the old
     * one wrote "%%PDF" (two percent signs) and gave .png files PDF content,
     * neither of which a browser would ever produce.
     */
    private static byte[] fileBytes(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        byte[] header = switch (extension) {
            case "pdf" -> new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
            case "png" -> new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            case "jpg", "jpeg" -> new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            case "webp" -> new byte[] {
                0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
            default -> new byte[] {0x00, 0x01, 0x02, 0x03};
        };
        byte[] body = (" test content for " + filename).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] all = new byte[header.length + body.length];
        System.arraycopy(header, 0, all, 0, header.length);
        System.arraycopy(body, 0, all, header.length, body.length);
        return all;
    }

    private static String contentTypeFor(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "application/pdf";
        };
    }

    private org.springframework.test.web.servlet.ResultActions reject(UUID documentId, String reason)
            throws Exception {
        return mockMvc.perform(post("/api/hr/documents/{id}/reject", documentId)
                .header("Authorization", "Bearer " + hrToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("reason", reason))));
    }

    private void verify(UUID documentId) throws Exception {
        mockMvc.perform(post("/api/hr/documents/{id}/verify", documentId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());
    }

    private Onboarding approveAllDocuments(Onboarding onboarding) throws Exception {
        submitProfile(onboarding.token()).andExpect(status().isOk());
        upload(onboarding.token(), "ssc_certificate", "class10.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "higher_education_provisional", "provisional.pdf", "b_tech")
                .andExpect(status().isOk());
        upload(onboarding.token(), "aadhaar_id", "aadhaar.pdf").andExpect(status().isOk());
        upload(onboarding.token(), "pan_card", "pan.pdf").andExpect(status().isOk());
        submitForReview(onboarding.token()).andExpect(status().isOk());
        verify(documentId(onboarding.candidateId(), "ssc_certificate"));
        verify(documentId(onboarding.candidateId(), "higher_education_provisional"));
        verify(documentId(onboarding.candidateId(), "aadhaar_id"));
        verify(documentId(onboarding.candidateId(), "pan_card"));
        // Verifying every mandatory document only makes the candidate eligible;
        // HR still has to click approve.
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_PENDING);
        approve(onboarding.candidateId());
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_APPROVED);
        return onboarding;
    }

    private void approve(UUID candidateId) throws Exception {
        mockMvc.perform(post("/api/hr/candidates/{id}/notify", candidateId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("approved"));
    }

    private Onboarding signOffer(Onboarding onboarding) throws Exception {
        uploadOffer(onboarding.candidateId());
        mockMvc.perform(post("/api/portal/{token}/offer/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isOk());
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.OFFER_ACCEPTED);
        return onboarding;
    }

    /** Uploads the offer letter (as a draft), places a signature field and sends it. */
    private void uploadOffer(UUID candidateId) throws Exception {
        mockMvc.perform(multipart("/api/hr/candidates/{id}/offer", candidateId)
                        .file(new MockMultipartFile("file", "offer-letter.pdf", "application/pdf",
                                realPdfBytes(1)))
                        .param("notes", "Standard offer, 30 day joining window")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("draft"));
        saveSignatureField(candidateId);
        sendOffer(candidateId);
    }

    private void saveSignatureField(UUID candidateId) throws Exception {
        mockMvc.perform(put("/api/hr/candidates/{id}/offer/fields", candidateId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields":[{"type":"signature","page":1,"xPct":10,"yPct":80,"widthPct":25,"heightPct":8}]}"""))
                .andExpect(status().isOk());
    }

    private void sendOffer(UUID candidateId) throws Exception {
        mockMvc.perform(post("/api/hr/candidates/{id}/offer/send", candidateId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"));
    }

    private UUID documentId(UUID candidateId, String typeCode) {
        return documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId).stream()
                .filter(doc -> doc.getDocumentType().getCode().equals(typeCode))
                .map(doc -> doc.getId())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No document of type " + typeCode));
    }

    private boolean mandatoryFlagFor(UUID candidateId, String typeCode) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/hr/candidates/{id}/documents", candidateId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode doc : json(result)) {
            if (doc.path("type").asText().equals(typeCode)) {
                return doc.path("mandatory").asBoolean();
            }
        }
        throw new AssertionError("No document of type " + typeCode);
    }

    private Map<String, String> optionLabels(JsonNode meta, String field) {
        return optionField(meta, field, "label");
    }

    private Map<String, String> optionGroups(JsonNode meta, String field) {
        return optionField(meta, field, "group");
    }

    private Map<String, String> optionField(JsonNode meta, String field, String property) {
        Map<String, String> options = new LinkedHashMap<>();
        meta.path(field).forEach(option -> {
            if (option.hasNonNull(property)) {
                options.put(option.path("value").asText(), option.path(property).asText());
            }
        });
        return options;
    }

    private Stage stageOf(UUID candidateId) {
        return candidateRepository.findById(candidateId).orElseThrow().getStage();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String tokenFromUrl(String portalUrl) {
        return portalUrl.substring(portalUrl.lastIndexOf('/') + 1);
    }
}
