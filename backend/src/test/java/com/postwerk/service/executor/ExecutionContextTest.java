package com.postwerk.service.executor;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextTest {

    @Test
    void organizationId_isNullByDefault_andSetByWithOrganizationId() {
        ExecutionContext ctx = new ExecutionContext(null, null, false);
        assertThat(ctx.getOrganizationId()).isNull();

        UUID orgId = UUID.randomUUID();
        assertThat(ctx.withOrganizationId(orgId).getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    void organizationId_survivesCopyOnWriteDerivations() {
        UUID orgId = UUID.randomUUID();
        ExecutionContext ctx = new ExecutionContext(null, null, false)
                .withOrganizationId(orgId)
                .withVariable("a", 1)
                .withVariables(Map.of("b", 2))
                .asDryRun();

        // #4: executors read getOrganizationId() to resolve org-scoped resources during a run.
        assertThat(ctx.getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    void minConfidence_isNullUntilAnAiDecisionRuns() {
        ExecutionContext ctx = new ExecutionContext(null, null, false);
        assertThat(ctx.getMinConfidence()).isNull();
    }

    @Test
    void withRecordedConfidence_keepsTheMinimum() {
        ExecutionContext ctx = new ExecutionContext(null, null, false)
                .withRecordedConfidence(90)
                .withRecordedConfidence(40)   // lower → becomes the min
                .withRecordedConfidence(70);  // higher → min stays 40

        assertThat(ctx.getMinConfidence()).isEqualTo(40.0);
    }

    @Test
    void branchesTrackTheirMinimumIndependently() {
        // Copy-on-write: each branch derives from the shared base without affecting siblings (#3b per-path).
        ExecutionContext base = new ExecutionContext(null, null, false).withRecordedConfidence(80);
        ExecutionContext branchA = base.withRecordedConfidence(30);
        ExecutionContext branchB = base.withRecordedConfidence(95);

        assertThat(branchA.getMinConfidence()).isEqualTo(30.0);
        assertThat(branchB.getMinConfidence()).isEqualTo(80.0); // unaffected by branchA's lower value
        assertThat(base.getMinConfidence()).isEqualTo(80.0);
    }
}
