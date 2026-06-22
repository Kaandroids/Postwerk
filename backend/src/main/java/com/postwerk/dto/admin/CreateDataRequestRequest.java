package com.postwerk.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Staff logging a DSAR received by email/post/phone. {@code type}/{@code channel} are the enum names.
 *
 * @since 1.0
 */
public record CreateDataRequestRequest(
        @NotBlank @Email @Size(max = 320) String subjectEmail,
        @NotBlank @Size(max = 200) String subjectName,
        @NotNull String type,
        @NotNull String channel,
        @Size(max = 4000) String note
) {}
