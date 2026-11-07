package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.RechercheDeSeances;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Port sortant (modèle de lecture) : l'historique des séances du centre, filtré, trié et paginé, avec l'identité du patient de chaque
 * ligne (jointure avec la fiche du patient : une séance dont le patient a disparu est absente). Une ligne de statut illisible est ignorée
 * et journalisée (KB-SEA-07, corrigé : une seule ligne illisible ne rend plus toute la liste impossible).
 */
public interface HistoriqueDesSeances {

    PagedResult<LigneDeSeance> rechercher(CentreId centre, RechercheDeSeances recherche, Pagination pagination);

    record LigneDeSeance(SeanceId id, CentreId centre, PatientId patient, String codePatient, String nomDuPatient,
                         String prenomDuPatient, LocalDate jour, StatutDeSeance statut, OffsetDateTime creeLe,
                         OffsetDateTime valideeLe, OffsetDateTime signeeParLInfirmierLe, OffsetDateTime signeeParLeMedecinLe,
                         OffsetDateTime deverrouilleeLe, boolean horsPlanning, MotifHorsPlanning motifHorsPlanning) {
    }
}
