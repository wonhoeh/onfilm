package com.onfilm.domain.common.config;

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
                || path.startsWith("/internal/api/");
    }
}
