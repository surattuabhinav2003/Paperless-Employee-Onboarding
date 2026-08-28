package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.config.AzureAdProperties;
import com.cloudfuze.onboarding.dto.CreateHrUserRequest;
import com.cloudfuze.onboarding.dto.HrUserDto;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.HrUser;
import com.cloudfuze.onboarding.repository.HrUserRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Who can sign in, and which of them are administrators. */
@Service
public class HrUserAdminService {

    private static final Logger log = LoggerFactory.getLogger(HrUserAdminService.class);

    private final HrUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AzureAdProperties azureProperties;

    public HrUserAdminService(HrUserRepository repository, PasswordEncoder passwordEncoder,
                              AzureAdProperties azureProperties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.azureProperties = azureProperties;
    }

    /**
     * Adds someone to the console before they have ever signed in.
     *
     * <p>Until now a person appeared only by signing in with Microsoft, which
     * meant an admin could not hand out admin access ahead of time - and could
     * not grant access at all without a config change and a restart. The row
     * written here is what {@code AuthService} finds on their first sign-in, so
     * the role an admin chose is the role they arrive with.
     *
     * <p>The password is random and never told to anyone: the column is NOT
     * NULL, and an unusable value keeps the email/password path closed for an
     * account that is meant to be SSO-only.
     */
    @Transactional
    public HrUserDto add(CreateHrUserRequest request, HrPrincipal actor) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        repository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            throw new BusinessRuleException("USER_ALREADY_EXISTS",
                    existing.getEmail() + " is already on this list.");
        });

        String name = request.fullName() == null || request.fullName().isBlank()
                // Microsoft overwrites this on first sign-in; until then the
                // local part is a better placeholder than an empty cell.
                ? email.split("@")[0]
                : request.fullName().trim();
        String jobTitle = request.jobTitle() == null || request.jobTitle().isBlank()
                ? azureProperties.getDefaultJobTitle()
                : request.jobTitle().trim();

        HrUser user = new HrUser(email, passwordEncoder.encode("sso:" + UUID.randomUUID()), name, jobTitle);
        user.setRole(request.role() == null ? HrRole.HR : request.role());
        repository.save(user);

        log.info("Admin {} added {} as {}", actor.getEmail(), email, user.getRole().getCode());
        return HrUserDto.from(user);
    }

    @Transactional(readOnly = true)
    public List<HrUserDto> list() {
        return repository.findAll().stream()
                // Admins first, then alphabetical - the list is read to answer
                // "who can change things", so that belongs at the top.
                .sorted(Comparator.comparing((HrUser u) -> !u.isAdmin())
                        .thenComparing(u -> u.getEmail().toLowerCase()))
                .map(HrUserDto::from)
                .toList();
    }

    /**
     * Grants or revokes admin.
     *
     * <p>Two things are refused: removing your own admin rights, which is
     * almost always a misclick and locks you out of the screen you are standing
     * on; and removing the last admin, which would leave nobody able to grant it
     * back.
     */
    @Transactional
    public HrUserDto setRole(UUID userId, HrRole role, HrPrincipal actor) {
        HrUser user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No HR user with id " + userId));

        if (user.getRole() == role) {
            return HrUserDto.from(user);
        }

        if (role != HrRole.ADMIN) {
            if (user.getId().equals(actor.getId())) {
                throw new BusinessRuleException("CANNOT_DEMOTE_SELF",
                        "You cannot remove your own admin access. Ask another admin to do it.");
            }
            long admins = repository.findAll().stream().filter(HrUser::isAdmin).count();
            if (admins <= 1) {
                throw new BusinessRuleException("LAST_ADMIN",
                        "This is the only administrator. Make someone else an admin first.");
            }
        }

        user.setRole(role);
        repository.save(user);
        log.info("HR {} set {} to {}", actor.getEmail(), user.getEmail(), role.getCode());
        return HrUserDto.from(user);
    }
}
