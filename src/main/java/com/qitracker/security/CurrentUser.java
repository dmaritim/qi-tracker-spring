package com.qitracker.security;

import com.qitracker.domain.User;
import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() {}

    public static User get(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal)) {
            throw new IllegalStateException("Not signed in.");
        }
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return principal.getUser();
    }
}
