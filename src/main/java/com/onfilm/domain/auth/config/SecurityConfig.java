package com.onfilm.domain.auth.config;

import com.onfilm.domain.auth.security.AuthPageBlockFilter;
import com.onfilm.domain.auth.security.CsrfProtectionFilter;
import com.onfilm.domain.auth.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
    private final HandlerMappingIntrospector handlerMappingIntrospector;

    // =========================
    // DEV
    // =========================
    @Bean
    @Profile("dev")
    SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        MvcRequestMatcher publicProfileMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}");
        MvcRequestMatcher userEditProfileMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/edit-profile");
        MvcRequestMatcher userEditFilmographyMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/edit-filmography");
        MvcRequestMatcher userEditGalleryMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/edit-gallery");
        MvcRequestMatcher userStoryboardMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/storyboard");
        MvcRequestMatcher userEditStoryboardMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/edit-storyboard");
        MvcRequestMatcher userStoryboardViewMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/storyboard-view");

        http
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                        })
                )
                // 세션 안씀
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 폼 로그인/베이직 끔
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 개발에서만 H2-console iframe 허용
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))

                // CSRF 비활성화 (쿠키 기반 refresh/logout이면 별도 CSRF 방어 권장)
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // ✅ 정적 리소스 (가장 안전)
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                        // ✅ 로컬 파일 서빙(/files/**) - img/video 접근 위해 permitAll 권장
                        .requestMatchers("/files/**").permitAll()

                        // ✅ 직접 접근할 html/js/css/assets 허용
                        .requestMatchers(
                                new AntPathRequestMatcher("/"),
                                new AntPathRequestMatcher("/index.html"),
                                new AntPathRequestMatcher("/login.html"),
                                new AntPathRequestMatcher("/signup.html"),
                                new AntPathRequestMatcher("/*.html"),
                                new AntPathRequestMatcher("/js/**"),
                                new AntPathRequestMatcher("/css/**"),
                                new AntPathRequestMatcher("/images/**"),
                                new AntPathRequestMatcher("/videos/**"),
                                new AntPathRequestMatcher("/vendor/**"),
                                new AntPathRequestMatcher("/favicon.ico"),
                                new AntPathRequestMatcher("/edit-profile"),
                                new AntPathRequestMatcher("/edit-filmography"),
                                new AntPathRequestMatcher("/edit-gallery"),
                                new AntPathRequestMatcher("/storyboard"),
                                new AntPathRequestMatcher("/edit-storyboard"),
                                new AntPathRequestMatcher("/storyboard-view"),
                                new AntPathRequestMatcher("/api/person/**"),
                                new AntPathRequestMatcher("/api/people/**"),
                                new AntPathRequestMatcher("/internal/api/**"),
                                new AntPathRequestMatcher("/auth/**"),
                                publicProfileMatcher,
                                userEditProfileMatcher,
                                userEditFilmographyMatcher,
                                userEditGalleryMatcher,
                                userStoryboardMatcher,
                                userEditStoryboardMatcher,
                                userStoryboardViewMatcher
                                ).permitAll()

                        // ✅ 헬스 체크 (ELB 등)
                        .requestMatchers("/health", "/health/**").permitAll()

                        // ✅ auth 정책
                        .requestMatchers("/auth/login", "/auth/signup", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers("/auth/check-email", "/auth/check-username").permitAll()
                        .requestMatchers("/auth/me").authenticated()

                        // ✅ dev 전용
                        .requestMatchers("/h2-console/**").permitAll()

                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )

                // JWT 필터
                .addFilterBefore(authPageBlockFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(csrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // =========================
    // PROD (dev 아닐 때 전부)
    // =========================
    @Bean
    @Profile("!dev")
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        MvcRequestMatcher publicProfileMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}");
        MvcRequestMatcher userEditProfileMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/edit-profile");
        MvcRequestMatcher userEditFilmographyMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/edit-filmography");
        MvcRequestMatcher userEditGalleryMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/edit-gallery");
        MvcRequestMatcher userStoryboardMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/storyboard");
        MvcRequestMatcher userEditStoryboardMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/edit-storyboard");
        MvcRequestMatcher userStoryboardViewMatcher =
                new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}/storyboard-view");

        http
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                        })
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // ✅ prod에서도 정적 리소스는 반드시 열어야 함 (안 열면 /js/auth.js 403)
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers(
                                new AntPathRequestMatcher("/"),
                                new AntPathRequestMatcher("/index.html"),
                                new AntPathRequestMatcher("/login.html"),
                                new AntPathRequestMatcher("/signup.html"),
                                new AntPathRequestMatcher("/*.html"),
                                new AntPathRequestMatcher("/js/**"),
                                new AntPathRequestMatcher("/css/**"),
                                new AntPathRequestMatcher("/images/**"),
                                new AntPathRequestMatcher("/videos/**"),
                                new AntPathRequestMatcher("/vendor/**"),
                                new AntPathRequestMatcher("/favicon.ico"),
                                new AntPathRequestMatcher("/edit-profile"),
                                new AntPathRequestMatcher("/edit-filmography"),
                                new AntPathRequestMatcher("/edit-gallery"),
                                new AntPathRequestMatcher("/storyboard"),
                                new AntPathRequestMatcher("/edit-storyboard"),
                                new AntPathRequestMatcher("/storyboard-view"),
                                new AntPathRequestMatcher("/api/person/**"),
                                new AntPathRequestMatcher("/api/people/**"),
                                new AntPathRequestMatcher("/internal/api/**"),
                                publicProfileMatcher,
                                userEditProfileMatcher,
                                userEditFilmographyMatcher,
                                userEditGalleryMatcher,
                                userStoryboardMatcher,
                                userEditStoryboardMatcher,
                                userStoryboardViewMatcher
                                ).permitAll()

                        // ✅ 헬스 체크 (ELB 등)
                        .requestMatchers("/health", "/health/**").permitAll()

                        .requestMatchers("/auth/login", "/auth/signup", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers("/auth/check-email", "/auth/check-username").permitAll()
                        .requestMatchers("/auth/me").authenticated()

                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )

                .addFilterBefore(authPageBlockFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(csrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
