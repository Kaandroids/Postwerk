package com.postwerk.service.impl;

import com.postwerk.config.GeminiPricingProperties;
import com.postwerk.dto.admin.ModelPricingRequest;
import com.postwerk.dto.admin.ModelPricingResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.ModelPricing;
import com.postwerk.repository.ModelPricingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceImplTest {

    @Mock private ModelPricingRepository repository;

    private GeminiPricingProperties yaml;
    private PricingServiceImpl service;

    @BeforeEach
    void setUp() {
        // Real YAML properties so usd-to-eur (0.92) + fallback rates behave exactly as in production.
        yaml = new GeminiPricingProperties();
        GeminiPricingProperties.ModelPricing flash = new GeminiPricingProperties.ModelPricing();
        flash.setInputPerMillion(0.15);
        flash.setOutputPerMillion(0.60);
        yaml.getModels().put("gemini-2.5-flash", flash);

        service = new PricingServiceImpl(repository, yaml);
    }

    private static ModelPricing row(String model, double in, double out) {
        return ModelPricing.builder().id(UUID.randomUUID()).model(model)
                .inputPerMillion(in).outputPerMillion(out).build();
    }

    @Test
    void calculateCostMicros_usesDbRate_andConvertsUsdToEur() {
        when(repository.findAll()).thenReturn(List.of(row("gemini-2.5-pro", 1.25, 10.00)));

        // input 100k*1.25/M=0.125, output 50k*10/M=0.50 → 0.625 USD * 0.92 = 0.575 EUR → 575000 micros
        int cost = service.calculateCostMicros("gemini-2.5-pro", 100_000, 50_000);

        assertThat(cost).isEqualTo(575_000);
    }

    @Test
    void calculateCostMicros_unknownInDb_fallsBackToYamlRate() {
        when(repository.findAll()).thenReturn(List.of()); // nothing in DB → yaml fallback

        // flash 0.15/0.60: 0.015+0.03=0.045 USD * 0.92 = 0.0414 EUR → 41400 micros
        int cost = service.calculateCostMicros("gemini-2.5-flash", 100_000, 50_000);

        assertThat(cost).isEqualTo(41_400);
    }

    @Test
    void calculateCostMicros_unknownEverywhere_isZero() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.calculateCostMicros("mystery-model", 100_000, 50_000)).isZero();
    }

    @Test
    void listModels_sortedByModelName() {
        when(repository.findAll()).thenReturn(List.of(
                row("z-model", 1, 1), row("a-model", 1, 1), row("m-model", 1, 1)));

        List<ModelPricingResponse> result = service.listModels();

        assertThat(result).extracting(ModelPricingResponse::model)
                .containsExactly("a-model", "m-model", "z-model");
    }

    @Test
    void createModel_duplicateName_throws() {
        when(repository.existsByModel("gemini-2.5-pro")).thenReturn(true);

        assertThatThrownBy(() -> service.createModel(new ModelPricingRequest("gemini-2.5-pro", 1.25, 10.0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createModel_new_savesAndReturns() {
        when(repository.existsByModel("gemini-2.5-pro")).thenReturn(false);
        when(repository.save(any(ModelPricing.class))).thenAnswer(inv -> inv.getArgument(0));

        ModelPricingResponse response = service.createModel(new ModelPricingRequest("gemini-2.5-pro", 1.25, 10.0));

        assertThat(response.model()).isEqualTo("gemini-2.5-pro");
        assertThat(response.inputPerMillion()).isEqualTo(1.25);
        assertThat(response.outputPerMillion()).isEqualTo(10.0);
        verify(repository).save(any(ModelPricing.class));
    }

    @Test
    void updateModel_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateModel(id, new ModelPricingRequest("x", 1.0, 1.0)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateModel_renameOntoExistingModel_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(row("old-name", 1, 1)));
        when(repository.existsByModel("taken-name")).thenReturn(true);

        assertThatThrownBy(() -> service.updateModel(id, new ModelPricingRequest("taken-name", 2.0, 3.0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteModel_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteModel(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }
}
