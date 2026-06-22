package com.postwerk.service;

import com.postwerk.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientResolverTest {

    @Mock private MembershipRepository membershipRepository;

    private RecipientResolver resolver;

    private final UUID orgId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new RecipientResolver(membershipRepository);
    }

    @Test
    void dedupsOwnerWhoIsAlsoAdmin_andKeepsOwnerFirst() {
        when(membershipRepository.findActiveAdminUserIds(orgId)).thenReturn(List.of(owner, admin));

        List<UUID> recipients = resolver.ownerAndOrgAdmins(orgId, owner);

        assertThat(recipients).containsExactly(owner, admin); // owner not duplicated, ordered first
    }

    @Test
    void nullOrg_returnsOwnerOnly_withoutQueryingMembers() {
        List<UUID> recipients = resolver.ownerAndOrgAdmins(null, owner);

        assertThat(recipients).containsExactly(owner);
        verifyNoInteractions(membershipRepository);
    }
}
