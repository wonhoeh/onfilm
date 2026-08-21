package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Entity
@Getter
@Table(
        name = "person_sns",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_person_sns_url",
                columnNames = {"person_id", "url"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonSns {
    public static final int URL_MAX_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SnsType type;

    @Column(nullable = false, length = URL_MAX_LENGTH)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    private PersonSns(SnsType type, String url) {
        this.type = type;
        this.url = url;
    }

    static PersonSns create(SnsType type, String url) {
        requireType(type);
        return new PersonSns(type, normalizeUrl(url));
    }

    void changeType(SnsType type) {
        requireType(type);
        this.type = type;
    }

    void attachPerson(Person person) {
        if (person == null) {
            throw new IllegalArgumentException("person is required");
        }
        if (this.person != null && this.person != person) {
            throw new IllegalStateException(
                    "personSns already belongs to another person"
            );
        }
        this.person = person;
    }

    void detachPerson(Person person) {
        if (this.person == person) {
            this.person = null;
        }
    }

    private static void requireType(SnsType type) {
        if (type == null) {
            throw new IllegalArgumentException("sns type is required");
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("sns url is required");
        }

        String trimmed = url.trim();
        String candidate = hasScheme(trimmed) ? trimmed : "https://" + trimmed;

        URI parsed;
        try {
            parsed = new URI(candidate).normalize();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid sns url");
        }

        String scheme = parsed.getScheme();
        if (scheme == null ||
                !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("sns url must use http or https");
        }
        if (parsed.getUserInfo() != null) {
            throw new IllegalArgumentException("sns url must not contain user info");
        }

        String host = parsed.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("sns url host is required");
        }

        int port = normalizePort(scheme, parsed.getPort());
        String normalizedUrl = buildUrl(
                scheme.toLowerCase(Locale.ROOT),
                host.toLowerCase(Locale.ROOT),
                port,
                normalizePath(parsed.getRawPath()),
                parsed.getRawQuery(),
                parsed.getRawFragment()
        );
        if (normalizedUrl.length() > URL_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "sns url is too long (max " + URL_MAX_LENGTH + ")"
            );
        }
        return normalizedUrl;
    }

    private static boolean hasScheme(String url) {
        return url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$");
    }

    private static int normalizePort(String scheme, int port) {
        if ((scheme.equalsIgnoreCase("http") && port == 80) ||
                (scheme.equalsIgnoreCase("https") && port == 443)) {
            return -1;
        }
        return port;
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.equals("/")) {
            return "";
        }

        String normalized = rawPath;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String buildUrl(
            String scheme,
            String host,
            int port,
            String rawPath,
            String rawQuery,
            String rawFragment
    ) {
        String normalizedHost = host.contains(":") ? "[" + host + "]" : host;
        StringBuilder result = new StringBuilder()
                .append(scheme)
                .append("://")
                .append(normalizedHost);
        if (port >= 0) result.append(':').append(port);
        result.append(rawPath);
        if (rawQuery != null) result.append('?').append(rawQuery);
        if (rawFragment != null) result.append('#').append(rawFragment);
        return result.toString();
    }
}
