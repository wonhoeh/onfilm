package com.onfilm.domain.auth.config;

import com.onfilm.domain.auth.security.AuthPageBlockFilter;
import com.onfilm.domain.auth.security.CsrfProtectionFilter;
import com.onfilm.domain.auth.security.JwtAuthFilter;
import com.onfilm.domain.auth.security.InternalCallbackHmacFilter;
import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.SecurityErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthPageBlockFilter authPageBlockFilter;
    private final CsrfProtectionFilter csrfProtectionFilter;
    private final InternalCallbackHmacFilter internalCallbackHmacFilter;
    private final HandlerMappingIntrospector handlerMappingIntrospector;
    private final SecurityErrorResponseWriter errorResponseWriter;

    private static final String USERNAME_PATTERN = "[a-zA-Z0-9_-]{3,20}";

    // =========================
    // DEV
    // =========================
    @Bean
    @Profile("dev")
    SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        baseConfig(http)
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers(staticMatchers()).permitAll()
                        .requestMatchers(userPageMatchers()).permitAll()
                        .requestMatchers(userAuthPageMatchers()).authenticated()
                        .requestMatchers(EndpointRequest.to("health", "info", "prometheus")).permitAll()
                        .requestMatchers("/health", "/health/**").permitAll()
                        .requestMatchers("/auth/login", "/auth/signup", "/auth/refresh", "/auth/logout",
                                         "/auth/check-email", "/auth/check-username").permitAll()
                        .requestMatchers("/auth/me").authenticated()
                        .requestMatchers("/internal/api/**").hasRole("MEDIA_WORKER")
                        .requestMatchers("/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/person/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/people/*",
                                                         "/api/people/*/movies",
                                                         "/api/people/*/gallery",
                                                         "/api/people/*/filmography").permitAll()
                        .requestMatchers("/api/people/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(authPageBlockFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(csrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalCallbackHmacFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // =========================
    // PROD
    // =========================
    @Bean
    @Profile("!dev")
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        baseConfig(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers(staticMatchers()).permitAll()
                        .requestMatchers(userPageMatchers()).permitAll()
                        .requestMatchers(userAuthPageMatchers()).authenticated()
                        .requestMatchers(EndpointRequest.to("health", "info", "prometheus")).permitAll()
                        .requestMatchers("/health", "/health/**").permitAll()
                        .requestMatchers("/auth/login", "/auth/signup", "/auth/refresh", "/auth/logout",
                                         "/auth/check-email", "/auth/check-username").permitAll()
                        .requestMatchers("/auth/me").authenticated()
                        .requestMatchers("/internal/api/**").hasRole("MEDIA_WORKER")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/person/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/people/*",
                                                         "/api/people/*/movies",
                                                         "/api/people/*/gallery",
                                                         "/api/people/*/filmography").permitAll()
                        .requestMatchers("/api/people/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(authPageBlockFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(csrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalCallbackHmacFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // =========================
    // 공통 보안 설정
    // =========================
    private HttpSecurity baseConfig(HttpSecurity http) throws Exception {
        return http
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            String accept = req.getHeader("Accept");
                            if (accept != null && accept.contains("text/html")) {
                                res.sendRedirect("/login.html");
                            } else {
                                errorResponseWriter.write(res, ErrorCode.AUTHENTICATION_REQUIRED);
                            }
                        })
                        .accessDeniedHandler((req, res, ex) ->
                                errorResponseWriter.write(res, ErrorCode.ACCESS_DENIED)
                        )
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable);
    }

    // 정적 리소스 + 공개 페이지
    private AntPathRequestMatcher[] staticMatchers() {
        return new AntPathRequestMatcher[]{
                new AntPathRequestMatcher("/"),
                new AntPathRequestMatcher("/*.html"),
                new AntPathRequestMatcher("/js/**"),
                new AntPathRequestMatcher("/css/**"),
                new AntPathRequestMatcher("/images/**"),
                new AntPathRequestMatcher("/videos/**"),
                new AntPathRequestMatcher("/vendor/**"),
                new AntPathRequestMatcher("/favicon.ico"),
                new AntPathRequestMatcher("/files/**")
        };
    }

    // /{username} 공개 페이지 패턴
    private MvcRequestMatcher[] userPageMatchers() {
        return new MvcRequestMatcher[]{
                mvc("/{username:" + USERNAME_PATTERN + "}"),
                mvc("/{username:" + USERNAME_PATTERN + "}/storyboard"),
                mvc("/{username:" + USERNAME_PATTERN + "}/storyboard-view"),
        };
    }

    // /{username} 인증 필요 페이지 패턴
    private MvcRequestMatcher[] userAuthPageMatchers() {
        return new MvcRequestMatcher[]{
                mvc("/{username:" + USERNAME_PATTERN + "}/edit-profile"),
                mvc("/{username:" + USERNAME_PATTERN + "}/edit-filmography"),
                mvc("/{username:" + USERNAME_PATTERN + "}/edit-gallery"),
                mvc("/{username:" + USERNAME_PATTERN + "}/edit-storyboard"),
        };
    }

    private MvcRequestMatcher mvc(String pattern) {
        return new MvcRequestMatcher(handlerMappingIntrospector, pattern);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
