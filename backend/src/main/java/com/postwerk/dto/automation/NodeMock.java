package com.postwerk.dto.automation;

import java.util.Map;

/**
 * Per-node mock configuration used while running a test case. Keyed by node id in the
 * test case's {@code mocks} map.
 *
 * <ul>
 *   <li><b>MOCK</b> — synthesize the node's result instead of performing the real call.
 *       {@code response} supplies the data the node would normally produce (interpreted
 *       per node type: webhook → parsed fields, integration → output map, categorize →
 *       category fields, extract → extraction groups). {@code forceError} routes the
 *       node down its failure handle so error branches can be exercised.</li>
 *   <li><b>LIVE</b> — perform the real call even during a dry-run (e.g. actually hit the
 *       webhook URL), bypassing the dry-run simulate short-circuit.</li>
 * </ul>
 *
 * <p>Absent / {@code null} mode = legacy default dry-run simulation.</p>
 */
public record NodeMock(
        String mode,
        Integer statusCode,
        Boolean forceError,
        Map<String, Object> response
) {
    public static final String MODE_MOCK = "MOCK";
    public static final String MODE_LIVE = "LIVE";

    public boolean isMock() {
        return MODE_MOCK.equalsIgnoreCase(mode);
    }

    public boolean isLive() {
        return MODE_LIVE.equalsIgnoreCase(mode);
    }

    public boolean shouldForceError() {
        return Boolean.TRUE.equals(forceError);
    }
}
