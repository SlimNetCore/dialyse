package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;
import com.hemodialyse.backend.contexts.planification.domain.EtatDuCompte;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;

import java.util.List;

/**
 * Gestes communs aux cas d'usage du référentiel des infirmiers (visibilité du paquet).
 */
final class Referentiel {
    private Referentiel() {
    }

    /** La fiche du centre, verrouillée pour l'écriture ; une fiche inconnue ou d'un autre centre est introuvable. */
    static Infirmier charger(InfirmiersRepository infirmiers, CentreId centre, InfirmierId id) {
        return infirmiers.pourEcriture(centre, id)
                .orElseThrow(() -> new RegleDePlanificationViolee("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
    }

    /** L'état du compte lié à la fiche ({@link EtatDuCompte#ABSENT} sans compte ou si le compte a disparu). */
    static EtatDuCompte etatDuCompte(ComptesUtilisateurs comptes, Infirmier fiche) {
        return fiche.compte().flatMap(compte -> comptes.parIds(List.of(compte)).stream().findFirst())
                .map(Compte::actif).map(EtatDuCompte::de).orElse(EtatDuCompte.ABSENT);
    }
}
