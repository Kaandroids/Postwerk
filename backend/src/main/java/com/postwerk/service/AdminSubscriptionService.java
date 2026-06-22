package com.postwerk.service;

import com.postwerk.dto.admin.AdminSubscriptionDetailResponse;
import com.postwerk.dto.admin.AdminSubscriptionResponse;
import com.postwerk.dto.admin.PlanHistoryEntryResponse;
import com.postwerk.dto.admin.SubscriptionKpisResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff Plans &amp; Subscriptions: every organization's plan assignment + derived MRR + metered
 * AI usage, with plan-change (and links to the existing suspend/activate + grant-credit features).
 * Reads need {@code PLAN_VIEW}/{@code BILLING_VIEW}; plan changes need {@code PLAN_MANAGE} (enforced
 * at the controller).
 *
 * @since 1.0
 */
public interface AdminSubscriptionService {

    /**
     * Paginated + filterable subscriptions list (filter/sort/paginate in-memory over all orgs — the
     * usage filter is derived from the effective AI cap, which has no single column to query on).
     *
     * @param search matches org name / slug / owner name+email / plan (blank = all)
     * @param plan   plan name | null
     * @param status "active" | "suspended" | null
     * @param usage  "over90" | "unlimited" | "aiOff" | null
     */
    Page<AdminSubscriptionResponse> listSubscriptions(String search, String plan, String status,
                                                      String usage, Pageable pageable);

    SubscriptionKpisResponse kpis();

    AdminSubscriptionDetailResponse getSubscription(UUID orgId);

    AdminSubscriptionDetailResponse changePlan(UUID orgId, UUID planId, String reason, UUID actorUserId, String ip);

    List<PlanHistoryEntryResponse> planHistory(UUID orgId);
}
