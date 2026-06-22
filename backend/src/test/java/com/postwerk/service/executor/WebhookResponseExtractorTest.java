package com.postwerk.service.executor;

import com.postwerk.model.ParameterSet;
import com.postwerk.repository.ParameterSetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebhookResponseExtractor} — the ParameterSet-driven response extraction
 * (Model B): only declared fields are exposed, nested trees become dotted keys, status conditions
 * select the matching ParameterSet, and a missing/no-match/non-JSON case extracts nothing.
 */
@ExtendWith(MockitoExtension.class)
class WebhookResponseExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    @Mock private ParameterSetRepository parameterSetRepository;

    private WebhookResponseExtractor extractor() {
        return new WebhookResponseExtractor(mapper, parameterSetRepository);
    }

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    private void stubParamSet(UUID id, UUID org, String paramsJson) {
        ParameterSet ps = ParameterSet.builder().id(id).organizationId(org).parameters(paramsJson).build();
        when(parameterSetRepository.findByIdAndOrganizationId(id, org)).thenReturn(Optional.of(ps));
    }

    @Test
    void noSchemas_extractsNothing() throws Exception {
        Map<String, Object> out = extractor().extract(json("[]"), 200, "{\"a\":1}", UUID.randomUUID());
        assertThat(out).isEmpty();
    }

    @Test
    void declaredNestedFields_extractedAsDottedKeys_undeclaredIgnored() throws Exception {
        UUID org = UUID.randomUUID();
        UUID psId = UUID.randomUUID();
        stubParamSet(psId, org, """
                [
                  {"name":"status","type":"TEXT","children":[]},
                  {"name":"order","type":"OBJECT","children":[
                     {"name":"total","type":"NUMBER","children":[]},
                     {"name":"customer","type":"OBJECT","children":[
                        {"name":"email","type":"EMAIL","children":[]}
                     ]}
                  ]}
                ]
                """);
        JsonNode schemas = json("[{\"name\":\"ok\",\"condition\":\"2xx\",\"parameterSetId\":\"" + psId + "\"}]");
        String body = "{\"status\":\"ok\",\"order\":{\"total\":149.9,\"customer\":{\"email\":\"a@x.com\"}},\"ignored\":\"x\"}";

        Map<String, Object> out = extractor().extract(schemas, 200, body, org);

        assertThat(out).containsEntry("status", "ok");
        assertThat(out).containsEntry("order.total", "149.9");
        assertThat(out).containsEntry("order.customer.email", "a@x.com");
        assertThat(out).doesNotContainKey("ignored"); // only declared fields are exposed
    }

    @Test
    void listField_iteratesElementsAndAddsLength() throws Exception {
        UUID org = UUID.randomUUID();
        UUID psId = UUID.randomUUID();
        stubParamSet(psId, org, """
                [{"name":"items","type":"OBJECT","isList":true,"children":[
                    {"name":"sku","type":"TEXT","children":[]}
                ]}]
                """);
        JsonNode schemas = json("[{\"name\":\"ok\",\"condition\":\"2xx\",\"parameterSetId\":\"" + psId + "\"}]");
        String body = "{\"items\":[{\"sku\":\"A\"},{\"sku\":\"B\"}]}";

        Map<String, Object> out = extractor().extract(schemas, 200, body, org);

        assertThat(out).containsEntry("items[0].sku", "A");
        assertThat(out).containsEntry("items[1].sku", "B");
        assertThat(out).containsEntry("items.length", 2);
    }

    @Test
    void statusCondition_selectsMatchingSchema() throws Exception {
        UUID org = UUID.randomUUID();
        UUID okId = UUID.randomUUID();
        UUID errId = UUID.randomUUID();
        stubParamSet(errId, org, "[{\"name\":\"message\",\"type\":\"TEXT\",\"children\":[]}]");
        JsonNode schemas = json("["
                + "{\"name\":\"ok\",\"condition\":\"2xx\",\"parameterSetId\":\"" + okId + "\"},"
                + "{\"name\":\"err\",\"condition\":\"4xx\",\"parameterSetId\":\"" + errId + "\"}]");

        Map<String, Object> out = extractor().extract(schemas, 404, "{\"message\":\"not found\"}", org);

        assertThat(out).containsEntry("message", "not found"); // 4xx schema selected, not the 2xx one
    }

    @Test
    void exactStatusCondition_winsOverClassRegardlessOfOrder() throws Exception {
        UUID org = UUID.randomUUID();
        UUID classId = UUID.randomUUID(); // generic 2xx
        UUID exactId = UUID.randomUUID();  // specific 210
        stubParamSet(exactId, org, "[{\"name\":\"special\",\"type\":\"TEXT\",\"children\":[]}]");
        // 2xx is listed FIRST, but the exact 210 must still win for status 210.
        JsonNode schemas = json("["
                + "{\"name\":\"ok\",\"condition\":\"2xx\",\"parameterSetId\":\"" + classId + "\"},"
                + "{\"name\":\"created\",\"condition\":\"210\",\"parameterSetId\":\"" + exactId + "\"}]");

        Map<String, Object> out = extractor().extract(schemas, 210, "{\"special\":\"yes\"}", org);

        assertThat(out).containsEntry("special", "yes"); // exact 210 schema wins over the generic 2xx
    }

    @Test
    void perPositionWildcard_matchesAndOutranksBroaderClass() throws Exception {
        UUID org = UUID.randomUUID();
        UUID classId = UUID.randomUUID(); // 2xx (1 fixed digit)
        UUID tensId = UUID.randomUUID();   // 21x (2 fixed digits)
        stubParamSet(tensId, org, "[{\"name\":\"info\",\"type\":\"TEXT\",\"children\":[]}]");
        JsonNode schemas = json("["
                + "{\"name\":\"ok\",\"condition\":\"2xx\",\"parameterSetId\":\"" + classId + "\"},"
                + "{\"name\":\"info\",\"condition\":\"21x\",\"parameterSetId\":\"" + tensId + "\"}]");

        Map<String, Object> out = extractor().extract(schemas, 213, "{\"info\":\"v\"}", org);

        assertThat(out).containsEntry("info", "v"); // 21x (210–219) matches 213 and beats 2xx
    }

    @Test
    void nonJsonBody_extractsNothing() throws Exception {
        JsonNode schemas = json("[{\"name\":\"ok\",\"condition\":\"2xx\",\"parameterSetId\":\"" + UUID.randomUUID() + "\"}]");
        Map<String, Object> out = extractor().extract(schemas, 200, "plain text", UUID.randomUUID());
        assertThat(out).isEmpty();
    }
}
