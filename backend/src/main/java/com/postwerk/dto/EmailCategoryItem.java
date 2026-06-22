package com.postwerk.dto;

import java.util.UUID;

/** Lightweight category reference used in email responses. */
public record EmailCategoryItem(UUID id, String name, String color) {}
