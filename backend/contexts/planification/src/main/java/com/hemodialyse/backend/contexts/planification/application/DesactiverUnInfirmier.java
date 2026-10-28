package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;

/**
 * Désactive la fiche d'un infirmier du centre (idempotent) ; son compte lié est désactivé dans la même transaction (RG-INF-003).
 * Le compte, partagé entre centres, est le seul effet hors de la fiche ; les fiches des autres centres ne sont pas touchées
 * (décision du 26/10/2026, KB-PLN-15).
 */
public class DesactiverUnInfirmier {
    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuReferentielDesInfirmiers verrou;
    private final InfirmiersRepository infirmiers;
    private final ComptesUtilisateurs comptes;
    private final FichesDetaillees fiches;

    public DesactiverUnInfirmier(UniteDeTravail uniteDeTravail, VerrouDuReferentielDesInfirmiers verrou,
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
            Infirmier desactivee = Referentiel.charger(infirmiers, centre, id).desactiver();
            infirmiers.enregistrer(desactivee);
            desactivee.compte().ifPresent(compte -> comptes.definirActif(compte, false));
            return fiches.de(desactivee);
        });
    }
}
