package com.postwerk.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiPricingPropertiesTest {

    private GeminiPricingProperties pricing;

    @BeforeEach
    void setUp() {
        pricing = new GeminiPricingProperties();

        GeminiPricingProperties.ModelPricing flash = new GeminiPricingProperties.ModelPricing();
        flash.setInputPerMillion(0.15);
        flash.setOutputPerMillion(0.60);

        GeminiPricingProperties.ModelPricing pro = new GeminiPricingProperties.ModelPricing();
        pro.setInputPerMillion(1.25);
        pro.setOutputPerMillion(10.00);

        pricing.getModels().put("gemini-2.5-flash", flash);
        pricing.getModels().put("gemini-2.5-pro", pro);
    }

    @Test
    void getInputRate_knownModel_returnsRate() {
        assertThat(pricing.getInputRate("gemini-2.5-flash")).isEqualTo(0.15);
        assertThat(pricing.getInputRate("gemini-2.5-pro")).isEqualTo(1.25);
    }

    @Test
    void getOutputRate_knownModel_returnsRate() {
        assertThat(pricing.getOutputRate("gemini-2.5-flash")).isEqualTo(0.60);
        assertThat(pricing.getOutputRate("gemini-2.5-pro")).isEqualTo(10.00);
    }

    @Test
    void getInputRate_unknownModel_returnsZero() {
        assertThat(pricing.getInputRate("unknown-model")).isEqualTo(0.0);
    }

    @Test
    void getOutputRate_unknownModel_returnsZero() {
        assertThat(pricing.getOutputRate("unknown-model")).isEqualTo(0.0);
    }

    @Test
    void calculateCostMicros_flashModel_correctCost() {
        // 100K input + 50K output with Flash pricing
        // input: (100000 / 1M) * 0.15 = 0.015
        // output: (50000 / 1M) * 0.60 = 0.03
        // subtotal: 0.045 USD → * 0.92 usdToEur = 0.0414 EUR → 41400 micros
        int cost = pricing.calculateCostMicros("gemini-2.5-flash", 100_000, 50_000);
        assertThat(cost).isEqualTo(41_400);
    }

    @Test
    void calculateCostMicros_proModel_correctCost() {
        // 100K input + 50K output with Pro pricing
        // input: (100000 / 1M) * 1.25 = 0.125
        // output: (50000 / 1M) * 10.00 = 0.50
        // subtotal: 0.625 USD → * 0.92 usdToEur = 0.575 EUR → 575000 micros
        int cost = pricing.calculateCostMicros("gemini-2.5-pro", 100_000, 50_000);
        assertThat(cost).isEqualTo(575_000);
    }

    @Test
    void calculateCostMicros_unknownModel_returnsZero() {
        int cost = pricing.calculateCostMicros("unknown", 100_000, 50_000);
        assertThat(cost).isEqualTo(0);
    }

    @Test
    void calculateCostMicros_zeroTokens_returnsZero() {
        int cost = pricing.calculateCostMicros("gemini-2.5-flash", 0, 0);
        assertThat(cost).isEqualTo(0);
    }

    @Test
    void usdToEur_defaultValue() {
        assertThat(pricing.getUsdToEur()).isEqualTo(0.92);
    }
}
