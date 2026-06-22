package com.postwerk.util;

import com.postwerk.dto.ImportResultDto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared utility for import operations across CRUD services.
 * Eliminates duplicated importAll loops in Category, Filter, Template, ParameterSet, and Automation services.
 */
public final class ImportHelper {

    private ImportHelper() {}

    /**
     * Runs an import operation over a list of items, collecting results.
     *
     * @param items         the items to import
     * @param nameExtractor extracts a human-readable name from each item for error messages
     * @param importer      the import action for each item (typically calls service.create())
     * @return an ImportResultDto with counts and error details
     */
    public static <T> ImportResultDto runImport(List<T> items,
                                                 Function<T, String> nameExtractor,
                                                 Consumer<T> importer) {
        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            try {
                importer.accept(item);
                imported++;
            } catch (Exception e) {
                failed++;
                errors.add("Item %d (%s): %s".formatted(i + 1, nameExtractor.apply(item), e.getMessage()));
            }
        }
        return new ImportResultDto(imported, failed, errors);
    }
}
