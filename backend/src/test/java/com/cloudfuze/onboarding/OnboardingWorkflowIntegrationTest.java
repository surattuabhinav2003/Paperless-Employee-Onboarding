package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.AuditLogRepository;
import com.cloudfuze.onboarding.repository.BondRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the gated onboarding workflow, driven through the real
 * HTTP API: HR authentication, portal tokens, document review, the offer gate,
 * the bond gate and SignatureOne completion.
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
    @Autowired
    private CandidateRepository candidateRepository;
    @Autowired
    private CandidateDocumentRepository documentRepository;
    @Autowired
    private CandidateProfileRepository profileRepository;
    @Autowired
    private OfferRepository offerRepository;
    @Autowired
    private BondRepository bondRepository;
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
        bondRepository.deleteAll();
        candidateRepository.deleteAll();
        hrUserRepository.deleteAll();

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

        // Re-uploading something already submitted is refused.
        upload(onboarding.token(), "aadhaar_id", "aadhaar-again.pdf")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_AWAITING_REVIEW"));
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
    @DisplayName("Stage moves to docs_approved only when every mandatory document is verified")
    void stageAdvancesOnlyWhenAllMandatoryDocumentsVerified() throws Exception {
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
        // nor handed the pack to HR, so the stage holds.
        verify(documentId(onboarding.candidateId(), "pan_card"));
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_PENDING);

        submitProfile(onboarding.token()).andExpect(status().isOk());
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_PENDING);

        submitForReview(onboarding.token()).andExpect(status().isOk());
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_APPROVED);
        assertThat(candidateRepository.findById(onboarding.candidateId()).orElseThrow().getDocsApprovedAt())
                .isNotNull();

        // Uploads close once documents are approved.
        upload(onboarding.token(), "aadhaar_id", "another.pdf")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAGE_FORBIDDEN"));
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
                .andExpect(jsonPath("$.fieldErrors.fathersName").exists());
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
        // education (provisional then marksheet), Aadhaar, PAN, then the rest.
        assertThat(offered).startsWith("ssc_certificate", "secondary_education_certificate",
                "higher_education_provisional", "higher_education_marksheet", "aadhaar_id", "pan_card");
        assertThat(offered).doesNotContain("education_certificate", "intermediate_certificate",
                "diploma_certificate", "ug_degree_certificate", "pg_degree_certificate");

        Map<String, String> labels = optionLabels(meta, "documentTypes");
        assertThat(labels)
                .containsEntry("ssc_certificate", "Class 10 (SSC) Certificate")
                .containsEntry("secondary_education_certificate", "Secondary Education Certificate")
                .containsEntry("higher_education_provisional", "Higher Education - Provisional Certificate")
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

        mockMvc.perform(post("/api/portal/{token}/offer/accept", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/portal/{token}/offer/file", onboarding.token()))
                .andExpect(status().isForbidden());

        // The candidate is only ever shown the step they are on: no padlocked
        // offer or bond tile, and no explanation of a stage they cannot reach.
        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.offerAvailable").value(false))
                .andExpect(jsonPath("$.currentStep").value("documents"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].key").value("documents"))
                .andExpect(jsonPath("$.steps[0].state").value("current"))
                .andExpect(jsonPath("$.steps[*].key").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItems("offer", "bond"))));
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

        // And once the offer is accepted, only the bond step remains.
        uploadOffer(onboarding.candidateId());
        mockMvc.perform(post("/api/portal/{token}/offer/accept", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.currentStep").value("bond"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].key").value("bond"));
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

        mockMvc.perform(post("/api/portal/{token}/offer/accept", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.OFFER_ACCEPTED);
        var offer = offerRepository.findByCandidateId(onboarding.candidateId()).orElseThrow();
        assertThat(offer.getAcceptedAt()).isNotNull();
        assertThat(offer.getAcceptedByName()).isEqualTo("Priya Sharma");

        // Accepting twice is refused.
        mockMvc.perform(post("/api/portal/{token}/offer/accept", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_ALREADY_ACCEPTED"));
    }

    @Test
    @DisplayName("Acceptance requires an explicit confirmation and a typed name")
    void offerAcceptanceValidation() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("offer.validation@example.com"));
        uploadOffer(onboarding.candidateId());

        mockMvc.perform(post("/api/portal/{token}/offer/accept", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acknowledgementName":"","accepted":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------
    // 12-16. The bond gate and SignatureOne completion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("The bond API is forbidden until the offer is accepted")
    void bondLockedBeforeOfferAcceptance() throws Exception {
        Onboarding onboarding = approveAllDocuments(createCandidate("bond.locked@example.com"));
        uploadOffer(onboarding.candidateId());
        uploadBond(onboarding.candidateId());

        mockMvc.perform(get("/api/portal/{token}/bond", onboarding.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAGE_FORBIDDEN"))
                .andExpect(jsonPath("$.details.requiredStage").value("offer_accepted"));

        mockMvc.perform(post("/api/portal/{token}/bond/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/portal/{token}/bond/file", onboarding.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bond signing completes onboarding and stores the signature evidence")
    void bondSigningCompletesOnboarding() throws Exception {
        Onboarding onboarding = acceptOffer(approveAllDocuments(createCandidate("bond.sign@example.com")));
        uploadBond(onboarding.candidateId());

        mockMvc.perform(get("/api/portal/{token}/bond", onboarding.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepared").value(true))
                .andExpect(jsonPath("$.canSign").value(true))
                .andExpect(jsonPath("$.documentVersion").value("v2.1"));

        mockMvc.perform(post("/api/portal/{token}/bond/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("signed"))
                .andExpect(jsonPath("$.signatureRef").isNotEmpty())
                .andExpect(jsonPath("$.canSign").value(false));

        var bond = bondRepository.findByCandidateId(onboarding.candidateId()).orElseThrow();
        assertThat(bond.getSignedAt()).isNotNull();
        assertThat(bond.getSignatureRequestId()).isNotBlank();
        assertThat(bond.getSignedDocumentKey()).isNotBlank();
        assertThat(bond.getSignedDocumentHash()).isNotBlank();
        assertThat(bond.getSignerName()).isEqualTo("Priya Sharma");
        assertThat(bond.getAuditTrail()).extracting("eventType")
                .contains("signature_initiated", "signature_applied");

        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.BOND_SIGNED);
        assertThat(candidateRepository.findById(onboarding.candidateId()).orElseThrow().getCompletedAt())
                .isNotNull();

        mockMvc.perform(get("/api/portal/{token}", onboarding.token()))
                .andExpect(jsonPath("$.onboardingComplete").value(true))
                .andExpect(jsonPath("$.headline").value("Onboarding Complete"))
                .andExpect(jsonPath("$.currentStep").value("bond"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].key").value("bond"))
                .andExpect(jsonPath("$.steps[0].state").value("completed"));

        // Signing twice is refused.
        mockMvc.perform(post("/api/portal/{token}/bond/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOND_ALREADY_SIGNED"));

        mockMvc.perform(get("/api/portal/{token}/bond/signed-file", onboarding.token()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HR sees the full audit trail and the completed pipeline row")
    void auditTrailAndPipelineReflectCompletion() throws Exception {
        Onboarding onboarding = acceptOffer(approveAllDocuments(createCandidate("audit@example.com")));
        uploadBond(onboarding.candidateId());
        mockMvc.perform(post("/api/portal/{token}/bond/sign", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signPayload()))
                .andExpect(status().isOk());

        MvcResult auditResult = mockMvc.perform(get("/api/hr/candidates/{id}/audit", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode audit = json(auditResult);
        List<String> events = audit.findValuesAsText("eventType");
        assertThat(events).contains("candidate_created", "invitation_generated", "document_uploaded",
                "document_verified", "documents_approved", "offer_uploaded", "offer_accepted",
                "signature_initiated", "bond_signed", "onboarding_completed");
        assertThat(audit.get(0).has("ipAddress")).isTrue();

        mockMvc.perform(get("/api/hr/candidates/{id}", onboarding.candidateId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.stage").value("bond_signed"))
                .andExpect(jsonPath("$.bond.status").value("signed"))
                .andExpect(jsonPath("$.offer.status").value("accepted"))
                .andExpect(jsonPath("$.documents.length()").value(4));

        mockMvc.perform(get("/api/hr/dashboard/stats").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bondsSigned").value(1))
                .andExpect(jsonPath("$.activeCandidates").value(0))
                .andExpect(jsonPath("$.stageBreakdown.bond_signed").value(1));
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
                  "bloodGroup": "o_positive"
                }""";
    }

    private String acceptPayload() {
        return """
                {"acknowledgementName":"Priya Sharma","accepted":true}""";
    }

    private String signPayload() {
        return """
                {"signerFullName":"Priya Sharma","consent":true}""";
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, String type, String filename)
            throws Exception {
        return upload(token, type, filename, null);
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, String type, String filename,
                                                                      String course) throws Exception {
        var request = multipart("/api/portal/{token}/documents/{type}", token, type)
                .file(new MockMultipartFile("file", filename, "application/pdf",
                        ("%%PDF-1.4 test content for " + filename).getBytes()));
        if (course != null) {
            request.param("course", course);
        }
        return mockMvc.perform(request);
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
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.DOCS_APPROVED);
        return onboarding;
    }

    private Onboarding acceptOffer(Onboarding onboarding) throws Exception {
        uploadOffer(onboarding.candidateId());
        mockMvc.perform(post("/api/portal/{token}/offer/accept", onboarding.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload()))
                .andExpect(status().isOk());
        assertThat(stageOf(onboarding.candidateId())).isEqualTo(Stage.OFFER_ACCEPTED);
        return onboarding;
    }

    private void uploadOffer(UUID candidateId) throws Exception {
        mockMvc.perform(multipart("/api/hr/candidates/{id}/offer", candidateId)
                        .file(new MockMultipartFile("file", "offer-letter.pdf", "application/pdf",
                                "%PDF-1.4 offer letter".getBytes()))
                        .param("notes", "Standard offer, 30 day joining window")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"));
    }

    private void uploadBond(UUID candidateId) throws Exception {
        mockMvc.perform(multipart("/api/hr/candidates/{id}/bond", candidateId)
                        .file(new MockMultipartFile("file", "employment-bond.pdf", "application/pdf",
                                "%PDF-1.4 employment bond".getBytes()))
                        .param("documentVersion", "v2.1")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentVersion").value("v2.1"));
    }

    private UUID documentId(UUID candidateId, String typeCode) {
        return documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId).stream()
                .filter(doc -> doc.getDocumentType().getCode().equals(typeCode))
                .map(doc -> doc.getId())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No document of type " + typeCode));
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
