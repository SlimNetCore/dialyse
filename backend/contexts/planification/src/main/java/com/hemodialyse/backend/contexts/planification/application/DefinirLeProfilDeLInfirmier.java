package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuCentre;

/**
 * Définit le profil de planification d'un infirmier (RG-PLN-097). Refus : infirmier inconnu ou d'un autre centre
 * {@code PROFIL_INFIRMIER_INTROUVABLE}, infirmier inactif {@code PROFIL_INFIRMIER_INACTIF} (KB-PLN-07 corrigé). Sous le verrou
 * du centre.
 */
public class DefinirLeProfilDeLInfirmier {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuCentre verrou;
    private final ProfilsRepository profils;
    private final InfirmiersDuCentre infirmiers;

    public DefinirLeProfilDeLInfirmier(UniteDeTravail uniteDeTravail, VerrouDuCentre verrou, ProfilsRepository profils,
                                       InfirmiersDuCentre infirmiers) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.profils = profils;
        this.infirmiers = infirmiers;
    }

    public ProfilDePlanification executer(CentreId centre, ProfilDePlanification profil) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            switch (infirmiers.situation(centre, profil.infirmier())) {
                case INTROUVABLE -> throw new RegleDePlanificationViolee("PROFIL_INFIRMIER_INTROUVABLE", "Infirmier introuvable");
                case INACTIF -> throw new RegleDePlanificationViolee("PROFIL_INFIRMIER_INACTIF",
                        "Cet infirmier est inactif : son profil n'est plus modifiable");
                case ACTIF -> profils.enregistrer(centre, profil);
            }
            return profil;
        });
    }
}
