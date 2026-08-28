package com.cloudfuze.onboarding.config;

import com.cloudfuze.onboarding.dto.CreateCandidateRequest;
import com.cloudfuze.onboarding.dto.RequiredDocumentRequest;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.HrUserRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.service.CandidateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Development seeding. Creates the HR sign-in account (and optionally a few demo
 * candidates) so the flow can be exercised immediately. Disable with
 * {@code app.seed.enabled=false} in any shared environment.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties appProperties;
    private final HrUserRepository hrUserRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AppProperties appProperties, HrUserRepository hrUserRepository,
                      CandidateRepository candidateRepository, CandidateService candidateService,
                      PasswordEncoder passwordEncoder) {
        this.appProperties = appProperties;
        this.hrUserRepository = hrUserRepository;
        this.candidateRepository = candidateRepository;
        this.candidateService = candidateService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Seed seed = appProperties.getSeed();
        promoteConfiguredAdmins(seed);
        String email = seed.getHrEmail().trim().toLowerCase();

        HrUser hrUser = hrUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (hrUser == null) {
            String password = seed.getHrPassword();
            boolean generated = password == null || password.isBlank();
            if (generated) {
                password = randomPassword();
            }
            hrUser = new HrUser(email, passwordEncoder.encode(password), seed.getHrName(), seed.getHrJobTitle());
            hrUserRepository.save(hrUser);

            if (generated) {
                log.warn("""

                                =========================================================================
                                Seeded HR account (no APP_SEED_HR_PASSWORD was configured)
                                  email    : {}
                                  password : {}
                                Set app.seed.hr-password / APP_SEED_HR_PASSWORD to control this value.
                                =========================================================================""",
                        email, password);
            } else {
                log.info("Seeded HR account {} using the configured password", email);
            }
        }

        if (seed.isSampleCandidates() && candidateRepository.count() == 0) {
            seedSampleCandidates(hrUser);
        }
    }

    private void seedSampleCandidates(HrUser hrUser) {
        HrPrincipal principal = new HrPrincipal(hrUser);
        List<CreateCandidateRequest> samples = List.of(
                new CreateCandidateRequest("Aarav Mehta", "aarav.mehta@example.com", "Software Engineer",
                        "Engineering", List.of(
                        new RequiredDocumentRequest(DocumentType.SSC_CERTIFICATE.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.SECONDARY_EDUCATION_CERTIFICATE.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.HIGHER_EDUCATION_PROVISIONAL.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.HIGHER_EDUCATION_MARKSHEET.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.AADHAAR_ID.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.PAN_CARD.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.PASSPORT_PHOTO.getCode(), false, null))),
                new CreateCandidateRequest("Nisha Verma", "nisha.verma@example.com", "Customer Success Manager",
                        "Customer Success", List.of(
                        new RequiredDocumentRequest(DocumentType.AADHAAR_ID.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.EXPERIENCE_CERTIFICATE.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.RELIEVING_LETTER.getCode(), true, null))),
                new CreateCandidateRequest("Rohan Iyer", "rohan.iyer@example.com", "Product Designer",
                        "Design", List.of(
                        new RequiredDocumentRequest(DocumentType.AADHAAR_ID.getCode(), true, null),
                        new RequiredDocumentRequest(DocumentType.ADDRESS_PROOF.getCode(), true, null))));

        samples.forEach(sample -> {
            var created = candidateService.create(sample, principal, "127.0.0.1");
            log.info("Seeded candidate {} - portal link: {}", sample.email(), created.invitation().portalUrl());
        });
    }

    /**
     * Grants admin to the configured addresses. Runs every startup so an admin
     * added to the config is promoted without anyone editing the database, and
     * so a user auto-provisioned before the list changed still gets it.
     *
     * <p>Only ever promotes. Demoting happens in the admin screen; doing it here
     * would silently undo a deliberate change on the next restart.
     */
    private void promoteConfiguredAdmins(AppProperties.Seed seed) {
        hrUserRepository.findAll().stream()
                .filter(user -> !user.isAdmin() && seed.isAdminEmail(user.getEmail()))
                .forEach(user -> {
                    user.setRole(com.cloudfuze.onboarding.model.HrRole.ADMIN);
                    hrUserRepository.save(user);
                    log.info("Granted admin to {} from the configured admin list", user.getEmail());
                });
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return "Cf" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "!1";
    }
}
