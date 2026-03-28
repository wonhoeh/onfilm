package com.onfilm.domain.auth.security;

import com.onfilm.domain.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevCsrfProtectionFilter extends CsrfProtectionFilter {

    public DevCsrfProtectionFilter(AuthProperties authProperties) {
        super(authProperties);
    }

    @Override
    protected boolean shouldSkipByPath(String path) {
        return super.shouldSkipByPath(path)
                || path.startsWith("/h2-console")
                || path.startsWith("/internal/api/");
    }
}
