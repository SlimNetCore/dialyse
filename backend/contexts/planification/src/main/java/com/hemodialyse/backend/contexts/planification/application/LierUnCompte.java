package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;

/**
 * Lie un compte existant à une fiche. Refus, dans cet ordre : fiche inconnue {@code INFIRMIER_INTROUVABLE}, fiche déjà liée
 * {@code COMPTE_DEJA_LIE}, compte absent du centre ou sans le rôle infirmier {@code COMPTE_INTROUVABLE}, compte déjà utilisé par une
 * autre fiche du centre {@code COMPTE_DEJA_UTILISE}, compte déjà lié à une fiche d'un autre centre
 * {@code COMPTE_LIE_A_UN_AUTRE_CENTRE} (décision du 26/10/2026, KB-PLN-15 : plus de compte lié à deux centres), états différents
 * {@code COMPTE_ETAT_INCOHERENT}.
 */
public class LierUnCompte {
    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuReferentielDesInfirmiers verrou;
    private final InfirmiersRepository infirmiers;
    private final ComptesUtilisateurs comptes;
    private final FichesDetaillees fiches;

    public LierUnCompte(UniteDeTravail uniteDeTravail, VerrouDuReferentielDesInfirmiers verrou, InfirmiersRepository infirmiers,
                        ComptesUtilisateurs comptes, FichesDetaillees fiches) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.infirmiers = infirmiers;
        this.comptes = comptes;
        this.fiches = fiches;
    }

    public FicheDetaillee executer(CentreId centre, InfirmierId id, CompteId compteId) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            Infirmier fiche = Referentiel.charger(infirmiers, centre, id);
            fiche.exigerSansCompte();
            Compte compte = comptes.compteInfirmierDuCentre(centre, compteId).orElseThrow(() -> new RegleDePlanificationViolee(
                    "COMPTE_INTROUVABLE", "Ce compte n'existe pas dans le centre ou n'a pas le rôle infirmier"));
            if (infirmiers.parCompte(centre, compteId).isPresent()) {
                throw new RegleDePlanificationViolee("COMPTE_DEJA_UTILISE", "Ce compte est déjà relié à un autre infirmier");
            }
            if (infirmiers.fichesDuCompte(compteId).stream().anyMatch(autre -> !autre.centre().equals(centre))) {
                throw new RegleDePlanificationViolee("COMPTE_LIE_A_UN_AUTRE_CENTRE",
                        "Ce compte est déjà lié à une fiche d'un autre centre : un compte ne se lie qu'à un seul centre");
            }
            Infirmier liee = fiche.lierCompte(compteId, compte.actif());
            infirmiers.enregistrer(liee);
            return fiches.de(liee);
        });
    }
}
