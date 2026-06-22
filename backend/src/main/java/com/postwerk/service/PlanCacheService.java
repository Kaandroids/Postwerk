package com.postwerk.service;

import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Organization;
import com.postwerk.model.Plan;
import com.postwerk.model.User;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.PlanRepository;
import com.postwerk.repository.UserRepository;
import org.hibernate.Hibernate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caches plan lookups to avoid repeated DB hits on every quota check.
 *
 * <p>Plans are billed per-organization (#4): {@link #loadPlanForOrg(UUID)} resolves the active
 * organization's plan (falling back to the default plan). The legacy per-user lookup is retained
 * for identity-level callers. Caches are evicted when plan assignment or plan details change.</p>
 *
 * @since 1.0
 */
@Service
public class PlanCacheService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final OrganizationRepository organizationRepository;

    public PlanCacheService(UserRepository userRepository, PlanRepository planRepository,
                            OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.organizationRepository = organizationRepository;
    }

    @Cacheable(value = "userPlans", key = "#userId")
    @Transactional(readOnly = true)
    public Plan loadPlanForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return resolveOrDefault(user.getPlan());
    }

    /**
     * Resolves the billing plan for an organization (#4). Falls back to the default plan when the
     * org id is null (unresolvable billing context), the org is gone, or it has no plan assigned.
     */
    @Cacheable(value = "orgPlans", key = "#organizationId")
    @Transactional(readOnly = true)
    public Plan loadPlanForOrg(UUID organizationId) {
        if (organizationId == null) {
            return defaultPlan();
        }
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        if (org == null) {
            return defaultPlan();
        }
        return resolveOrDefault(org.getPlan());
    }

    private Plan resolveOrDefault(Plan plan) {
        if (plan == null) {
            return defaultPlan();
        }
        return (Plan) Hibernate.unproxy(plan);
    }

    private Plan defaultPlan() {
        return planRepository.findByName(Plan.DEFAULT_PLAN_NAME)
                .orElseThrow(() -> new IllegalStateException(Plan.DEFAULT_PLAN_NAME + " plan not found"));
    }

    @CacheEvict(value = "userPlans", key = "#userId")
    public void evictUserPlan(UUID userId) {
        // eviction only
    }

    @CacheEvict(value = "orgPlans", key = "#organizationId")
    public void evictOrgPlan(UUID organizationId) {
        // eviction only
    }

    @CacheEvict(value = {"userPlans", "orgPlans"}, allEntries = true)
    public void evictAllUserPlans() {
        // eviction only — used when a plan's properties are updated
    }
}
