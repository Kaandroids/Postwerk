package com.postwerk.config;

import com.postwerk.service.CustomUserDetailsService;
import com.postwerk.service.JwtService;
import com.postwerk.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that extracts and validates JWT bearer tokens from incoming HTTP requests.
 *
 * <p>Executes once per request, parsing the {@code Authorization} header, verifying the token
 * against the blacklist, and populating the {@link SecurityContextHolder} with the
 * authenticated principal when the token is valid.</p>
 *
 * @since 1.0
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService,
                                   TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);
        try {
            final String email = jwtService.extractEmail(token);
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                final String jti = jwtService.extractJti(token);
                if (tokenBlacklistService.isBlacklisted(jti)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                var userDetails = userDetailsService.loadUserByUsername(email);
                if (jwtService.isTokenValid(token, userDetails)) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ignored) {
            // Invalid token — let the request proceed unauthenticated
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Re-run this filter on ASYNC dispatches so the SecurityContext is repopulated when
     * an async request (e.g. SSE streaming endpoints) is dispatched back through the
     * filter chain. Without this, Spring Security's AuthorizationFilter — which filters
     * ASYNC dispatches by default — would deny access on the async re-dispatch because
     * the SecurityContext has already been cleared.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
