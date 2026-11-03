package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeDeMouvement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Port sortant de lecture : le journal des mouvements enrichi du nom du patient et des libellés de la place. */
public interface LectureDesMouvements {

    /** Filtres cumulatifs ; tous facultatifs. {@code du} et {@code au} sont inclusifs. */
    record Filtre(UUID patient, TypeDeMouvement type, LocalDate du, LocalDate au) {
    }

    /** Ligne du journal ; les libellés de place sont nuls si la place a disparu, le nom est « prénom nom ». */
    record Ligne(UUID id, UUID patientId, String patientNom, String patientCode, TypeDeMouvement type, LocalDate dateEffet,
                 String etatPrecedent, String etatNouveau, String salle, String creneau, String generateur,
                 String joursDialyse, boolean automatique, Instant creeLe) {
    }

    record Page(List<Ligne> items, long total, int page, int size) {
    }

    /** Du plus récent au plus ancien : date d'effet, création puis identifiant. */
    Page lister(CentreId centre, Filtre filtre, int page, int size);
}
