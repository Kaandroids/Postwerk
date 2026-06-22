package com.postwerk.service;

import com.postwerk.dto.admin.ModelPricingRequest;
import com.postwerk.dto.admin.ModelPricingResponse;

import java.util.List;
import java.util.UUID;

/**
 * Computes the cost of AI calls from admin-editable per-model rates (DB-backed), falling back to the
 * static {@code application.yml} rates for any model not present in the database. Replaces direct use
 * of {@link com.postwerk.config.GeminiPricingProperties#calculateCostMicros} on the recording path so
 * pricing can be changed at runtime without a restart.
 *
 * @since 1.0
 */
public interface PricingService {

    /**
     * Cost of a call in micros (1 EUR = 1,000,000 micros). Model rates are USD per million tokens;
     * the USD subtotal is converted to EUR (usd-to-eur) before scaling. Unknown models fall back to
     * the YAML rates, which default to 0 → cost 0.
     */
    int calculateCostMicros(String model, int promptTokens, int outputTokens);

    /** All editable model rates, ordered by model name. */
    List<ModelPricingResponse> listModels();

    /** Create a new model rate. Fails if the model name already exists. */
    ModelPricingResponse createModel(ModelPricingRequest request);

    /** Update an existing model rate by id. */
    ModelPricingResponse updateModel(UUID id, ModelPricingRequest request);

    /** Delete a model rate by id (the model then falls back to its YAML rate, if any). */
    void deleteModel(UUID id);
}
