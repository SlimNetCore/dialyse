package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;

import java.util.List;

/**
 * Prépare la suppression d'un compte : refusée ({@code COMPTE_LIE_A_UNE_FICHE_ACTIVE}) si une fiche active, de n'importe quel
 * centre, y est liée ; sinon les fiches inactives sont déliées. Joue dans la transaction de la suppression : si celle-ci échoue,
 * les fiches restent liées (KB-PLN-16).
 */
public class DelierAvantLaSuppressionDuCompte {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;

    public DelierAvantLaSuppressionDuCompte(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
    }

    public void executer(CompteId compte) {
        uniteDeTravail.executer(() -> {
            List<Infirmier> deliees = infirmiers.fichesDuCompte(compte).stream()
                    .map(Infirmier::delierAvantLaSuppressionDuCompte).toList();
            deliees.forEach(infirmiers::enregistrer);
            return null;
        });
    }
}
