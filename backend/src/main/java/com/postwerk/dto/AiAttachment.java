package com.postwerk.dto;

/**
 * An email attachment to be sent to the LLM as an inline multimodal part.
 *
 * @param filename original attachment file name (for logging/diagnostics; not sent to the model)
 * @param mimeType content type used for the inline part (e.g. {@code application/pdf}, {@code image/png})
 * @param data     the raw attachment bytes
 *
 * @since 1.0
 */
public record AiAttachment(String filename, String mimeType, byte[] data) {}
