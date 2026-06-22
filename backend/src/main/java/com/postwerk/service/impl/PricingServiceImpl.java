package com.postwerk.service.impl;

import com.postwerk.config.GeminiPricingProperties;
import com.postwerk.dto.admin.ModelPricingRequest;
import com.postwerk.dto.admin.ModelPricingResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.ModelPricing;
import com.postwerk.repository.ModelPricingRepository;
import com.postwerk.service.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link PricingService}. Reads admin-editable rates from {@code model_pricing} into an
 * in-memory cache (refreshed on every mutation), falling back to the static {@link GeminiPricingProperties}
 * (application.yml) for the usd-to-eur rate and for any model absent from the database.
 *
 * <p>The cache is per-instance and evicted on write; with the current single-instance deployment this
 * is always consistent (mirrors {@code PlanCacheService}). A multi-instance deployment would see a brief
 * staleness window until each instance reloads — acceptable for a rarely-changed pricing table.</p>
 *
 * @since 1.0
 */
@Service
public class PricingServiceImpl implements PricingService {

    private final ModelPricingRepository repository;
    private final GeminiPricingProperties yamlPricing;

    /** model → {inputPerMillion, outputPerMillion}. Lazily loaded; rebuilt on every mutation. */
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public PricingServiceImpl(ModelPricingRepository repository, GeminiPricingProperties yamlPricing) {
        this.repository = repository;
        this.yamlPricing = yamlPricing;
    }

    @Override
    @Transactional(readOnly = true)
    public int calculateCostMicros(String model, int promptTokens, int outputTokens) {
        ensureLoaded();
        double[] rates = cache.get(model);
        double inputRate = rates != null ? rates[0] : yamlPricing.getInputRate(model);
        double outputRate = rates != null ? rates[1] : yamlPricing.getOutputRate(model);

        double inputCostUsd = (promptTokens / 1_000_000.0) * inputRate;
        double outputCostUsd = (outputTokens / 1_000_000.0) * outputRate;
        double costEur = (inputCostUsd + outputCostUsd) * yamlPricing.getUsdToEur();
        return (int) Math.round(costEur * 1_000_000);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelPricingResponse> listModels() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(ModelPricing::getModel))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ModelPricingResponse createModel(ModelPricingRequest request) {
        if (repository.existsByModel(request.model())) {
            throw new IllegalArgumentException("Pricing for model '" + request.model() + "' already exists");
        }
        ModelPricing saved = repository.save(ModelPricing.builder()
                .model(request.model())
                .inputPerMillion(request.inputPerMillion())
                .outputPerMillion(request.outputPerMillion())
                .build());
        reload();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ModelPricingResponse updateModel(UUID id, ModelPricingRequest request) {
        ModelPricing pricing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ModelPricing", id.toString()));
        // Block renaming onto another row's model name (the column is UNIQUE).
        if (!pricing.getModel().equals(request.model()) && repository.existsByModel(request.model())) {
            throw new IllegalArgumentException("Pricing for model '" + request.model() + "' already exists");
        }
        pricing.setModel(request.model());
        pricing.setInputPerMillion(request.inputPerMillion());
        pricing.setOutputPerMillion(request.outputPerMillion());
        repository.save(pricing);
        reload();
        return toResponse(pricing);
    }

    @Override
    @Transactional
    public void deleteModel(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ModelPricing", id.toString());
        }
        repository.deleteById(id);
        reload();
    }

    private void ensureLoaded() {
        if (!loaded) reload();
    }

    private synchronized void reload() {
        Map<String, double[]> fresh = new ConcurrentHashMap<>();
        for (ModelPricing p : repository.findAll()) {
            fresh.put(p.getModel(), new double[]{p.getInputPerMillion(), p.getOutputPerMillion()});
        }
        cache.clear();
        cache.putAll(fresh);
        loaded = true;
    }

    private ModelPricingResponse toResponse(ModelPricing p) {
        return new ModelPricingResponse(p.getId(), p.getModel(),
                p.getInputPerMillion(), p.getOutputPerMillion(), p.getUpdatedAt());
    }
}
