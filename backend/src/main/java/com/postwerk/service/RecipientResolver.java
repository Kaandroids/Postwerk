package com.postwerk.service;

import com.postwerk.repository.MembershipRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Resolves the recipients of a system-generated, org-scoped notification: the resource owner plus
 * the active owners/admins of the organization (deduped). Personal events resolve to the user alone;
 * the NOTIFY automation node resolves its recipient explicitly and does not use this. See
 * {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
@Component
public class RecipientResolver {

    private final MembershipRepository membershipRepository;

    public RecipientResolver(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    /** Owner first, then the org's active owners/admins. Order-preserving and deduped. */
    public List<UUID> ownerAndOrgAdmins(UUID organizationId, UUID ownerUserId) {
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        if (ownerUserId != null) {
            recipients.add(ownerUserId);
        }
        if (organizationId != null) {
            recipients.addAll(membershipRepository.findActiveAdminUserIds(organizationId));
        }
        return new ArrayList<>(recipients);
    }
}
