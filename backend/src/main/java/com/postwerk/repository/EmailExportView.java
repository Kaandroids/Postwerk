package com.postwerk.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface projection for the GDPR data export read path.
 *
 * <p>Carries only the metadata columns the export includes (matching
 * {@code UserExportResponse.EmailExport}), deliberately omitting the heavy
 * {@code body_text}/{@code body_html} TEXT columns so exporting a large mailbox does not load every
 * email body into memory. Combined with paging, this bounds the export's memory footprint.</p>
 *
 * @since 1.0
 */
public interface EmailExportView {

    UUID getId();

    UUID getEmailAccountId();

    String getFolder();

    String getFromAddress();

    String getToAddresses();

    String getSubject();

    String getSnippet();

    Instant getReceivedAt();

    boolean getIsRead();

    boolean getIsStarred();
}
