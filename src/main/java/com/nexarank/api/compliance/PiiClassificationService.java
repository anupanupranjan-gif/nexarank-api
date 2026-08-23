// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * NR-155: reports which entity fields are tagged {@link Pii} and why. Scans
 * the model package at call time via classpath scanning rather than a
 * maintained list, so a new @Pii field shows up automatically — the same
 * reason field-level diffing and audit classification elsewhere in this
 * codebase favor structural derivation over a hand-kept registry that drifts.
 */
@Service
public class PiiClassificationService {

    private static final String MODEL_PACKAGE = "com.nexarank.api.model";

    public List<ClassifiedField> classify() {
        List<ClassifiedField> results = new ArrayList<>();

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        scanner.findCandidateComponents(MODEL_PACKAGE).forEach(candidate -> {
            try {
                Class<?> entityClass = Class.forName(candidate.getBeanClassName());
                String table = tableName(entityClass);

                for (Field field : entityClass.getDeclaredFields()) {
                    Pii pii = field.getAnnotation(Pii.class);
                    if (pii == null) continue;

                    Column column = field.getAnnotation(Column.class);
                    String columnName = (column != null && !column.name().isBlank())
                            ? column.name() : field.getName();

                    results.add(new ClassifiedField(
                            entityClass.getSimpleName(), table, field.getName(),
                            columnName, pii.value(), pii.note()));
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Scanned class not loadable: " + candidate.getBeanClassName(), e);
            }
        });

        results.sort(Comparator.comparing(ClassifiedField::entity).thenComparing(ClassifiedField::field));
        return results;
    }

    private String tableName(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) return table.name();
        return entityClass.getSimpleName();
    }

    public record ClassifiedField(String entity, String table, String field,
                                   String column, PiiCategory category, String note) {
    }
}
