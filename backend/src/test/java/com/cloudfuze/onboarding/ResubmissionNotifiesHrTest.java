package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.HrUserRepository;
import com.cloudfuze.onboarding.service.HrNotifier;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Closing the loop on a rejected document.
 *
 * <p>Rejecting one emails the candidate, so they know to act. The journey back
 * was silent: the replacement landed in the database and HR found out only by
 * reopening the record on the off chance. These tests pin both halves of the
 * rule - HR is told when the pack is whole again, and is left alone until then,
 * because a mail that arrives while two documents are still outstanding sends
 * them on a wasted trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResubmissionNotifiesHrTest {

    private static final String HR_EMAIL = "resub.hr@cloudfuze.com";
    private static final String HR_PASSWORD = "Sup3r-Secret-Pass!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HrUserRepository hrUserRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private com.cloudfuze.onboarding.repository.CandidateDocumentRepository documentRepository;
    @Autowired private com.cloudfuze.onboarding.repository.CandidateProfileRepository profileRepository;
    @Autowired private com.cloudfuze.onboarding.repository.AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /* Spied rather than mocked: the real notifier still runs, so a composer that
       cannot build the message fails the test rather than passing silently. */
    @SpyBean private HrNotifier hrNotifier;

    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        profileRepository.deleteAll();
        documentRepository.deleteAll();
        candidateRepository.deleteAll();
        hrUserRepository.deleteAll();

        HrUser hr = new HrUser(HR_EMAIL, passwordEncoder.encode(HR_PASSWORD), "Resub HR", "HR");
        hr.setRole(HrRole.ADMIN);
        hrUserRepository.save(hr);
        hrToken = login();
    }

    @Test
    @DisplayName("Returning the last rejected document tells HR the pack is back with them")
    void resubmissionNotifiesHr() throws Exception {
        String token = createCandidate("resub@example.com", "aadhaar_id", "pan_card");
        upload(token, "aadhaar_id");
        upload(token, "pan_card");
        submitForReview(token);

        reject(documentId(token, "aadhaar_id"));

        // Replacing it puts the pack back in HR's hands, and they are told so.
        upload(token, "aadhaar_id");

        verify(hrNotifier, timeout(5000)).candidateResubmitted(any(Candidate.class), anyInt());
    }

    @Test
    @DisplayName("HR is not pinged while documents they sent back are still outstanding")
    void partialReturnStaysQuiet() throws Exception {
        String token = createCandidate("partial@example.com", "aadhaar_id", "pan_card", "address_proof");
        upload(token, "aadhaar_id");
        upload(token, "pan_card");
        upload(token, "address_proof");
        submitForReview(token);

        reject(documentId(token, "aadhaar_id"));
        reject(documentId(token, "pan_card"));

        // One of the two comes back. Looking now would be a wasted trip.
        upload(token, "aadhaar_id");
        verify(hrNotifier, never()).candidateResubmitted(any(Candidate.class), anyInt());

        // The second one lands, so now it is worth their time.
        upload(token, "pan_card");
        verify(hrNotifier, timeout(5000)).candidateResubmitted(any(Candidate.class), anyInt());
    }

    @Test
    @DisplayName("Swapping a file before submitting is the candidate working, not news for HR")
    void reuploadBeforeSubmissionStaysQuiet() throws Exception {
        String token = createCandidate("early@example.com", "aadhaar_id");
        upload(token, "aadhaar_id");
        // Changed their mind before handing anything over.
        upload(token, "aadhaar_id");

        verify(hrNotifier, never()).candidateResubmitted(any(Candidate.class), anyInt());
    }

    /* ---- helpers ---- */

    private String createCandidate(String email, String... typeCodes) throws Exception {
        StringBuilder documents = new StringBuilder();
        for (String code : typeCodes) {
            if (documents.length() > 0) {
                documents.append(",");
            }
            documents.append("{\"type\":\"%s\",\"mandatory\":true}".formatted(code));
        }
        String payload = """
                {
                  "name": "Priya Sharma",
                  "email": "%s",
                  "role": "Software Engineer",
                  "department": "Engineering",
                  "requiredDocuments": [%s]
                }""".formatted(email, documents);

        MvcResult result = mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        String portalUrl = json(result).path("invitation").path("portalUrl").asText();
        String path = URI.create(portalUrl).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void upload(String token, String typeCode) throws Exception {
        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", token, typeCode)
                        .file(new MockMultipartFile("file", typeCode + ".pdf", "application/pdf", pdfBytes())))
                .andExpect(status().isOk());
    }

    /** Fills in the profile too, since a pack cannot be handed over without it. */
    private void submitForReview(String token) throws Exception {
        mockMvc.perform(put("/api/portal/{token}/profile", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/portal/{token}/submit", token))
                .andExpect(status().isOk());
    }

    private void reject(String documentId) throws Exception {
        mockMvc.perform(post("/api/hr/documents/{id}/reject", documentId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"The scan is unreadable - please send it again."}"""))
                .andExpect(status().isOk());
    }

    private String documentId(String token, String typeCode) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/portal/{token}/documents", token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode document : json(result).path("documents")) {
            if (typeCode.equals(document.path("type").asText())) {
                return document.path("id").asText();
            }
        }
        throw new AssertionError("No uploaded document of type " + typeCode);
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

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(HR_EMAIL, HR_PASSWORD)))
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
