package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.dto.CategoryRequest;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.Category;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository repository;
    @Mock private AuditService auditService;
    @Mock private EmbeddingService embeddingService;

    @InjectMocks
    private CategoryServiceImpl service;

    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void create_validData_persists() {
        var request = new CategoryRequest("Work", "#3b82f6", "Work emails", "Invoice", "Newsletter");
        when(repository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var response = service.create(orgId, userId, request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Work");
        verify(auditService).log(eq(userId), eq(AuditAction.CATEGORY_CREATED), anyString(), anyString());
    }

    @Test
    void update_changesFields() {
        var category = TestFixtures.createCategory(userId);
        when(repository.findByIdAndOrganizationId(category.getId(), orgId)).thenReturn(Optional.of(category));
        when(repository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CategoryRequest("Updated", "#ef4444", "Updated desc", "pos", "neg");
        var response = service.update(orgId, userId, category.getId(), request, "127.0.0.1");

        assertThat(response.name()).isEqualTo("Updated");
        verify(auditService).logDiff(eq(userId), eq(AuditAction.CATEGORY_UPDATED), any(), any(), anyString(), anyString());
    }

    @Test
    void delete_removesCategory() {
        var category = TestFixtures.createCategory(userId);
        when(repository.findByIdAndOrganizationId(category.getId(), orgId)).thenReturn(Optional.of(category));

        service.delete(orgId, userId, category.getId(), "127.0.0.1");

        verify(repository).delete(category);
        verify(auditService).log(eq(userId), eq(AuditAction.CATEGORY_DELETED), anyString(), anyString());
    }

    @Test
    void listByOrg_returnsAll() {
        // listByOrg now uses the embedding-free scalar projection (findViewByOrganizationId).
        when(repository.findViewByOrganizationId(orgId)).thenReturn(List.of(
                mock(CategoryRepository.CategoryView.class),
                mock(CategoryRepository.CategoryView.class)));

        var result = service.listByOrg(orgId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getById_notFound_throws() {
        when(repository.findByIdAndOrganizationId(any(), eq(orgId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(orgId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_embeddingFailure_doesNotPropagate() throws Exception {
        var request = new CategoryRequest("Work", "#3b82f6", "desc", null, null);
        when(embeddingService.embed(any(), any(), anyString())).thenThrow(new Exception("API error"));
        when(repository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        // Should not throw — embedding failure is non-critical
        var response = service.create(orgId, userId, request, "127.0.0.1");
        assertThat(response).isNotNull();
    }
}
