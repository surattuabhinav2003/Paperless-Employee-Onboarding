package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.email.EmailMessage;
import com.cloudfuze.onboarding.email.EmailService;
import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.model.OfferStatus;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.HrUserRepository;
import com.cloudfuze.onboarding.repository.OfferRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Releasing an offer letter has to reach the candidate.
 *
 * <p>Sending used to change a status and write an audit row, and that was all:
 * the candidate was never told, so the letter waited until they happened to open
 * their portal. These tests pin the two halves of "sent" - the mail goes out,
 * carrying a link straight to the signing page, and if it cannot go out then the
 * letter is not marked sent either.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfferEmailTest {

    private static final String HR_EMAIL = "offer.hr@cloudfuze.com";
    private static final String HR_PASSWORD = "Sup3r-Secret-Pass!";
    private static final String CANDIDATE_EMAIL = "offer.candidate@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HrUserRepository hrUserRepository;
    @Autowired private CandidateRepository candidateRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private com.cloudfuze.onboarding.repository.CandidateDocumentRepository documentRepository;
    @Autowired private com.cloudfuze.onboarding.repository.CandidateProfileRepository profileRepository;
    @Autowired private com.cloudfuze.onboarding.repository.AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /* Spied, not mocked: the real transport still runs, so a message the
       composer cannot build fails here rather than passing quietly. */
    @SpyBean private EmailService emailService;

    private String hrToken;
    private String portalToken;
    private UUID candidateId;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        offerRepository.deleteAll();
        profileRepository.deleteAll();
        documentRepository.deleteAll();
        candidateRepository.deleteAll();
        hrUserRepository.deleteAll();

        HrUser hr = new HrUser(HR_EMAIL, passwordEncoder.encode(HR_PASSWORD), "Offer HR", "HR");
        hr.setRole(HrRole.ADMIN);
        hrUserRepository.save(hr);
        hrToken = login();

        portalToken = createCandidate();
        candidateId = candidateRepository.findByEmailIgnoreCase(CANDIDATE_EMAIL).orElseThrow().getId();
        getCandidateApproved();
        uploadOfferDraft();
    }

    @Test
    @DisplayName("Sending an offer emails the candidate a link straight to the signing page")
    void sendingEmailsTheCandidate() throws Exception {
        sendOffer().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("sent"));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService, atLeastOnce()).send(sent.capture());

        EmailMessage offerMail = sent.getAllValues().stream()
                .filter(m -> m.subject().toLowerCase().contains("offer letter"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No offer email was sent"));

        assertThat(offerMail.toAddress()).isEqualTo(CANDIDATE_EMAIL);

        /*
         * The link must land on the signing page itself, carrying their token -
         * a mail that only says "log in and look" is the problem, not the fix.
         * /upload/<token> is the front door every candidate email uses; the app
         * forwards the rest of the path on to /portal/<token>/offer.
         */
        assertThat(offerMail.textBody()).contains("/upload/" + portalToken + "/offer");
        assertThat(offerMail.htmlBody()).contains("/upload/" + portalToken + "/offer");
    }

    @Test
    @DisplayName("If the email cannot go out, the letter is not marked sent")
    void undeliverableEmailLeavesTheOfferADraft() throws Exception {
        doThrow(new RuntimeException("smtp unavailable")).when(emailService).send(any(EmailMessage.class));

        sendOffer().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_SENT"));

        // Still a draft, so HR can try again rather than believing it went out.
        assertThat(offerRepository.findByCandidateId(candidateId).orElseThrow().getStatus())
                .isEqualTo(OfferStatus.DRAFT);
    }

    /* ---- setup helpers ---- */

    private String createCandidate() throws Exception {
        String payload = """
                {
                  "name": "Priya Sharma",
                  "email": "%s",
                  "role": "Software Engineer",
                  "department": "Engineering",
                  "requiredDocuments": [{"type": "aadhaar_id", "mandatory": true}]
                }""".formatted(CANDIDATE_EMAIL);

        MvcResult result = mockMvc.perform(post("/api/hr/candidates")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        String path = URI.create(json(result).path("invitation").path("portalUrl").asText()).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Upload, submit, verify and approve - an offer cannot be sent before that. */
    private void getCandidateApproved() throws Exception {
        mockMvc.perform(multipart("/api/portal/{token}/documents/{type}", portalToken, "aadhaar_id")
                        .file(new MockMultipartFile("file", "aadhaar.pdf", "application/pdf", pdfBytes())))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/portal/{token}/profile", portalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/portal/{token}/submit", portalToken))
                .andExpect(status().isOk());

        UUID documentId = documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId)
                .get(0).getId();
        mockMvc.perform(post("/api/hr/documents/{id}/verify", documentId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/hr/candidates/{id}/notify", candidateId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());
    }

    private void uploadOfferDraft() throws Exception {
        mockMvc.perform(multipart("/api/hr/candidates/{id}/offer", candidateId)
                        .file(new MockMultipartFile("file", "offer-letter.pdf", "application/pdf", pdfBytes()))
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/hr/candidates/{id}/offer/fields", candidateId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields":[{"type":"signature","page":1,"xPct":10,"yPct":80,
                                            "widthPct":25,"heightPct":8}]}"""))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions sendOffer() throws Exception {
        return mockMvc.perform(post("/api/hr/candidates/{id}/offer/send", candidateId)
                .header("Authorization", "Bearer " + hrToken));
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
