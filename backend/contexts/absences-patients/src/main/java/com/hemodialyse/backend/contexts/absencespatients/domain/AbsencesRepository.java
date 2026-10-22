package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port sortant du domaine : les absences enregistrées (table {@code absence_patient}), une racine d'agrégat. Les
 * écritures se font dans la transaction du cas d'usage, sur une absence verrouillée.
 */
public interface AbsencesRepository {

    /**
     * Relit l'absence en la verrouillant jusqu'à la fin de la transaction : une autre écriture sur la même absence attend.
     * Vide si elle n'existe pas ou appartient à un autre centre.
     *
     * @throws EcritureConcurrente le verrou n'a pas pu être pris (délai dépassé)
     */
    Optional<AbsencePatient> verrouiller(CentreId centre, AbsenceId id);

    /**
     * Relit l'absence en verrouillant TOUTES les absences de son patient, toujours dans le même ordre (rattrapage : une séance
     * ne rattrape qu'une absence, et deux rattrapages du même patient s'attendent sans interblocage). Rend aussi les dates
     * de séance déjà utilisées par les absences rattrapées du patient ; une absence annulée ne réserve rien. Vide si l'absence
     * n'existe pas ou appartient à un autre centre.
     *
     * @throws EcritureConcurrente un verrou n'a pas pu être pris (délai dépassé)
     */
    Optional<AbsenceEtRattrapages> verrouillerPourRattrapage(CentreId centre, AbsenceId id);

    /**
     * @param datesDeRattrapageUtilisees dates de rattrapage des absences rattrapées du patient (celle de l'absence comprise)
     */
    record AbsenceEtRattrapages(AbsencePatient absence, List<LocalDate> datesDeRattrapageUtilisees) {
    }

    /**
     * Une absence existe pour ce patient ce jour-là, quel que soit son statut (même annulée, RG-ABS-001).
     */
    boolean existe(CentreId centre, PatientId patient, LocalDate jour);

    /**
     * @throws EcritureConcurrente une autre déclaration vient de prendre ce jour pour ce patient
     */
    void creer(AbsencePatient absence);

    /**
     * @throws EcritureConcurrente la date de rattrapage est prise par une autre absence du patient (contrainte d'unicité)
     */
    void enregistrer(AbsencePatient absence);

    /**
     * Supprime physiquement l'absence ; sans effet si elle n'existe plus. Seule la réinclusion d'une séance exclue le fait
     * (ADR 0031).
     */
    void supprimer(CentreId centre, AbsenceId id);
}
