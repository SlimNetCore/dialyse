package com.hemodialyse.backend.infrastructure.web.dto.referential;

import com.hemodialyse.backend.domain.referential.admin.model.ReferentialField;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;

import java.util.List;

/**
 * Description d'un référentiel administrable : le frontend en déduit formulaire, colonnes et aide à l'import.
 */
public record ReferentialKindResponse(String slug, String label, int importOrder, List<String> naturalKey,
                                      List<FieldResponse> fields) {

    public static ReferentialKindResponse from(ReferentialKind kind) {
        return new ReferentialKindResponse(kind.slug(), kind.label(), kind.importOrder(), kind.naturalKey(),
                kind.fields().stream().map(FieldResponse::from).toList());
    }

    public record FieldResponse(String key, String label, String type, boolean required, boolean requiredColumn,
                                int maxLength, List<String> allowedValues, String defaultValue, String reference,
                                String example) {

        static FieldResponse from(ReferentialField f) {
            return new FieldResponse(f.key(), f.label(), f.type().name(), f.required(), f.requiredColumn(),
                    f.maxLength(), f.allowedValues(), f.defaultValue(), f.referenceSlug(), f.example());
        }
    }
}

