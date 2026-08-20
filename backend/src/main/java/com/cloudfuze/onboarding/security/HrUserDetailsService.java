package com.cloudfuze.onboarding.security;

import com.cloudfuze.onboarding.repository.HrUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class HrUserDetailsService implements UserDetailsService {

    private final HrUserRepository hrUserRepository;

    public HrUserDetailsService(HrUserRepository hrUserRepository) {
        this.hrUserRepository = hrUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return hrUserRepository.findByEmailIgnoreCase(email)
                .map(HrPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No HR user for " + email));
    }
}
