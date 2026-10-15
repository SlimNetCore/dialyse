package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import java.time.Instant;
import java.util.List;

/**
 * Export comptable : ce qu'il y a de nouveau à transmettre au logiciel comptable pour un journal et une période. Les écritures
 * déjà exportées n'y figurent plus ; les brouillons y figurent (reproduit : seules les validées passent à « exportée »).
 */
public final class ExportComptable {

    private final List<EcritureComptable> ecritures;

    private ExportComptable(List<EcritureComptable> ecritures) {
        this.ecritures = ecritures;
    }

    /**
     * L'export des écritures du journal sur la période, sans celles qui ont déjà été exportées.
     */
    public static ExportComptable depuis(List<EcritureComptable> ecrituresDuJournal) {
        return new ExportComptable(ecrituresDuJournal.stream().filter(e -> !e.estExportee()).toList());
    }

    /**
     * Vrai s'il n'y a rien de nouveau à transmettre.
     */
    public boolean estVide() {
        return ecritures.isEmpty();
    }

    public List<EcritureComptable> ecritures() {
        return ecritures;
    }

    /**
     * Marque exportées les écritures validées du fichier, datées et signées ; rend celles qui ont changé d'état.
     */
    public List<EcritureComptable> marquerExportees(Instant le, String par) {
        List<EcritureComptable> validees = ecritures.stream().filter(e -> e.statut() == StatutEcriture.VALIDEE).toList();
        validees.forEach(e -> e.marquerExportee(le, par));
        return validees;
    }
}
