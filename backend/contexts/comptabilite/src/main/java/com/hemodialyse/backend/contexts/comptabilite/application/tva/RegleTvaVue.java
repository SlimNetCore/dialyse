package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une règle de TVA telle que la liste la montre : ni identifiant, ni état actif, ni texte de référence (comme le legacy).
 */
public record RegleTvaVue(String typePrestation, BigDecimal tauxApplique, boolean exonere, LocalDate dateDebutValidite,
                          LocalDate dateFinValidite) {

    static RegleTvaVue depuis(RegleTva regle) {
        return new RegleTvaVue(regle.typePrestation(), regle.taux().valeur(), regle.exonere(), regle.debutValidite(),
                regle.finValidite());
    }
}
