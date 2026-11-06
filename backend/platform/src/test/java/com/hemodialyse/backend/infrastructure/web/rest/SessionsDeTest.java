package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Sessions de test qui portent leur centre : la garde de centre (revue DM1, KB-MED-02) exige un principal réel
 * ({@link UserPrincipal}) dont le centre est celui de la requête ; un {@code user("x").roles(...)} nu est refusé (403).
 */
final class SessionsDeTest {

    private SessionsDeTest() {
    }

    static RequestPostProcessor session(UUID centre, String nom, String role) {
        return user(new UserPrincipal(UUID.nameUUIDFromBytes(nom.getBytes()).toString(), centre.toString(), nom, "",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), true));
    }
}
