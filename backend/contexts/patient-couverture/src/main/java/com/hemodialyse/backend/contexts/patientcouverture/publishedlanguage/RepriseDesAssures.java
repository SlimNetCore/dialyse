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

    record AssureConnu(String numeroAssurance, UUID centre) {
    }

    /** Valeurs du fichier de reprise ; une valeur nulle n'est pas renseignée. */
    record ReportDeLAssure(String numeroAssurance, String nom, String prenom, String sexe, LocalDate dateNaissance,
                           String telPersonnel, String telMobile, String telBureau, String adresse, String groupeSanguin) {
    }
}
