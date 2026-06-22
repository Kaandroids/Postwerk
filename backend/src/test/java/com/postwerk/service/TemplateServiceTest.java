package com.postwerk.service;

import com.postwerk.dto.TemplateRequest;
import com.postwerk.dto.TemplateResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Template;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.service.impl.TemplateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private ParameterSetRepository parameterSetRepository;
    @Mock private AuditService auditService;

    private TemplateServiceImpl service;
    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new TemplateServiceImpl(templateRepository, parameterSetRepository, auditService,
                new com.fasterxml.jackson.databind.ObjectMapper());
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void create_savesTemplateAndExtractsParams() {
        TemplateRequest request = new TemplateRequest(
                "Welcome", "Hello {{name}}", "Dear {{name}}, your order {{orderId}} is ready.", null);

        Template saved = buildTemplate("Welcome", "Hello {{name}}", "Dear {{name}}, your order {{orderId}} is ready.");
        when(templateRepository.save(any(Template.class))).thenReturn(saved);

        TemplateResponse response = service.create(orgId, userId, request, "127.0.0.1");

        assertThat(response.name()).isEqualTo("Welcome");
        verify(templateRepository).save(any(Template.class));
        verify(auditService).log(eq(userId), any(), anyString(), eq("127.0.0.1"));
    }

    @Test
    void create_extractsUniqueParams() {
        TemplateRequest request = new TemplateRequest(
                "Test", "{{name}} {{name}}", "{{name}} {{email}}", null);

        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        service.create(orgId, userId, request, "127.0.0.1");

        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).save(captor.capture());
        String params = captor.getValue().getParams();
        assertThat(params).contains("name").contains("email");
        // name should appear only once
        assertThat(params.indexOf("name")).isEqualTo(params.lastIndexOf("name"));
    }

    @Test
    void listByOrg_returnsAllOrgTemplates() {
        Template t1 = buildTemplate("Template 1", "Subject 1", "Body 1");
        Template t2 = buildTemplate("Template 2", "Subject 2", "Body 2");
        when(templateRepository.findByOrganizationId(orgId)).thenReturn(List.of(t1, t2));

        List<TemplateResponse> result = service.listByOrg(orgId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Template 1");
    }

    @Test
    void getById_existingTemplate_returnsResponse() {
        Template template = buildTemplate("Test", "Sub", "Body");
        when(templateRepository.findByIdAndOrganizationId(template.getId(), orgId))
                .thenReturn(Optional.of(template));

        TemplateResponse response = service.getById(orgId, template.getId());

        assertThat(response.name()).isEqualTo("Test");
    }

    @Test
    void getById_nonExistent_throwsNotFound() {
        UUID templateId = UUID.randomUUID();
        when(templateRepository.findByIdAndOrganizationId(templateId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(orgId, templateId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_modifiesFieldsAndLogs() {
        Template existing = buildTemplate("Old Name", "Old Subject", "Old Body");
        when(templateRepository.findByIdAndOrganizationId(existing.getId(), orgId))
                .thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenReturn(existing);

        TemplateRequest request = new TemplateRequest("New Name", "New Subject", "New Body", null);
        TemplateResponse response = service.update(orgId, userId, existing.getId(), request, "127.0.0.1");

        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getSubject()).isEqualTo("New Subject");
        verify(auditService).logDiff(eq(userId), any(), any(), any(), anyString(), eq("127.0.0.1"));
    }

    @Test
    void delete_removesTemplateAndLogs() {
        Template template = buildTemplate("To Delete", "Sub", "Body");
        when(templateRepository.findByIdAndOrganizationId(template.getId(), orgId))
                .thenReturn(Optional.of(template));

        service.delete(orgId, userId, template.getId(), "127.0.0.1");

        verify(templateRepository).delete(template);
        verify(auditService).log(eq(userId), any(), anyString(), eq("127.0.0.1"));
    }

    @Test
    void toggleLock_flipsLockState() {
        Template template = buildTemplate("Test", "Sub", "Body");
        template.setLocked(false);
        when(templateRepository.findByIdAndOrganizationId(template.getId(), orgId))
                .thenReturn(Optional.of(template));
        when(templateRepository.save(any(Template.class))).thenReturn(template);

        service.toggleLock(orgId, template.getId());

        assertThat(template.isLocked()).isTrue();
    }

    private Template buildTemplate(String name, String subject, String body) {
        return Template.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .organizationId(orgId)
                .name(name)
                .subject(subject)
                .body(body)
                .params("[]")
                .locked(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
