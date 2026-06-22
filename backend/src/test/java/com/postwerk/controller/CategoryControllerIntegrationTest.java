package com.postwerk.controller;

import com.postwerk.BaseIntegrationTest;
import com.postwerk.dto.CategoryExportDto;
import com.postwerk.dto.CategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration coverage for {@link CategoryController}, which is served entirely by the generic
 * {@link OrgScopedCrudController} base via the {@code OrgContext} argument resolver (#4 DRY/SOLID
 * cleanup, B1+B2). Exercises every inherited endpoint — in particular the generic
 * {@code @RequestBody List<Exp>} import path that the frontend e2e suite cannot reach — to prove
 * the generic request-body type resolution and the explicit permission gates work end-to-end.
 */
class CategoryControllerIntegrationTest extends BaseIntegrationTest {

    private static final String DESC = "Emails about invoices, billing and payment reminders.";

    private CategoryRequest request(String name) {
        return new CategoryRequest(name, "#3b82f6", DESC, null, null);
    }

    @Test
    void fullCrudLifecycle_throughGenericBaseController() throws Exception {
        String token = registerAndGetToken("cat-crud@example.com");

        // create
        String body = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("Billing"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Billing"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        // list
        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // get
        mockMvc.perform(get("/api/v1/categories/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // update
        mockMvc.perform(put("/api/v1/categories/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("Billing & Invoices"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Billing & Invoices"));

        // toggle lock
        mockMvc.perform(patch("/api/v1/categories/" + id + "/lock")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // delete
        mockMvc.perform(delete("/api/v1/categories/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void exportAndImport_roundTrip_throughGenericListBody() throws Exception {
        String token = registerAndGetToken("cat-impexp@example.com");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("Support"))))
                .andExpect(status().isCreated());

        // export returns the generic List<Exp>
        mockMvc.perform(get("/api/v1/categories/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // import accepts a generic List<Exp> @RequestBody (the path e2e can't cover)
        List<CategoryExportDto> payload = List.of(
                new CategoryExportDto("Imported", "#19a563", "Imported category description.", null, null));
        mockMvc.perform(post("/api/v1/categories/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Imported")));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("NoAuth"))))
                .andExpect(status().isUnauthorized());
    }
}
