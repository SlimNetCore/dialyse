package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié des règles de TVA comptables (RG-CPT-006) : la règle applicable à une date, en lecture. La table des règles
 * est partagée avec la Facturation, dont le propriétaire est à trancher avec sa modélisation.
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
