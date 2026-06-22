package com.postwerk.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface projection for the inbox-list read path.
 *
 * <p>Carries only the scalar columns {@code EmailListResponse} needs, deliberately omitting the
 * heavy {@code body_text}/{@code body_html} TEXT columns so the list queries never materialize email
 * bodies. The single-email detail path ({@code toFullResponse}/{@code getById}) keeps loading full
 * {@link com.postwerk.model.Email} entities.</p>
 *
 * @since 1.0
 */
public interface EmailListView {

    UUID getId();

    String getMessageId();

    String getFolder();

    String getFromAddress();

    String getFromPersonal();

    String getToAddresses();

    String getCcAddresses();

    String getSubject();

    String getSnippet();

    Instant getReceivedAt();

    boolean getIsRead();

    boolean getIsStarred();

    boolean getHasAttachments();

    String getAttachments();

    Long getSizeBytes();

    String getCategories();

    String getApprovalStatus();

    boolean getProcessed();
}
