package com.postwerk.event;

import java.util.UUID;

/**
 * Published when a user is invited to an organization ({@code OrganizationService.invite}). A
 * {@code @TransactionalEventListener(AFTER_COMMIT)} turns it into a {@code TEAM_INVITED} notification
 * for the invitee (so it only fires once the invite row is committed). Decoupled via Spring events so
 * the membership path never depends on the notification subsystem.
 *
 * @since 1.0
 */
public record TeamInvitedEvent(UUID organizationId,
                               UUID invitedUserId,
                               String organizationName,
                               String invitedByName) {
}
