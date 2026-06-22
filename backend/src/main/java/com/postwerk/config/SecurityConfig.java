package com.postwerk.config;

import com.postwerk.service.CustomUserDetailsService;
import com.postwerk.util.WebhookConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for stateless JWT-based authentication.
 *
 * <p>Configures CSRF (disabled for REST API), session management (stateless),
 * authorization rules (public: /auth/**, /health; authenticated: all others),
 * BCrypt password encoding, and JWT filter chain integration.</p>
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitFilter rateLimitFilter,
                          CustomUserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})
            // CSRF is disabled because the API is stateless and authenticated exclusively via
            // Bearer JWTs in the Authorization header (no cookies / no server session). CSRF
            // attacks rely on the browser auto-attaching ambient credentials; a token that must
            // be read from storage and set as a header is not auto-sent cross-site, so CSRF does
            // not apply. If cookie-based auth is ever introduced, CSRF protection MUST be re-enabled.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Stateless JWT API: an unauthenticated request must get 401 (authenticate), not Spring's
            // default 403. An authenticated-but-insufficient request still yields 403 via the
            // access-denied handler. (Previously relied on the framework default, which returns 403.)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/api/v1/health").permitAll()
                    .requestMatchers("/api/v1/wizard/chat", "/api/v1/wizard/session/**").permitAll()
                    .requestMatchers(WebhookConstants.HOOKS_PATH_PREFIX + "**").permitAll();
                // Only expose Swagger UI when enabled (dev profile)
                if (swaggerEnabled) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**").permitAll();
                }
                auth.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/**").hasRole("ADMIN")
                    // /admin/me is the caller's OWN staff identity (null staffRole for normal users) —
                    // every authenticated user's UI calls it to decide whether to show admin features,
                    // so it must NOT be STAFF-gated (otherwise a normal login 401s here → redirect loop).
                    .requestMatchers("/api/v1/admin/me").authenticated()
                    // All other admin endpoints require platform staff (non-null staffRole → ROLE_STAFF);
                    // per-endpoint @PreAuthorize then gates each action on a discrete StaffPermission.
                    .requestMatchers("/api/v1/admin/**").hasRole("STAFF")
                    .anyRequest().authenticated();
            })
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
