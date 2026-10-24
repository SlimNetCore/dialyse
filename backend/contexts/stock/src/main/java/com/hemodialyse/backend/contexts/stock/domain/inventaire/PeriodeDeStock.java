package com.hemodialyse.backend.contexts.stock.domain.inventaire;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Période de stock d'un centre : porte les deux règles « centre entier » — un seul inventaire en cours, et rien avant la dernière
 * clôture. Déduite des inventaires (en cours, dernière clôture) ; la sérialisation des ouvertures et des mouvements est assurée
 * par l'adapter (ligne de verrou par centre). Valeur immuable.
 *
 * @param derniereCloture {@code null} s'il n'y a jamais eu de clôture
 * @param enCours         référence de l'inventaire en cours, {@code null} s'il n'y en a pas
 */
public record PeriodeDeStock(CentreId centre, LocalDate derniereCloture, String enCours) {
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public PeriodeDeStock {
        Objects.requireNonNull(centre, "Le centre est obligatoire");
    }

    public boolean mouvementsBloques() {
        return enCours != null;
    }

    /**
     * Contrôles d'ouverture, dans l'ordre du legacy : une sortie pour séances non rapprochée (T43, ADR 0045), un inventaire déjà en
     * cours, une date qui ne suit pas la dernière clôture, des mouvements datés après la date, un recalcul du PMP en cours.
     *
     * @param sortieNonRapprochee référence de la première sortie pour séances non rapprochée
     * @param mouvementsApres     des mouvements du centre sont datés après la fin du jour d'inventaire
     * @param recalculEnCours     un recalcul du PMP verrouille des articles du centre
     * @throws RefusDuReferentiel {@code INVENTORY_SORTIE_POUR_SEANCES_NON_RAPPROCHEE}, {@code INVENTORY_ALREADY_IN_PROGRESS},
     *                            {@code INVENTORY_DATE_BEFORE_LAST}, {@code INVENTORY_MOVEMENTS_AFTER_DATE},
     *                            {@code INVENTORY_RECALC_RUNNING}
     */
    public void exigerOuvrable(LocalDate date, Optional<String> sortieNonRapprochee, boolean mouvementsApres, boolean recalculEnCours) {
        sortieNonRapprochee.ifPresent(reference -> {
            throw new RefusDuReferentiel("INVENTORY_SORTIE_POUR_SEANCES_NON_RAPPROCHEE", "La sortie pour séances " + reference
                    + " n'est pas rapprochée : rapprochez-la avant d'ouvrir un inventaire.");
        });
        if (enCours != null) {
            throw new RefusDuReferentiel("INVENTORY_ALREADY_IN_PROGRESS",
                    "L'inventaire " + enCours + " est déjà en cours : clôturez-le ou annulez-le d'abord.");
        }
        if (derniereCloture != null && !date.isAfter(derniereCloture)) {
            throw new RefusDuReferentiel("INVENTORY_DATE_BEFORE_LAST",
                    "La date doit être postérieure au dernier inventaire clôturé (" + derniereCloture.format(FR) + ").");
        }
        if (mouvementsApres) {
            throw new RefusDuReferentiel("INVENTORY_MOVEMENTS_AFTER_DATE", "Des mouvements de stock sont datés après le "
                    + date.format(FR) + " : choisissez une date d'inventaire postérieure ou égale au dernier mouvement.");
        }
        if (recalculEnCours) {
            throw new RefusDuReferentiel("INVENTORY_RECALC_RUNNING",
                    "Un recalcul de stock est en cours : attendez sa fin avant d'ouvrir l'inventaire.");
        }
    }
}

