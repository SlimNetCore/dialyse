package com.hemodialyse.backend.contexts.stock.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.inventaire.ProcesVerbalDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ACL vers Documents (PL7) : le PV est demandé au type INVENTAIRE_STOCK, pour le centre de l'inventaire, au format du modèle.
 */
class ProcesVerbalRenduParDocumentsTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZoneOffset.UTC);

    private static Inventaire inventaireVide() {
        return Inventaire.ouvrir(UUID.randomUUID(), new CentreId(UUID.randomUUID()), "INV-00001", LocalDate.of(2026, 9, 30), null,
                "pharma", NOW, List.of());
    }

    @Test
    void lePvEstDemandeAuTypeInventaireStockPourLeCentreDeLInventaire() {
        AtomicReference<Object[]> appel = new AtomicReference<>();
        ProcesVerbalDInventaire acl = new ProcesVerbalRenduParDocuments((centre, type, donnees, format) -> {
            appel.set(new Object[]{centre, type, donnees, format});
            return new DocumentRendu(new byte[]{1, 2}, "application/pdf", "x.pdf");
        });
        Inventaire inventaire = inventaireVide();

        ProcesVerbalDInventaire.Document document = acl.imprimer(inventaire, "admin");

        assertEquals(inventaire.centre().valeur(), appel.get()[0]);
        assertEquals("INVENTAIRE_STOCK", appel.get()[1]);
        assertNull(appel.get()[3]);
        @SuppressWarnings("unchecked") Map<String, Object> donnees = (Map<String, Object>) appel.get()[2];
        assertEquals("INV-00001", donnees.get("INV_REFERENCE"));
        assertEquals("admin", donnees.get("EDITE_PAR"));
        assertEquals("PDF", document.format());
        assertArrayEquals(new byte[]{1, 2}, document.contenu());
    }

    @Test
    void leFormatSeDeduitDuTypeMime() {
        assertEquals("EXCEL", ProcesVerbalRenduParDocuments.format("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertEquals("HTML", ProcesVerbalRenduParDocuments.format("text/html;charset=UTF-8"));
        assertEquals("PDF", ProcesVerbalRenduParDocuments.format("application/pdf"));
        assertEquals("PDF", ProcesVerbalRenduParDocuments.format(null));
    }
}
