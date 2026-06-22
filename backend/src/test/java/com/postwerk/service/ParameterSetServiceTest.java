package com.postwerk.service;

import com.postwerk.dto.ParameterItemDto;
import com.postwerk.dto.ParameterSetRequest;
import com.postwerk.dto.ParameterSetResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.ParameterSet;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.service.impl.ParameterSetServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParameterSetServiceTest {

    @Mock private ParameterSetRepository repository;
    @Mock private AuditService auditService;

    private ParameterSetServiceImpl service;
    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new ParameterSetServiceImpl(repository, auditService, new ObjectMapper());
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void create_savesParameterSetAndLogs() {
        List<ParameterItemDto> params = List.of(
                new ParameterItemDto("customerName", "TEXT", "Customer name", null, null, true, false, null));

        ParameterSetRequest request = new ParameterSetRequest("Order Params", params);

        when(repository.save(any(ParameterSet.class))).thenAnswer(inv -> {
            ParameterSet ps = inv.getArgument(0);
            ps.setId(UUID.randomUUID());
            ps.setCreatedAt(Instant.now());
            ps.setUpdatedAt(Instant.now());
            return ps;
        });

        ParameterSetResponse response = service.create(orgId, userId, request, "127.0.0.1");

        assertThat(response.name()).isEqualTo("Order Params");
        assertThat(response.parameters()).hasSize(1);
        verify(auditService).log(eq(userId), any(), anyString(), eq("127.0.0.1"));
    }

    @Test
    void create_reservedName_throwsException() {
        List<ParameterItemDto> params = List.of(
                new ParameterItemDto("fromAddress", "TEXT", "Reserved", null, null, true, false, null));

        ParameterSetRequest request = new ParameterSetRequest("Bad Params", params);

        assertThatThrownBy(() -> service.create(orgId, userId, request, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void listByOrg_returnsAllOrgParameterSets() {
        ParameterSet ps = buildParameterSet("Set 1");
        when(repository.findByOrganizationId(orgId)).thenReturn(List.of(ps));

        List<ParameterSetResponse> result = service.listByOrg(orgId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Set 1");
    }

    @Test
    void getById_existingSet_returnsResponse() {
        ParameterSet ps = buildParameterSet("Test Set");
        when(repository.findByIdAndOrganizationId(ps.getId(), orgId)).thenReturn(Optional.of(ps));

        ParameterSetResponse response = service.getById(orgId, ps.getId());

        assertThat(response.name()).isEqualTo("Test Set");
    }

    @Test
    void getById_nonExistent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(orgId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_modifiesFieldsAndLogs() {
        ParameterSet existing = buildParameterSet("Old Name");
        when(repository.findByIdAndOrganizationId(existing.getId(), orgId)).thenReturn(Optional.of(existing));
        when(repository.save(any(ParameterSet.class))).thenReturn(existing);

        List<ParameterItemDto> newParams = List.of(
                new ParameterItemDto("amount", "NUMBER", "Amount", null, null, true, false, null));
        ParameterSetRequest request = new ParameterSetRequest("New Name", newParams);

        service.update(orgId, userId, existing.getId(), request, "127.0.0.1");

        assertThat(existing.getName()).isEqualTo("New Name");
        verify(auditService).logDiff(eq(userId), any(), any(), any(), anyString(), eq("127.0.0.1"));
    }

    @Test
    void delete_removesAndLogs() {
        ParameterSet ps = buildParameterSet("To Delete");
        when(repository.findByIdAndOrganizationId(ps.getId(), orgId)).thenReturn(Optional.of(ps));

        service.delete(orgId, userId, ps.getId(), "127.0.0.1");

        verify(repository).delete(ps);
        verify(auditService).log(eq(userId), any(), anyString(), eq("127.0.0.1"));
    }

    @Test
    void toggleLock_flipsLockState() {
        ParameterSet ps = buildParameterSet("Test");
        ps.setLocked(false);
        when(repository.findByIdAndOrganizationId(ps.getId(), orgId)).thenReturn(Optional.of(ps));
        when(repository.save(any(ParameterSet.class))).thenReturn(ps);

        service.toggleLock(orgId, ps.getId());

        assertThat(ps.isLocked()).isTrue();
    }

    private ParameterSet buildParameterSet(String name) {
        return ParameterSet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .organizationId(orgId)
                .name(name)
                .parameters("[]")
                .locked(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
