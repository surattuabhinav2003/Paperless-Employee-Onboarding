package com.cloudfuze.onboarding.security;

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

    private final UUID id;
    private final String email;
    private final String fullName;
    private final String jobTitle;
    private final String passwordHash;
    private final boolean active;

    public HrPrincipal(HrUser user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.jobTitle = user.getJobTitle();
        this.passwordHash = user.getPasswordHash();
        this.active = user.isActive();
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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE));
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
