package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant : les articles sortis du stock un jour donné, cumulés par article (langage publié du Stock). Ce sont les BONS DE SORTIE
 * du Stock datés du jour, pas les consommables déclarés sur les séances (KB-SEA-61, reproduit sans alerte : un consommable ajouté à une
 * séance n'y apparaît pas).
 */
public interface SortiesDuJour {

    List<SortieDArticle> du(CentreId centre, LocalDate jour);

    record SortieDArticle(UUID article, String code, String libelle, BigDecimal quantiteTotale) {
    }
}
