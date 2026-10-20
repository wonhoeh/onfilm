package com.onfilm.domain.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Non-secret process identity: the launcher must not mistake another HTTP server for its child. */
@RestController
@Profile("local")
public class DesktopHealthController {
    private final String instanceId;

    public DesktopHealthController(@Value("${onfilm.desktop.instance-id}") String instanceId) {
        this.instanceId = instanceId;
    }

    @GetMapping("/health/desktop")
    public DesktopStatus status() {
        return new DesktopStatus("onfilm-api", instanceId);
    }

    public record DesktopStatus(String service, String instanceId) { }
}
