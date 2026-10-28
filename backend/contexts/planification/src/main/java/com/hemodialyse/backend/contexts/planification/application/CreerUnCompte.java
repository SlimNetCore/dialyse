package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs.Compte;
import com.hemodialyse.backend.contexts.planification.domain.AdresseEmail;
import com.hemodialyse.backend.contexts.planification.domain.IdentifiantDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;

/**
 * Crée un compte pour une fiche active sans compte et le lie, dans une seule transaction (fiche et compte atomiques, KB-PLN-23).
 * Le mot de passe temporaire n'est rendu qu'ici : il n'est jamais relisible. Refus, dans cet ordre : fiche inconnue, fiche déjà
 * liée, fiche inactive {@code COMPTE_ETAT_INCOHERENT}, identifiant invalide {@code COMPTE_IDENTIFIANT_INVALIDE}, identifiant déjà
 * pris (tous centres, sans la casse) {@code COMPTE_IDENTIFIANT_EXISTANT}.
 */
public class CreerUnCompte {

    /** La fiche reliée au compte tout juste créé, et son mot de passe temporaire (communiqué une seule fois). */
    public record CompteCree(FicheDetaillee fiche, String motDePasseTemporaire) {
    }

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuReferentielDesInfirmiers verrou;
    private final InfirmiersRepository infirmiers;
    private final ComptesUtilisateurs comptes;
    private final GenerateurDeMotDePasse motsDePasse;
    private final FichesDetaillees fiches;

    @SuppressWarnings("java:S107")
    public CreerUnCompte(UniteDeTravail uniteDeTravail, VerrouDuReferentielDesInfirmiers verrou, InfirmiersRepository infirmiers,
                         ComptesUtilisateurs comptes, GenerateurDeMotDePasse motsDePasse, FichesDetaillees fiches) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.infirmiers = infirmiers;
        this.comptes = comptes;
        this.motsDePasse = motsDePasse;
        this.fiches = fiches;
    }

    /**
     * @param identifiantSaisi l'identifiant tel que saisi (jugé après la fiche)
     * @param email            l'adresse, déjà vérifiée, ou {@code null}
     */
    public CompteCree executer(CentreId centre, InfirmierId id, String identifiantSaisi, AdresseEmail email) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            Infirmier fiche = Referentiel.charger(infirmiers, centre, id);
            fiche.exigerSansCompte();
            fiche.exigerActivePourUnNouveauCompte();
            IdentifiantDeConnexion identifiant = IdentifiantDeConnexion.depuisLaSaisie(identifiantSaisi);
            if (comptes.identifiantExiste(identifiant)) {
                throw new RegleDePlanificationViolee("COMPTE_IDENTIFIANT_EXISTANT", "Cet identifiant de connexion existe déjà");
            }
            String motDePasse = motsDePasse.generer();
            Compte compte = comptes.creerCompteInfirmier(centre, identifiant, motDePasse, fiche.nomComplet(), email);
            Infirmier liee = fiche.lierLeCompteCree(compte.id());
            infirmiers.enregistrer(liee);
            return new CompteCree(fiches.de(liee), motDePasse);
        });
    }
}
