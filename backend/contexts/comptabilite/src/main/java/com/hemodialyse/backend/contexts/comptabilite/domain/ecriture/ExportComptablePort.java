package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import java.util.List;

/**
 * Port sortant vers un format de fichier comptable ; le domaine reste indépendant de Sage/CSV.
 */
public interface ExportComptablePort {
    byte[] exporter(List<EcritureComptable> ecritures);
}
