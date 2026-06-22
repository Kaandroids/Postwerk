package com.postwerk.config;

import com.postwerk.util.OrgAuditContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures the per-request {@link OrgAuditContext} thread-local never leaks across pooled request threads.
 * The active org is populated (with the validated value) by {@code OrgContextService.resolve(...)} while the
 * request is handled; this filter guarantees it is cleared both before and after the request runs.
 *
 * @since 1.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrgAuditContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        OrgAuditContext.clear();
        try {
            chain.doFilter(request, response);
        } finally {
            OrgAuditContext.clear();
        }
    }
}
