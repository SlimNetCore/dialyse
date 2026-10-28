package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.EtatDuCompte;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;

/**
 * Réactive la fiche d'un infirmier du centre ; son compte lié, désactivé avec elle, est réactivé dans la même transaction. Une
 * fiche déjà active dont le compte est désactivé est refusée ({@code COMPTE_ETAT_INCOHERENT}) : l'état d'un compte n'est jamais
 * imposé par une fiche qui n'a pas changé (KB-PLN-22).
 */
public class ReactiverUnInfirmier {
    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuReferentielDesInfirmiers verrou;
    private final InfirmiersRepository infirmiers;
    private final ComptesUtilisateurs comptes;
    private final FichesDetaillees fiches;

    public ReactiverUnInfirmier(UniteDeTravail uniteDeTravail, VerrouDuReferentielDesInfirmiers verrou,
                                InfirmiersRepository infirmiers, ComptesUtilisateurs comptes, FichesDetaillees fiches) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.infirmiers = infirmiers;
        this.comptes = comptes;
        this.fiches = fiches;
    }

    public FicheDetaillee executer(CentreId centre, InfirmierId id) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            Infirmier fiche = Referentiel.charger(infirmiers, centre, id);
            boolean etaitInactive = !fiche.actif();
            EtatDuCompte etat = Referentiel.etatDuCompte(comptes, fiche);
            Infirmier reactivee = fiche.reactiver(etat);
            infirmiers.enregistrer(reactivee);
            if (etaitInactive && etat == EtatDuCompte.INACTIF) {
                reactivee.compte().ifPresent(compte -> comptes.definirActif(compte, true));
            }
            return fiches.de(reactivee);
        });
    }
}
