package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Objects;
import java.util.UUID;

/**
 * Emplacement de stockage d'un centre (référentiel simple, RG-STK-003) : libellé obligatoire ; valeurs nettoyées (KB-STK-09
 * corrigé). Un emplacement créé est actif.
 */
public record Emplacement(UUID id, CentreId centre, String code, String libelle, boolean actif) {

    public Emplacement {
        Objects.requireNonNull(id, "L'identifiant est obligatoire");
        Objects.requireNonNull(centre, "Le centre est obligatoire");
    }

    public static Emplacement creer(UUID id, CentreId centre, String code, String libelle) {
        if (libelle == null || libelle.isBlank()) {
            throw new RefusDuReferentiel("EMPLACEMENT_LIBELLE_REQUIS", "Le libelle de l'emplacement est obligatoire");
        }
        return new Emplacement(id, centre, code == null || code.isBlank() ? null : code.trim(), libelle.trim(), true);
    }
}

