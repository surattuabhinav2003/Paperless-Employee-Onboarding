package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.model.HrRole;
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

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permanently removing records.
 *
 * <p>This is the code that destroys Aadhaar and PAN scans, so what it takes and
 * what it leaves both need pinning. Two properties matter most and neither is
 * visible from the outside afterwards: that nothing belonging to the candidate
 * is left orphaned in the database, and that the audit trail survives - if
 * deleting someone also deleted the record of the deletion, nobody could ever
 * show what happened.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecordDeletionTest {

    private static final String ADMIN_EMAIL = "delete.admin@cloudfuze.com";
    private static final String HR_EMAIL = "delete.hr@cloudfuze.com";
    private static final String PASSWORD = "Sup3r-Secret-Pass!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HrUserRepository hrUserRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private CandidateDocumentRepository documentRepository;
    @Autowired private CandidateProfileRepository profileRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        offerRepository.deleteAll();
        profileRepository.deleteAll();
        documentRepository.deleteAll();
        candidateRepository.deleteAll();
        hrUserRepository.deleteAll();

        HrUser admin = new HrUser(ADMIN_EMAIL, passwordEncoder.encode(PASSWORD), "Delete Admin", "HR");
        admin.setRole(HrRole.ADMIN);
        hrUserRepository.save(admin);
        hrUserRepository.save(new HrUser(HR_EMAIL, passwordEncoder.encode(PASSWORD), "Plain HR", "HR"));

        adminToken = login(ADMIN_EMAIL);
        hrToken = login(HR_EMAIL);
    }

    @Test
    @DisplayName("Deleting a candidate takes their documents, details and offer with them")
    void deletingACandidateTakesEverythingOfTheirs() throws Exception {
        String token = createCandidate("gone@example.com");
        UUID candidateId = candidateRepository.findByEmailIgnoreCase("gone@example.com").orElseThrow().getId();
        upload(token, "aadhaar_id");
        submitProfile(token);

        assertThat(documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId)).isNotEmpty();
        assertThat(profileRepository.existsByCandidateId(candidateId)).isTrue();

        mockMvc.perform(delete("/api/hr/admin/candidates/{id}", candidateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(candidateRepository.findById(candidateId)).isEmpty();
        // Nothing of theirs may be left behind pointing at a candidate that is gone.
        assertThat(documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId)).isEmpty();
        assertThat(profileRepository.existsByCandidateId(candidateId)).isFalse();
        assertThat(offerRepository.findByCandidateId(candidateId)).isEmpty();
    }

    @Test
    @DisplayName("The audit trail outlives the record, so a deletion can still be shown")
    void theAuditTrailSurvives() throws Exception {
        String token = createCandidate("audited@example.com");
        UUID candidateId = candidateRepository.findByEmailIgnoreCase("audited@example.com").orElseThrow().getId();
        upload(token, "aadhaar_id");

        long before = auditLogRepository.count();
        mockMvc.perform(delete("/api/hr/admin/candidates/{id}", candidateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        /*
         * Deleting the person must not delete the evidence that anyone did. The
         * rows carry no foreign key precisely so they can outlive the record.
         */
        assertThat(auditLogRepository.count()).isGreaterThan(before);
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> entry.getEventType().name().equals("CANDIDATE_DELETED")
                        && entry.getActor().equals(ADMIN_EMAIL));
    }

    @Test
    @DisplayName("Ordinary HR cannot reach the deletion endpoints at all")
    void deletionIsAdministratorOnly() throws Exception {
        String token = createCandidate("protected@example.com");
        UUID candidateId = candidateRepository.findByEmailIgnoreCase("protected@example.com").orElseThrow().getId();
        upload(token, "aadhaar_id");

        mockMvc.perform(delete("/api/hr/admin/candidates/{id}", candidateId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isForbidden());

        assertThat(candidateRepository.findById(candidateId)).isPresent();
    }

    @Test
    @DisplayName("The preview says what would be destroyed before anything is")
    void previewDescribesTheDamage() throws Exception {
        String token = createCandidate("preview@example.com");
        UUID candidateId = candidateRepository.findByEmailIgnoreCase("preview@example.com").orElseThrow().getId();
        upload(token, "aadhaar_id");
        submitProfile(token);

        mockMvc.perform(get("/api/hr/admin/candidates/{id}/deletion-preview", candidateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Priya Sharma"))
                .andExpect(jsonPath("$.documents").value(1))
                .andExpect(jsonPath("$.hasProfile").value(true))
                .andExpect(jsonPath("$.hasOffer").value(false));

        // A preview must not be a deletion.
        assertThat(candidateRepository.findById(candidateId)).isPresent();
    }

    @Test
    @DisplayName("Deleting only the offer leaves the candidate and their documents standing")
    void deletingAnOfferKeepsTheCandidate() throws Exception {
        String token = createCandidate("offer@example.com");
        UUID candidateId = candidateRepository.findByEmailIgnoreCase("offer@example.com").orElseThrow().getId();
        upload(token, "aadhaar_id");
        submitProfile(token);
        approveAndOffer(candidateId, token);

        assertThat(offerRepository.findByCandidateId(candidateId)).isPresent();

        mockMvc.perform(delete("/api/hr/admin/candidates/{id}/offer", candidateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(offerRepository.findByCandidateId(candidateId)).isEmpty();
        assertThat(candidateRepository.findById(candidateId)).isPresent();
        assertThat(documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId)).isNotEmpty();
    }

    @Test
    @DisplayName("A candidate whose offer is deleted stops claiming to be complete")
    void deletingAnAcceptedOfferRewindsTheStage() throws Exception {
        String token = createCandidate("rewind@example.com");
        UUID candidateId = candidateRepository.findByEmailIgnoreCase("rewind@example.com").orElseThrow().getId();
        upload(token, "aadhaar_id");
        submitProfile(token);
        approveAndOffer(candidateId, token);

        // Force the end state the real signing flow produces.
        var candidate = candidateRepository.findById(candidateId).orElseThrow();
        candidate.setStage(Stage.OFFER_ACCEPTED);
        candidateRepository.save(candidate);

        mockMvc.perform(delete("/api/hr/admin/candidates/{id}/offer", candidateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // "Complete" with no letter behind it is a record that contradicts itself.
        assertThat(candidateRepository.findById(candidateId).orElseThrow().getStage())
                .isEqualTo(Stage.DOCS_APPROVED);
    }

    /* ---- helpers ---- */

    private String createCandidate(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Priya Sharma",
                                  "email": "%s",
                                  "role": "Software Engineer",
                                  "department": "Engineering",
                                  "requiredDocuments": [{"type": "aadhaar_id", "mandatory": true}]
                                }""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String path = URI.create(json(result).path("invitation").path("portalUrl").asText()).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void upload(String token, String typeCode) throws Exception {
        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", token, typeCode)
                        .file(new MockMultipartFile("file", typeCode + ".pdf", "application/pdf", pdfBytes())))
                .andExpect(status().isOk());
    }

    private void submitProfile(String token) throws Exception {
        mockMvc.perform(put("/api/portal/{token}/profile", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullNameAsPerAadhaar": "Priya Sharma",
                                  "personalEmail": "priya.personal@example.com",
                                  "contactNumber": "9876543210",
                                  "alternateContactNumber": "9876500000",
                                  "dateOfBirth": "2001-04-17",
                                  "gender": "female",
                                  "fathersName": "Rakesh Sharma",
                                  "permanentAddress": "12-4-56 Banjara Hills, Hyderabad 500034",
                                  "bloodGroup": "o_positive",
                                  "aadhaarNumber": "234567890123",
                                  "panNumber": "ABCDE1234F",
                                  "emergencyContactName": "Sunita Sharma",
                                  "emergencyContactRelation": "mother",
                                  "emergencyContactNumber": "9123456780"
                                }"""))
                .andExpect(status().isOk());
    }

    private void approveAndOffer(UUID candidateId, String token) throws Exception {
        mockMvc.perform(post("/api/portal/{token}/submit", token)).andExpect(status().isOk());
        UUID documentId = documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId).get(0).getId();
        mockMvc.perform(post("/api/hr/documents/{id}/verify", documentId)
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/hr/candidates/{id}/notify", candidateId)
                .header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/hr/candidates/{id}/offer", candidateId)
                        .file(new MockMultipartFile("file", "offer.pdf", "application/pdf", pdfBytes()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("token").asText();
    }

    private static byte[] pdfBytes() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
