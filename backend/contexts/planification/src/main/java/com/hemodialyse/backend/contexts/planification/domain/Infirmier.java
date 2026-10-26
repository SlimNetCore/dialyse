package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

import java.util.Optional;

/**
 * Fiche d'un infirmier du centre (racine d'agrégat, RG-INF-001 à 012). Immuable : chaque intention rend la fiche suivante.
 * <p>
 * Invariants : matricule, nom et qualification obligatoires ; matricule unique dans le centre (vérifié par le cas d'usage sous le
 * verrou du référentiel) ; un compte lié une seule fois, dans le même état que la fiche (désactiver la fiche désactive le compte,
 * réactiver la réactive) ; aucune opération n'impose à un compte un état que la fiche n'a pas déjà (décision du 26/10/2026,
 * KB-PLN-22). Le compte est une référence par identité vers Identité et accès.
 * <p>
 * Les noms de la fiche et du compte ne sont pas synchronisés (KB-PLN-14 reproduit, « à décider »).
 */
public final class Infirmier {
    private final InfirmierId id;
    private final CentreId centre;
    private final Matricule matricule;
    private final IdentiteDeLInfirmier identite;
    private final Qualification qualification;
    private final boolean habiliteIsolement;
    private final boolean actif;
    private final CompteId compte;

    private Infirmier(InfirmierId id, CentreId centre, Matricule matricule, IdentiteDeLInfirmier identite,
                      Qualification qualification, boolean habiliteIsolement, boolean actif, CompteId compte) {
        this.id = requis(id);
        this.centre = requis(centre);
        this.matricule = requis(matricule);
        this.identite = requis(identite);
        this.qualification = requis(qualification);
        this.habiliteIsolement = habiliteIsolement;
        this.actif = actif;
        this.compte = compte;
    }

    private static <T> T requis(T valeur) {
        if (valeur == null) {
            throw new SaisieInvalide("Valeur requise");
        }
        return valeur;
    }

    /** Une nouvelle fiche : active, sans compte. */
    public static Infirmier enregistrer(InfirmierId id, CentreId centre, Matricule matricule, IdentiteDeLInfirmier identite,
                                        Qualification qualification, boolean habiliteIsolement) {
        return new Infirmier(id, centre, matricule, identite, qualification, habiliteIsolement, true, null);
    }

    /** Une fiche relue du référentiel (aucune règle rejouée sur son état). */
    public static Infirmier reconstituer(InfirmierId id, CentreId centre, Matricule matricule, IdentiteDeLInfirmier identite,
                                         Qualification qualification, boolean habiliteIsolement, boolean actif, CompteId compte) {
        return new Infirmier(id, centre, matricule, identite, qualification, habiliteIsolement, actif, compte);
    }

    /** Remplacement complet des données de la fiche ; l'état et le compte sont conservés (même fiche inactive). */
    public Infirmier modifier(Matricule nouveauMatricule, IdentiteDeLInfirmier nouvelleIdentite, Qualification nouvelleQualification,
                              boolean habilite) {
        return new Infirmier(id, centre, nouveauMatricule, nouvelleIdentite, nouvelleQualification, habilite, actif, compte);
    }

    /** Désactive la fiche (idempotent). Le compte lié, s'il existe, doit être désactivé par le cas d'usage. */
    public Infirmier desactiver() {
        return avecActif(false);
    }

    /**
     * Réactive la fiche. Une fiche déjà active dont le compte est désactivé est un état incohérent : refusé, on n'impose pas
     * l'état du compte (KB-PLN-22).
     */
    public Infirmier reactiver(EtatDuCompte etatDuCompte) {
        if (actif && compte != null && etatDuCompte == EtatDuCompte.INACTIF) {
            throw new RegleDePlanificationViolee("COMPTE_ETAT_INCOHERENT",
                    "La fiche est active mais son compte est désactivé : réactiver le compte depuis l'administration des utilisateurs");
        }
        return avecActif(true);
    }

    /** La fiche doit être sans compte pour en recevoir un. */
    public void exigerSansCompte() {
        if (compte != null) {
            throw new RegleDePlanificationViolee("COMPTE_DEJA_LIE", "Cette fiche est déjà reliée à un compte");
        }
    }

    /** La fiche doit être active pour recevoir un compte créé pour elle. */
    public void exigerActivePourUnNouveauCompte() {
        if (!actif) {
            throw new RegleDePlanificationViolee("COMPTE_ETAT_INCOHERENT", "Impossible de créer un compte pour une fiche inactive");
        }
    }

    /** Lie un compte existant : la fiche et le compte doivent être tous deux actifs ou tous deux inactifs. */
    public Infirmier lierCompte(CompteId nouveauCompte, boolean compteActif) {
        exigerSansCompte();
        if (compteActif != actif) {
            throw new RegleDePlanificationViolee("COMPTE_ETAT_INCOHERENT",
                    "Le compte et la fiche doivent être tous deux actifs ou tous deux inactifs");
        }
        return new Infirmier(id, centre, matricule, identite, qualification, habiliteIsolement, actif, nouveauCompte);
    }

    /** Lie le compte tout juste créé pour cette fiche (créé actif : la fiche doit l'être, sinon états incohérents). */
    public Infirmier lierLeCompteCree(CompteId nouveauCompte) {
        return lierCompte(nouveauCompte, true);
    }

    /**
     * Délie le compte (idempotent sans compte). Refusé pour une fiche inactive dont le compte reste actif : le compte pourrait
     * encore se connecter (KB-PLN-22).
     */
    public Infirmier delierCompte(EtatDuCompte etatDuCompte) {
        if (compte == null) {
            return this;
        }
        if (!actif && etatDuCompte == EtatDuCompte.ACTIF) {
            throw new RegleDePlanificationViolee("COMPTE_DELIAISON_INCOHERENTE",
                    "La fiche est inactive mais son compte est actif : désactiver le compte avant de le délier");
        }
        return sansCompte();
    }

    /** Délie le compte qui va être supprimé : refusé tant que la fiche est active (KB-PLN-16). */
    public Infirmier delierAvantLaSuppressionDuCompte() {
        if (compte == null) {
            return this;
        }
        if (actif) {
            throw new RegleDePlanificationViolee("COMPTE_LIE_A_UNE_FICHE_ACTIVE",
                    "Ce compte est lié à une fiche d'infirmier active : désactiver la fiche ou délier le compte avant de le supprimer");
        }
        return sansCompte();
    }

    /** La fiche suit l'état de son compte, modifié dans l'administration des utilisateurs (RG-INF-003). */
    public Infirmier suivreLeCompte(boolean compteActif) {
        return actif == compteActif ? this : avecActif(compteActif);
    }

    private Infirmier avecActif(boolean nouvelEtat) {
        return new Infirmier(id, centre, matricule, identite, qualification, habiliteIsolement, nouvelEtat, compte);
    }

    private Infirmier sansCompte() {
        return new Infirmier(id, centre, matricule, identite, qualification, habiliteIsolement, actif, null);
    }

    public InfirmierId id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public Matricule matricule() {
        return matricule;
    }

    public IdentiteDeLInfirmier identite() {
        return identite;
    }

    public Qualification qualification() {
        return qualification;
    }

    public boolean habiliteIsolement() {
        return habiliteIsolement;
    }

    public boolean actif() {
        return actif;
    }

    /** Le compte lié, s'il y en a un. */
    public Optional<CompteId> compte() {
        return Optional.ofNullable(compte);
    }

    /** « Prénom Nom », ou le nom seul sans prénom : nom complet du compte créé depuis la fiche. */
    public String nomComplet() {
        return identite.nomComplet();
    }
}
