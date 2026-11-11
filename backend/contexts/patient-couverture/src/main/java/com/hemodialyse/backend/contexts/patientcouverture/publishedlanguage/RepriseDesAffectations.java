package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : la reprise de l'historique des affectations assuré - patient d'un centre (outil de
 * migration historique, ADR 0134, S6b-3). L'appelant contrôle tout le fichier (patient repris, assuré du centre, dates, une seule
 * principale par patient) AVANT d'écrire.
 */
public interface RepriseDesAffectations {

    /** Historique d'affectations du patient dans le centre (pour rapprocher une ligne du fichier d'une ligne existante). */
    List<AffectationConnue> historique(UUID centre, UUID patient);

    /**
     * Écrit une ligne d'affectation et renvoie son identifiant. Une ligne principale devient l'unique principale du patient (les autres
     * perdent ce caractère) et l'assuré principal de sa fiche ; elle le reste quand on rejoue le même fichier (KB-S6a-1).
     *
     * @param affectation ligne à remplacer ; si elle n'existe pas dans ce centre (ou si elle est nulle), une ligne est ajoutée
     */
    UUID reprendre(UUID centre, UUID patient, UUID affectation, ReportDeLAffectation report);

    record AffectationConnue(UUID id, String numeroAssurance, LocalDate dateDebut) {
    }

    /** Valeurs du fichier de reprise ; les dates sont facultatives. */
    record ReportDeLAffectation(String numeroAssurance, boolean principale, LocalDate dateDebut, LocalDate dateFin) {
    }
}
