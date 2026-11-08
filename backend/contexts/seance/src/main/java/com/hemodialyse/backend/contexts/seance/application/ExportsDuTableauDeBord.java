package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.FormatDExport;
import com.hemodialyse.backend.contexts.seance.domain.TableauDeBordDesSeances;

/**
 * Port sortant : produit le fichier d'export du tableau de bord dans le format demandé (CSV, XLSX, PDF). Le contenu ne porte que les
 * compteurs et les répartitions : ni donnée nominative, ni montant.
 */
public interface ExportsDuTableauDeBord {

    byte[] produire(FormatDExport format, TableauDeBordDesSeances tableau);
}
