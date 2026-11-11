package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : la reprise des assurés d'un centre (outil de migration historique, ADR 0134). Le
 * centre est celui que l'appelant a déjà contrôlé ; les refus d'appartenance (numéro d'un autre centre) sont décidés par l'appelant
 * à partir de {@link #trouver}.
 */
public interface RepriseDesAssures {

    /** Assuré connu de la plateforme, quel que soit son centre (le numéro d'assurance est unique sur toute la plateforme). */
    Optional<AssureConnu> trouver(String numeroAssurance);

    /**
     * Crée l'assuré ou le met à jour dans le centre. Une reprise n'efface jamais une donnée : seules les valeurs renseignées
     * (non nulles) remplacent celles de l'assuré existant ; le nom est toujours remplacé.
     */
    void reprendre(UUID centre, ReportDeLAssure report);

    /**
     * Le même assuré, numéro comparé sans casse (KB-S6a-5) ; {@link AssureConnu#numeroAssurance()} est le numéro canonique, celui
     * qu'il faut écrire.
     */
    Optional<AssureConnu> trouverSansCasse(String numeroAssurance);

    /**
     * État actuel de l'assuré, à garder avant de le rapprocher pour pouvoir le restaurer (KB-S6a-2). Opaque pour l'appelant : seul
     * {@link #restaurer} le comprend. Vide si l'assuré n'existe pas.
     */
    Optional<String> memoriser(String numeroAssurance);

    /** Remet l'assuré du centre dans l'état mémorisé (toutes ses valeurs, y compris celles qui étaient vides). Sans effet s'il n'est pas du centre. */
    void restaurer(UUID centre, String numeroAssurance, String memento);

    record AssureConnu(String numeroAssurance, UUID centre) {
    }

    /** Valeurs du fichier de reprise ; une valeur nulle n'est pas renseignée. */
    record ReportDeLAssure(String numeroAssurance, String nom, String prenom, String sexe, LocalDate dateNaissance,
                           String telPersonnel, String telMobile, String telBureau, String adresse, String groupeSanguin) {
    }
}
