package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : les écritures comptables d'un centre. Toute lecture est bornée au centre ; une écriture illisible fait échouer la lecture.
 */
public interface EcritureComptableRepository {

    Optional<EcritureComptable> parId(CentreId centre, UUID id);

    /**
     * L'écriture produite par cette opération (idempotence), quel que soit son journal ; la plus ancienne s'il y en a plusieurs.
     */
    Optional<EcritureComptable> parSource(CentreId centre, UUID source);

    /**
     * Liste paginée par date d'écriture, avec filtres facultatifs journal/statut.
     */
    PageEcritures rechercher(CentreId centre, LocalDate du, LocalDate au, CodeJournal journal, StatutEcriture statut,
                             int page, int taille);

    /**
     * Crée l'écriture ou remplace celle qui porte le même identifiant (champs et lignes) ; la date et l'auteur de son export sont conservés.
     */
    void enregistrer(EcritureComptable ecriture);

    /**
     * Toutes les écritures du journal dont la date d'écriture est dans [du, au], exportées comprises, par date puis numéro.
     */
    List<EcritureComptable> duJournal(CentreId centre, CodeJournal journal, LocalDate du, LocalDate au);
}
