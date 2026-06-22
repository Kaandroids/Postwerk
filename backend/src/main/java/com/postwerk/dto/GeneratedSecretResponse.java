package com.postwerk.dto;

/**
 * Response carrying a freshly generated signing secret, shown to the user exactly once.
 * The secret is stored encrypted and is never returned again afterwards.
 */
public record GeneratedSecretResponse(String secret) {}
