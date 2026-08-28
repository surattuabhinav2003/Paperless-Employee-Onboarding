package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.HrUserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard tiles count people, not paperwork.
 *
 * <p>HR chases names: a candidate who owes six payslips is one person to email,
 * not six items of work, and a per-document total made a handful of stragglers
 * look like a backlog. Each tile is also a link into the candidate list, so the
 * number has to be the length of the list it opens - which is what these tests
 * pin down.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardCountersTest {

    private static final String HR_EMAIL = "dash.hr@cloudfuze.com";
    private static final String HR_PASSWORD = "Sup3r-Secret-Pass!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HrUserRepository hrUserRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private com.cloudfuze.onboarding.repository.CandidateDocumentRepository documentRepository;
    @Autowired private com.cloudfuze.onboarding.repository.CandidateProfileRepository profileRepository;
    @Autowired private com.cloudfuze.onboarding.repository.AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        profileRepository.deleteAll();
        documentRepository.deleteAll();
        candidateRepository.deleteAll();
        hrUserRepository.deleteAll();

        HrUser hr = new HrUser(HR_EMAIL, passwordEncoder.encode(HR_PASSWORD), "Dash HR", "HR");
        hr.setRole(HrRole.ADMIN);
        hrUserRepository.save(hr);
        hrToken = login();
    }

    @Test
    @DisplayName("Documents pending counts the people still to upload, not the documents they owe")
    void tilesAreHeadCounts() throws Exception {
        // Owes three, has sent nothing.
        createCandidate("silent@example.com", "aadhaar_id", "pan_card", "address_proof");

        // Has sent everything asked of them - in HR's queue, nothing left to chase.
        String settled = createCandidate("settled@example.com", "aadhaar_id", "pan_card");
        upload(settled, "aadhaar_id");
        upload(settled, "pan_card");

        // Halfway: one document in the queue, one still owed. Belongs in both tiles.
        String halfway = createCandidate("halfway@example.com", "aadhaar_id", "pan_card");
        upload(halfway, "aadhaar_id");

        /*
         * Four documents are outstanding across the three candidates - three from
         * the first, one from the third - but only two people need chasing, and
         * two is what the tile must say.
         */
        mockMvc.perform(get("/api/hr/dashboard/stats").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsPending").value(2))
                .andExpect(jsonPath("$.awaitingReview").value(2))
                .andExpect(jsonPath("$.activeCandidates").value(3))
                .andExpect(jsonPath("$.verificationDone").value(0))
                .andExpect(jsonPath("$.onboardingComplete").value(0));
    }

    @Test
    @DisplayName("A rejected document puts its candidate back among those still to upload")
    void rejectionReopensTheChase() throws Exception {
        String candidate = createCandidate("rejected@example.com", "aadhaar_id");
        upload(candidate, "aadhaar_id");

        mockMvc.perform(get("/api/hr/dashboard/stats").header("Authorization", "Bearer " + hrToken))
                .andExpect(jsonPath("$.documentsPending").value(0))
                .andExpect(jsonPath("$.awaitingReview").value(1));

        String documentId = documentId(candidate, "aadhaar_id");
        rejectDocument(documentId);

        // Back on the candidate, and out of HR's queue.
        mockMvc.perform(get("/api/hr/dashboard/stats").header("Authorization", "Bearer " + hrToken))
                .andExpect(jsonPath("$.documentsPending").value(1))
                .andExpect(jsonPath("$.awaitingReview").value(0));
    }

    @Test
    @DisplayName("Nobody outstanding reads as zero, not as an error")
    void nothingOutstanding() throws Exception {
        mockMvc.perform(get("/api/hr/dashboard/stats").header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsPending").value(0))
                .andExpect(jsonPath("$.awaitingReview").value(0))
                .andExpect(jsonPath("$.activeCandidates").value(0));
    }

    /* ---- helpers ---- */

    /** @return the candidate's portal token. */
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

    private void rejectDocument(String documentId) throws Exception {
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

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(HR_EMAIL, HR_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("token").asText();
    }

    /** A byte sequence the upload validator accepts as a PDF. */
    private static byte[] pdfBytes() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
