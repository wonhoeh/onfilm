package com.onfilm.domain.common.util;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public final class SecurityUtil {

    private SecurityUtil() {}

    public static Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("UNAUTHENTICATED");
        }
        return auth;
    }

    public static Long currentUserId() {
        Authentication auth = currentAuth();
        Object principal = auth.getPrincipal();

        // 1) principal이 UserDetails인 경우 (Spring Security 기본)
        if (principal instanceof UserDetails ud) {
            return parseId(ud.getUsername());
        }

        // 2) principal이 String인 경우
        if (principal instanceof String s) {
            return parseId(s);
        }

        throw new IllegalStateException("UNSUPPORTED_PRINCIPAL_TYPE: " + principal.getClass().getName());
    }

    private static Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("INVALID_USER_ID_IN_PRINCIPAL: " + value, e);
        }
    }
}