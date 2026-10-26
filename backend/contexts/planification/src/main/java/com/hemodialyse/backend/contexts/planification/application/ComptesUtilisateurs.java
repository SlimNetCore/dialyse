package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.AdresseEmail;
import com.hemodialyse.backend.contexts.planification.domain.IdentifiantDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Port sortant : les comptes utilisateurs, qui appartiennent à Identité et accès (contexte générique, encore dans le legacy).
 * Implémenté par une ACL sur les tables {@code app_user*} ; participe à la transaction du cas d'usage.
 */
public interface ComptesUtilisateurs {

    /** Un compte utilisateur tel que la fiche le voit. */
    record Compte(CompteId id, String identifiant, String nomComplet, boolean actif) {
    }

    /**
     * Le compte, s'il est rattaché à ce centre et porte le rôle INFIRMIER. Le compte est verrouillé avant la lecture de son état
     * (ordre global des verrous : centre, fiches, comptes ; ADR 0056).
     */
    Optional<Compte> compteInfirmierDuCentre(CentreId centre, CompteId compte);

    /** Les comptes existants parmi ces identifiants (un compte supprimé est simplement absent). */
    List<Compte> parIds(Collection<CompteId> comptes);

    /** Les comptes INFIRMIER du centre qu'aucune fiche du centre n'utilise encore, par identifiant de connexion. */
    PageDeResultats<Compte> comptesLiables(CentreId centre, Pagination pagination);

    /** L'identifiant de connexion existe déjà (tous centres, sans tenir compte de la casse). */
    boolean identifiantExiste(IdentifiantDeConnexion identifiant);

    /**
     * Crée un compte actif, rôle INFIRMIER, rattaché au centre, qui devra changer son mot de passe temporaire à la première
     * connexion. Le mot de passe n'est conservé que haché.
     */
    Compte creerCompteInfirmier(CentreId centre, IdentifiantDeConnexion identifiant, String motDePasseTemporaire,
                                String nomComplet, AdresseEmail email);

    /**
     * Verrou qui sérialise la création des identifiants de connexion (aucune unicité en base PostgreSQL, KB-PLN-24), pour la
     * transaction courante. Dernier verrou de l'ordre global (ADR 0056).
     */
    void verrouillerLesIdentifiants();

    /** Active ou désactive le compte (sans effet si le compte n'existe plus). */
    void definirActif(CompteId compte, boolean actif);
}
