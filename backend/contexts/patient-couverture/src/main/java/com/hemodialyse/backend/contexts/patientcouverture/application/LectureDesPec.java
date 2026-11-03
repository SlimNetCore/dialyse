package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Port sortant de lecture : la recherche paginée des PEC du centre, enrichie de l'identité du patient. */
public interface LectureDesPec {

    /** Ligne de la recherche ; noms de champs du JSON actuel (snake_case). */
    record Ligne(UUID id, UUID patient_id, LocalDate date_debut_demande, LocalDate date_fin_demande, String statut,
                 LocalDate date_debut_effectif, LocalDate date_fin_effectif, String code_patient, String nom, String prenom,
                 String numero_assurance) {
    }

    record Page(List<Ligne> items, long total, int page, int size) {
    }

    /** Tri : fin de la demande décroissante. */
    Page rechercher(CentreId centre, CriteresDePec criteres);
}
