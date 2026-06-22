package com.postwerk.controller;

import com.postwerk.dto.WizardChatRequest;
import com.postwerk.dto.WizardClaimRequest;
import com.postwerk.dto.WizardClaimResponse;
import com.postwerk.dto.WizardSessionResponse;
import com.postwerk.service.UserIdResolverService;
import com.postwerk.service.WizardService;
import com.postwerk.util.IpResolverUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller for the public AI automation wizard.
 * Chat and session endpoints are public; claim requires authentication.
 */
@RestController
@RequestMapping("/api/v1/wizard")
@Tag(name = "Wizard", description = "Public AI automation wizard for onboarding")
public class WizardController {

    private final WizardService wizardService;
    private final UserIdResolverService userIdResolver;

    public WizardController(WizardService wizardService,
                            UserIdResolverService userIdResolver) {
        this.wizardService = wizardService;
        this.userIdResolver = userIdResolver;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody WizardChatRequest request,
                           HttpServletRequest httpRequest) {
        String ip = IpResolverUtil.extractIp(httpRequest);
        return wizardService.chatStream(request, ip);
    }

    @GetMapping("/session/{id}")
    public ResponseEntity<WizardSessionResponse> getSession(@PathVariable UUID id) {
        WizardSessionResponse session = wizardService.getSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    @PostMapping("/claim")
    public ResponseEntity<WizardClaimResponse> claim(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WizardClaimRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = userIdResolver.resolve(userDetails);
        String ip = IpResolverUtil.extractIp(httpRequest);
        WizardClaimResponse response = wizardService.claimSession(request.sessionId(), userId, ip);
        return ResponseEntity.ok(response);
    }
}
