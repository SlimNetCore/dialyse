package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port publié (lecture) : <b>la programmation des patients d'un centre</b> (patientcouverture.v1, LEC-PAT sous-slice i, ADR 0176) :
 * pour chaque patient, ses jours de dialyse, son état, sa date d'admission et son sommeil, avec le sexe et la date de naissance de la
 * fiche. Lue par la Séance (tableau de bord mensuel, planning d'un scan). Toutes les lectures sont BORNÉES au centre demandé : un
 * patient d'un autre centre est absent. Aucune règle nouvelle : les lectures que la Séance faisait en SQL direct sur {@code patients},
 * à l'identique.
 */
public interface ProgrammationDesPatients {

    /** Tous les patients du centre, endormis et sortis compris, sans ordre garanti. */
    List<ProgrammationDuPatient> duCentre(UUID centre);

    /** La programmation du patient du centre ; vide si le patient est inconnu ou d'un autre centre. */
    Optional<ProgrammationDuPatient> duPatient(UUID centre, UUID patient);

    /**
     * Valeurs telles que saisies sur la fiche : {@code sexe} et {@code etat} peuvent être nuls ou libres, les dates absentes sont
     * nulles, un jour ou un sommeil non renseigné vaut faux.
     */
    record ProgrammationDuPatient(UUID patient, String sexe, LocalDate dateNaissance, LocalDate dateAdmission, boolean enSommeil,
                                  String etat, LocalDate dateEvenement, Set<DayOfWeek> joursDeDialyse) {
        public ProgrammationDuPatient {
            joursDeDialyse = Set.copyOf(joursDeDialyse);
        }
    }
}
