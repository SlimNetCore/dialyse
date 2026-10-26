package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;

/**
 * Délie le compte d'une fiche (idempotent sans compte) ; le compte est conservé et redevient liable. Refusé pour une fiche
 * inactive dont le compte est actif {@code COMPTE_DELIAISON_INCOHERENTE} (KB-PLN-22).
 */
public class DelierLeCompte {
    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuReferentielDesInfirmiers verrou;
    private final InfirmiersRepository infirmiers;
    private final ComptesUtilisateurs comptes;
    private final FichesDetaillees fiches;

    public DelierLeCompte(UniteDeTravail uniteDeTravail, VerrouDuReferentielDesInfirmiers verrou, InfirmiersRepository infirmiers,
                          ComptesUtilisateurs comptes, FichesDetaillees fiches) {
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
            Infirmier deliee = fiche.delierCompte(Referentiel.etatDuCompte(comptes, fiche));
            infirmiers.enregistrer(deliee);
            return fiches.de(deliee);
        });
    }
}
