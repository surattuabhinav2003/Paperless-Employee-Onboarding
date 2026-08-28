package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.CustomDocumentTypeRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Document types an administrator invented.
 *
 * <p>The point of these tests is that a runtime type is not a second-class
 * citizen: it must be requestable, uploadable and reviewable exactly like one
 * of the twenty-five built-in ones, even though it has no enum constant behind
 * it and is matched by code rather than by enum identity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomDocumentTypeTest {

    private static final String HR_EMAIL = "doc.admin@cloudfuze.com";
    private static final String HR_PASSWORD = "Sup3r-Secret-Pass!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HrUserRepository hrUserRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private com.cloudfuze.onboarding.repository.CandidateDocumentRepository documentRepository;
    @Autowired private com.cloudfuze.onboarding.repository.CandidateProfileRepository profileRepository;
    @Autowired private com.cloudfuze.onboarding.repository.AuditLogRepository auditLogRepository;
    @Autowired private CustomDocumentTypeRepository customDocumentTypeRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        profileRepository.deleteAll();
        documentRepository.deleteAll();
        candidateRepository.deleteAll();
        // The catalogue is global, so a type left behind would change what every
        // later test is offered.
        customDocumentTypeRepository.deleteAll();
        hrUserRepository.deleteAll();

        HrUser admin = new HrUser(HR_EMAIL, passwordEncoder.encode(HR_PASSWORD), "Doc Admin", "HR");
        admin.setRole(HrRole.ADMIN);
        hrUserRepository.save(admin);
        adminToken = login();
    }

    @Test
    @DisplayName("A type an admin invented can be requested, uploaded to, and reviewed like a built-in one")
    void customDocumentTypeWorksEndToEnd() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/hr/admin/document-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Police Verification","group":"identity",
                                 "description":"Issued by your local station","enabled":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].code").value("police_verification"))
                .andReturn();
        assertThat(json(created).get(0).path("groupLabel").asText()).isEqualTo("Identity & personal");

        // It joins the same picker list the built-in types come from, so HR
        // needs no separate screen to ask for it.
        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentTypes[?(@.value=='police_verification')].label")
                        .value("Police Verification"));

        String token = createCandidateRequiring("police_verification");

        // The candidate sees it on their checklist under the name the admin gave it.
        mockMvc.perform(get("/api/portal/{token}/documents", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[?(@.type=='police_verification')].typeLabel")
                        .value("Police Verification"));

        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", token, "police_verification")
                        .file(new MockMultipartFile("file", "pv.pdf", "application/pdf", pdfBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[?(@.type=='police_verification')].status")
                        .value("submitted"));

        // Uploading to a type that was never requested is still refused - the
        // code path is shared, so this guards the new matching too.
        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", token, "made_up_type")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", pdfBytes())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_REQUESTED"));

        // Withdrawing the type takes it off the picker but leaves the uploaded
        // document where it is, still named.
        String id = customDocumentTypeRepository.findByCode("police_verification").orElseThrow()
                .getId().toString();
        mockMvc.perform(delete("/api/hr/admin/document-types/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].archived").value(true));

        mockMvc.perform(get("/api/meta"))
                .andExpect(jsonPath("$.documentTypes[?(@.value=='police_verification')]").isEmpty());

        mockMvc.perform(get("/api/portal/{token}/documents", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[?(@.type=='police_verification')].typeLabel")
                        .value("Police Verification"))
                .andExpect(jsonPath("$.documents[?(@.type=='police_verification')].status")
                        .value("submitted"));
    }

    @Test
    @DisplayName("A custom type cannot collide with a built-in one, nor be created by ordinary HR")
    void customDocumentTypeIsGuarded() throws Exception {
        // A label that would generate a built-in code is refused, or two types
        // would answer to the same identity.
        mockMvc.perform(post("/api/hr/admin/document-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"pan card"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.label").exists());

        mockMvc.perform(post("/api/hr/admin/document-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Police Verification"}"""))
                .andExpect(status().isCreated());

        // The same name twice is refused rather than creating a second type.
        mockMvc.perform(post("/api/hr/admin/document-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"police verification"}"""))
                .andExpect(status().isBadRequest());
        assertThat(customDocumentTypeRepository.count()).isEqualTo(1);

        // A name with nothing usable in it cannot become a code.
        mockMvc.perform(post("/api/hr/admin/document-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"???"}"""))
                .andExpect(status().isBadRequest());

        // Ordinary HR may use the catalogue but not change it.
        HrUser plain = new HrUser("plain.hr@cloudfuze.com", passwordEncoder.encode(HR_PASSWORD), "Plain", "HR");
        hrUserRepository.save(plain);
        String hrToken = login("plain.hr@cloudfuze.com");

        mockMvc.perform(post("/api/hr/admin/document-types")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Medical Certificate"}"""))
                .andExpect(status().isForbidden());
        assertThat(customDocumentTypeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("A withdrawn type can no longer be asked of a new candidate")
    void withdrawnTypeCannotBeRequested() throws Exception {
        mockMvc.perform(post("/api/hr/admin/document-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Police Verification"}"""))
                .andExpect(status().isCreated());

        String id = customDocumentTypeRepository.findByCode("police_verification").orElseThrow()
                .getId().toString();
        mockMvc.perform(delete("/api/hr/admin/document-types/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidatePayload("withdrawn@example.com", "police_verification")))
                .andExpect(status().isBadRequest());

        // A code that never existed is refused the same way.
        mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidatePayload("nonsense@example.com", "not_a_real_type")))
                .andExpect(status().isBadRequest());
    }

    /* ---- helpers ---- */

    private String createCandidateRequiring(String typeCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidatePayload("custom.doc@example.com", typeCode)))
                .andExpect(status().isCreated())
                .andReturn();
        String portalUrl = json(result).path("invitation").path("portalUrl").asText();
        String path = URI.create(portalUrl).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String candidatePayload(String email, String typeCode) {
        return """
                {
                  "name": "Priya Sharma",
                  "email": "%s",
                  "role": "Software Engineer",
                  "department": "Engineering",
                  "requiredDocuments": [
                    {"type": "aadhaar_id", "mandatory": true},
                    {"type": "%s", "mandatory": true}
                  ]
                }""".formatted(email, typeCode);
    }

    private String login() throws Exception {
        return login(HR_EMAIL);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, HR_PASSWORD)))
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
