package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.MatriculeUniqueDansLeCentre;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;

/**
 * Remplace les données d'une fiche (même inactive) ; son état et son compte sont conservés, le compte n'est pas renommé
 * (KB-PLN-14). Refus : fiche inconnue ou d'un autre centre {@code INFIRMIER_INTROUVABLE}, matricule déjà pris par une autre fiche
 * {@code INFIRMIER_MATRICULE_EXISTANT}.
 */
public class ModifierUnInfirmier {
    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuReferentielDesInfirmiers verrou;
    private final InfirmiersRepository infirmiers;
    private final FichesDetaillees fiches;

    public ModifierUnInfirmier(UniteDeTravail uniteDeTravail, VerrouDuReferentielDesInfirmiers verrou, InfirmiersRepository infirmiers,
                               FichesDetaillees fiches) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.infirmiers = infirmiers;
        this.fiches = fiches;
    }

    public FicheDetaillee executer(CentreId centre, InfirmierId id, Matricule matricule, IdentiteDeLInfirmier identite,
                                   Qualification qualification, boolean habiliteIsolement) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            Infirmier modifiee = Referentiel.charger(infirmiers, centre, id).modifier(matricule, identite, qualification,
                    habiliteIsolement);
            MatriculeUniqueDansLeCentre.verifier(infirmiers, centre, matricule, id);
            infirmiers.enregistrer(modifiee);
            return fiches.de(modifiee);
        });
    }
}
