package com.postwerk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Gemini AI model pricing used to calculate per-call cost in micros.
 *
 * @since 1.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gemini.pricing")
public class GeminiPricingProperties {

    private double usdToEur = 0.92;
    private Map<String, ModelPricing> models = new HashMap<>();

    @Data
    public static class ModelPricing {
        private double inputPerMillion;
        private double outputPerMillion;
    }

    public double getInputRate(String model) {
        ModelPricing pricing = models.get(model);
        return pricing != null ? pricing.getInputPerMillion() : 0.0;
    }

    public double getOutputRate(String model) {
        ModelPricing pricing = models.get(model);
        return pricing != null ? pricing.getOutputPerMillion() : 0.0;
    }

    /**
     * Computes the cost of a call in micros (1 EUR = 1,000,000 micros). Model rates are quoted in
     * USD per million tokens, so the USD subtotal is converted to EUR via {@link #usdToEur} before
     * scaling to micros — this keeps recorded cost in the same currency as the plan's EUR cent caps.
     */
    public int calculateCostMicros(String model, int promptTokens, int outputTokens) {
        double inputCostUsd = (promptTokens / 1_000_000.0) * getInputRate(model);
        double outputCostUsd = (outputTokens / 1_000_000.0) * getOutputRate(model);
        double costEur = (inputCostUsd + outputCostUsd) * usdToEur;
        return (int) Math.round(costEur * 1_000_000);
    }
}
