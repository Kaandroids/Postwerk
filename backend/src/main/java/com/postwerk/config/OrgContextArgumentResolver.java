package com.postwerk.config;

import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.security.Principal;

/**
 * Resolves a controller method parameter of type {@link OrgContext} from the authenticated
 * principal and the {@code X-Org-Id} header. Removes the boilerplate
 * {@code OrgContext ctx = orgContext.resolve(userDetails, request)} (and the
 * {@code @AuthenticationPrincipal UserDetails} + {@code HttpServletRequest} parameters)
 * from every org-scoped controller method.
 *
 * <p><strong>Context resolution only — no authorization decision.</strong> Handlers still gate
 * access explicitly via {@link OrgContextService#require(OrgContext, com.postwerk.model.enums.Permission)}
 * (and the mailbox/role variants), so the security check stays visible and auditable at each call
 * site rather than hidden behind a declarative annotation.</p>
 *
 * @since 1.0
 */
@Component
public class OrgContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final OrgContextService orgContextService;

    public OrgContextArgumentResolver(OrgContextService orgContextService) {
        this.orgContextService = orgContextService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return OrgContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return orgContextService.resolve(currentUser(webRequest), request);
    }

    private UserDetails currentUser(NativeWebRequest webRequest) {
        Principal principal = webRequest.getUserPrincipal();
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails;
        }
        throw new AccessDeniedException("Unauthenticated");
    }
}
