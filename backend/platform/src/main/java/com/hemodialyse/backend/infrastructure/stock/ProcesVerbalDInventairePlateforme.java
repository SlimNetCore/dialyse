package com.hemodialyse.backend.infrastructure.stock;

import com.hemodialyse.backend.contexts.stock.application.inventaire.ProcesVerbalDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.infrastructure.reporting.InventaireReportService;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import org.springframework.stereotype.Component;

/**
 * Procès-verbal d'inventaire du contexte Stock, au-dessus du moteur de documents de la plateforme (modèle « INVENTAIRE_STOCK »).
 */
@Component
public class ProcesVerbalDInventairePlateforme implements ProcesVerbalDInventaire {
    private final InventaireReportService rapport;

    public ProcesVerbalDInventairePlateforme(InventaireReportService rapport) {
        this.rapport = rapport;
    }

    @Override
    public Document imprimer(Inventaire inventaire, String utilisateur) {
        ModeleDocumentPrinter.Document document = rapport.imprimer(InventaireVersLegacy.depuis(inventaire), utilisateur);
        return new Document(document.format(), document.content());
    }
}

