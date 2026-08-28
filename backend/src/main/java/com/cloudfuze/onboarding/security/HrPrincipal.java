package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.HrUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Authenticated HR user attached to the security context. */
public class HrPrincipal implements UserDetails {

    public static final String ROLE = "ROLE_HR";
    /** Granted on top of ROLE_HR, so admin endpoints can require it alone. */
    public static final String ADMIN_ROLE = "ROLE_HR_ADMIN";

    private final UUID id;
    private final String email;
    private final String fullName;
    private final String jobTitle;
    private final String passwordHash;
    private final boolean active;
    private final HrRole role;

    public HrPrincipal(HrUser user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.jobTitle = user.getJobTitle();
        this.passwordHash = user.getPasswordHash();
        this.active = user.isActive();
        this.role = user.getRole() == null ? HrRole.HR : user.getRole();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public HrRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role.isAdmin();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role.isAdmin()
                ? List.of(new SimpleGrantedAuthority(ROLE), new SimpleGrantedAuthority(ADMIN_ROLE))
                : List.of(new SimpleGrantedAuthority(ROLE));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
