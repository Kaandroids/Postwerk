package com.postwerk.dto;

/** Result of an AI email classification with confidence score and reasoning. */
public record ClassificationResult(String categoryId, int confidence, String reason) {}
