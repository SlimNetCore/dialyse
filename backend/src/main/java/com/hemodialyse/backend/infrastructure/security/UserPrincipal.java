package com.hemodialyse.backend.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class UserPrincipal implements UserDetails {

    private final String id;          // user_id (String in our app)
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean actif;

    public UserPrincipal(String id,
                         String username,
                         String password,
                         Collection<? extends GrantedAuthority> authorities,
                         boolean actif) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.actif = actif;
    }

    public static UserPrincipal create(String id, String username, String password,
                                       List<String> roles, boolean actif) {
        List<GrantedAuthority> authorities = roles.stream()
            .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
            .collect(Collectors.toList());
        return new UserPrincipal(id, username, password, authorities, actif);
    }

    public String getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return actif; }
}
