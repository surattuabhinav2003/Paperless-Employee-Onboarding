package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.HrUserRepository;
import com.cloudfuze.onboarding.storage.FileStorageService;
import com.cloudfuze.onboarding.storage.StoredBlobRepository;
import com.cloudfuze.onboarding.storage.StoredFile;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The storage production actually uses.
 *
 * <p>Everywhere else in this suite runs on the disk-backed store, which is the
 * development default. Production defaults to the database, because a container
 * throws its filesystem away on redeploy - so without this class the code that
 * holds real candidates' identity documents would be the one code path never
 * exercised.
 *
 * <p>The tests go through the API rather than calling the store directly. What
 * matters is not that bytes can be saved and read back, but that a document
 * uploaded through the portal comes back byte-identical when HR downloads it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "storage.provider=database")
class DatabaseStorageTest {

    private static final String HR_EMAIL = "storage.hr@cloudfuze.com";
    private static final String HR_PASSWORD = "Sup3r-Secret-Pass!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HrUserRepository hrUserRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private StoredBlobRepository blobRepository;
    @Autowired private FileStorageService storageService;
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
        blobRepository.deleteAll();

        HrUser hr = new HrUser(HR_EMAIL, passwordEncoder.encode(HR_PASSWORD), "Storage HR", "HR");
        hr.setRole(HrRole.ADMIN);
        hrUserRepository.save(hr);
        hrToken = login();
    }

    @Test
    @DisplayName("The database store is the one wired in, not the disk one")
    void databaseProviderIsActive() {
        assertThat(storageService.providerName()).isEqualTo("database");
    }

    @Test
    @DisplayName("A document uploaded through the portal comes back byte-identical to HR")
    void uploadedDocumentSurvivesTheRoundTrip() throws Exception {
        byte[] original = pdfBytes("This exact byte sequence must come back unchanged.");
        String token = createCandidate("storage@example.com");

        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", token, "aadhaar_id")
                        .file(new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", original)))
                .andExpect(status().isOk());

        // Nothing was written to a disk; the bytes are a row.
        assertThat(blobRepository.count()).isEqualTo(1);

        String documentId = documentId(token, "aadhaar_id");
        byte[] downloaded = mockMvc.perform(get("/api/hr/documents/{id}/file", documentId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(downloaded).isEqualTo(original);
    }

    @Test
    @DisplayName("Keys look the same as the disk store's, so the two are interchangeable")
    void keysMatchTheDiskStoreShape() {
        StoredFile stored = storageService.store("candidates/abc/documents", "My Scan.pdf",
                "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(stored.key()).startsWith("candidates/abc/documents/").endsWith(".pdf");
        assertThat(stored.originalFilename()).isEqualTo("My Scan.pdf");
        assertThat(stored.sizeBytes()).isEqualTo(5);
        // Same digest the disk store records, so audit evidence is unchanged.
        assertThat(stored.sha256())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    @DisplayName("A filename carrying a path cannot escape into one")
    void uploaderSuppliedPathsAreStripped() {
        StoredFile stored = storageService.store("candidates/abc/documents",
                "..\\..\\windows\\system32\\evil.pdf", "application/pdf",
                "x".getBytes(StandardCharsets.UTF_8));

        assertThat(stored.originalFilename()).isEqualTo("evil.pdf");
        assertThat(stored.key()).startsWith("candidates/abc/documents/").doesNotContain("..");
    }

    @Test
    @DisplayName("Reading a key that is not there fails loudly rather than returning nothing")
    void missingKeyIsAnError() {
        assertThat(storageService.exists("candidates/nope/documents/missing.pdf")).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> storageService.readAllBytes("candidates/nope/documents/missing.pdf"))
                .isInstanceOf(com.cloudfuze.onboarding.exception.StorageException.class);
    }

    /* ---- helpers ---- */

    private String createCandidate(String email) throws Exception {
        String payload = """
                {
                  "name": "Priya Sharma",
                  "email": "%s",
                  "role": "Software Engineer",
                  "department": "Engineering",
                  "requiredDocuments": [{"type": "aadhaar_id", "mandatory": true}]
                }""".formatted(email);

        MvcResult result = mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        String path = URI.create(json(result).path("invitation").path("portalUrl").asText()).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
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

    private static byte[] pdfBytes(String marker) {
        return ("%PDF-1.4\n1 0 obj\n<<>>\nendobj\n% " + marker + "\ntrailer\n<<>>\n%%EOF")
                .getBytes(StandardCharsets.UTF_8);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
