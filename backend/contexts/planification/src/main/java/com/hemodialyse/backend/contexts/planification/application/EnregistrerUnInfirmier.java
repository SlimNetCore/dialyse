package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.MatriculeUniqueDansLeCentre;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;

/**
 * Enregistre un nouvel infirmier dans le référentiel du centre : fiche active, sans compte. Refus : matricule déjà pris dans le
 * centre {@code INFIRMIER_MATRICULE_EXISTANT}. Sous le verrou du référentiel (écriture concurrente : 409).
 */
public class EnregistrerUnInfirmier {
    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuReferentielDesInfirmiers verrou;
    private final InfirmiersRepository infirmiers;
    private final NouveauxIdentifiantsDInfirmier identifiants;
    private final FichesDetaillees fiches;

    public EnregistrerUnInfirmier(UniteDeTravail uniteDeTravail, VerrouDuReferentielDesInfirmiers verrou,
                                  InfirmiersRepository infirmiers, NouveauxIdentifiantsDInfirmier identifiants,
                                  FichesDetaillees fiches) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.infirmiers = infirmiers;
        this.identifiants = identifiants;
        this.fiches = fiches;
    }

    public FicheDetaillee executer(CentreId centre, Matricule matricule, IdentiteDeLInfirmier identite,
                                   Qualification qualification, boolean habiliteIsolement) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            MatriculeUniqueDansLeCentre.verifier(infirmiers, centre, matricule, null);
            Infirmier fiche = Infirmier.enregistrer(identifiants.suivant(), centre, matricule, identite, qualification,
                    habiliteIsolement);
            infirmiers.enregistrer(fiche);
            return fiches.de(fiche);
        });
    }
}
