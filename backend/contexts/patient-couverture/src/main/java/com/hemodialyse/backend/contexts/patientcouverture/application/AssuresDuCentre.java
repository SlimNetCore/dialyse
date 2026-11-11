package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant des assurés et de leurs affectations aux patients (tables {@code assure} et {@code assure_patient}). Tant que
 * la slice PC2 n'est pas reprise, l'adapter est une ACL sur les tables actuelles ; les règles ci-dessous sont celles de la route
 * actuelle.
 */
public interface AssuresDuCentre {

    /** Assuré tel que rendu par l'API (ordre et noms des champs du JSON actuel). */
    record Assure(String numeroAssurance, String nom, String prenom, String sexe, LocalDate dateNaissance, String telPersonnel,
                  String telMobile, String telBureau, String adresse, String groupeSanguin, UUID centre) {
    }

    /** Affectation d'un assuré à un patient (historique : la plus récente d'abord). */
    record Affectation(UUID id, UUID patientId, String numeroAssurance, UUID centre, boolean principal,
                       OffsetDateTime dateAffectation, LocalDate dateDebut, LocalDate dateFin) {
        public boolean actif() {
            return dateFin == null;
        }
    }

    /** Recherche par numéro, nom ou prénom (contient, sans casse), triée par nom puis prénom ; sans texte, tous. */
    List<Assure> rechercher(CentreId centre, String texte);

    Optional<Assure> trouver(String numeroAssurance);

    void mettreAJour(Assure assure);

    List<Affectation> historique(CentreId centre, PatientId patient);

    Optional<Affectation> trouverAffectation(UUID id);

    /** Ferme l'affectation principale active (fin à la date donnée). */
    void fermerLAffectationPrincipale(CentreId centre, PatientId patient, LocalDate fin);

    UUID ajouterUneAffectationPrincipale(CentreId centre, PatientId patient, String numeroAssurance, LocalDate debut);

    void modifierLesDates(UUID affectation, LocalDate debut, LocalDate fin);

    // ---- assuré principal de la fiche (PC2) : ce que l'enregistrement de la fiche d'un patient écrit

    /** Assuré principal actuel du patient (la ligne {@code is_primary} la plus récente), s'il y en a un. */
    Optional<Affectation> principalActuel(CentreId centre, PatientId patient);

    /** Retire le caractère principal de toutes les lignes du patient (aucune date n'est modifiée, comme aujourd'hui). */
    void retirerLePrincipal(CentreId centre, PatientId patient);

    /**
     * Crée l'assuré s'il est nouveau, sinon remplace ses coordonnées (jamais son centre : le contrôle d'appartenance est fait
     * avant par le cas d'usage) ; une date de naissance absente ne modifie pas celle de l'assuré existant.
     */
    void declarer(Assure assure);

    /** Ajoute une ligne d'affectation principale SANS dates (fiche) ; renvoie son identifiant. */
    UUID lierSansDates(CentreId centre, PatientId patient, String numeroAssurance);

    // ---- reprise des affectations (outil de migration historique, ADR 0134 S6b-3)

    /** Le même assuré, numéro comparé sans casse (un numéro exact est préféré s'il y en a plusieurs). */
    Optional<Assure> trouverSansCasse(String numeroAssurance);

    /** Ajoute une ligne d'affectation (principale ou non, dates facultatives) ; renvoie son identifiant. */
    UUID ajouterUneAffectation(CentreId centre, PatientId patient, String numeroAssurance, boolean principale, LocalDate debut,
                               LocalDate fin);

    /** Remplace le numéro, le caractère principal et les dates d'une ligne du centre ; sa date d'affectation n'est posée que si elle manque. */
    void remplacerUneAffectation(CentreId centre, UUID affectation, String numeroAssurance, boolean principale, LocalDate debut,
                                 LocalDate fin);
}
