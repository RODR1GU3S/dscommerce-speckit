package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.Field;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.Representation;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.repositories.CategoryRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Protected reference diagnosis only: no request/entity mutation, DML or maintenance operation. */
@Service
@Transactional(readOnly = true)
public class ProductCategoryDiagnosticService {
    private final AdminAccessService access;
    private final CategoryRepository categories;
    @PersistenceContext private EntityManager entityManager;

    public ProductCategoryDiagnosticService(AdminAccessService access, CategoryRepository categories) {
        this.access = access;
        this.categories = categories;
    }

    /** Metadata only, invoked by authorized, revalidated creation before saving; never by isValid or after failure. */
    public Set<String> categoryForeignKeysForCreation() {
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            var metadata = connection.getMetaData();
            if (!"H2".equals(metadata.getDatabaseProductName())) return Set.of();
            var schema = connection.getSchema();
            var rowsByName = new LinkedHashMap<String, List<Boolean>>();
            try (var keys = metadata.getImportedKeys(connection.getCatalog(), schema, "TB_PRODUCT_CATEGORY")) {
                while (keys.next()) {
                    var name = keys.getString("FK_NAME");
                    boolean categoryReference = "TB_PRODUCT_CATEGORY".equals(keys.getString("FKTABLE_NAME"))
                            && schema.equals(keys.getString("FKTABLE_SCHEM"))
                            && "CATEGORY_ID".equals(keys.getString("FKCOLUMN_NAME"))
                            && "TB_CATEGORY".equals(keys.getString("PKTABLE_NAME"))
                            && schema.equals(keys.getString("PKTABLE_SCHEM"))
                            && "ID".equals(keys.getString("PKCOLUMN_NAME"))
                            && keys.getShort("KEY_SEQ") == 1;
                    rowsByName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(categoryReference);
                }
            }
            // Composite/unnamed/unrecognized keys are deliberately unclassified.
            return rowsByName.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank()
                            && entry.getValue().equals(List.of(true)))
                    .map(java.util.Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
        });
    }

    /** Include the local/structural diagnoses without letting them suppress legible reference lookups. */
    public ProductInputDiagnostic diagnose(ProductWriteRequestDTO input, ProductInputDiagnostic diagnostic,
                                           AuthenticatedAccountPrincipal principal) {
        access.requireAdmin(principal);
        Objects.requireNonNull(input, "Product input is required");
        Objects.requireNonNull(diagnostic, "Internal input diagnostic is required");
        var errors = new ArrayList<>(diagnostic.errors());
        var state = diagnostic.fields().getOrDefault(Field.CATEGORY_IDS, Representation.UNEXAMINED);
        if (state == Representation.ABSENT || state == Representation.NULL) {
            addIfMissing(errors, "categoryIds", "REQUIRED", "Required product field");
        } else if (state == Representation.READABLE || state == Representation.UNEXAMINED) {
            diagnoseSelection(input, diagnostic, errors);
        }
        // Invalid/duplicated whole fields are already diagnosed and supply no trustworthy references.
        return new ProductInputDiagnostic(diagnostic.fields(), diagnostic.categoryElements(), errors);
    }

    private void diagnoseSelection(ProductWriteRequestDTO input, ProductInputDiagnostic diagnostic,
                                   List<AdminFieldErrorDTO> errors) {
        if (input.categoryIds() == null) {
            addIfMissing(errors, "categoryIds", "REQUIRED", "Required product field");
            return;
        }
        if (input.categoryIds().isEmpty()) {
            addIfMissing(errors, "categoryIds", "EMPTY_CATEGORIES", "Select at least one category");
            return;
        }
        var legible = new LinkedHashMap<Integer, Long>();
        for (int index = 0; index < input.categoryIds().size(); index++) {
            var state = diagnostic.categoryElements().getOrDefault(index, Representation.UNEXAMINED);
            if (state != Representation.READABLE && state != Representation.UNEXAMINED) continue;
            String field = "categoryIds[" + index + "]";
            Long id = input.categoryIds().get(index);
            if (id == null) {
                addIfMissing(errors, field, "INVALID_TYPE", "Expected an integer category identifier");
            } else if (id <= 0) {
                addIfMissing(errors, field, "NOT_POSITIVE", "Category identifier must be positive");
            } else legible.put(index, id);
        }
        if (legible.isEmpty()) return;
        var ids = new LinkedHashSet<>(legible.values());
        var existing = categories.findAllByIdIn(ids).stream().map(Category::getId).collect(Collectors.toSet());
        legible.forEach((index, id) -> {
            if (!existing.contains(id)) {
                addIfMissing(errors, "categoryIds[" + index + "]", "CATEGORY_NOT_FOUND", "Selected category does not exist");
            }
        });
    }

    /** Avoid reproducing a diagnosis already supplied for this field/code; overall presentation belongs to the advice. */
    private static void addIfMissing(List<AdminFieldErrorDTO> errors, String field, String code, String message) {
        if (errors.stream().noneMatch(error -> error.field().equals(field) && error.code().equals(code))) {
            errors.add(new AdminFieldErrorDTO(field, code, message));
        }
    }
}
