package com.postwerk.dto;

import java.util.UUID;

/** Candidate category submitted to the AI classifier for email classification. */
public record CategoryCandidate(UUID id, String name, String description,
                                 String positiveExample, String negativeExample) {}
