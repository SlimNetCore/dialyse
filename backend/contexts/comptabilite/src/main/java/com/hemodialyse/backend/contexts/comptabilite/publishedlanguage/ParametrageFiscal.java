package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié des règles de TVA (RG-FAC-002, RG-CPT-006) : la règle applicable à une date, en lecture. La Comptabilité
 * est propriétaire de la table des règles ({@code tva_types}, ADR 0030) ; la Facturation, les Absences et la simulation
 * lisent le taux par ce contrat.
 */
public interface ParametrageFiscal {

    /**
     * La règle de TVA active du type de prestation à cette date : la dernière commencée, début et fin inclus ; vide si aucune
     * règle ne s'applique. Le type se compare tel que saisi.
     */
    Optional<RegleTvaApplicable> regleApplicable(UUID centre, String typePrestation, LocalDate date);

    /**
     * @param finValidite nulle si la règle est ouverte
     */
    record RegleTvaApplicable(String typePrestation, BigDecimal taux, boolean exonere, LocalDate debutValidite,
                              LocalDate finValidite, String texteReference) {
    }
}
