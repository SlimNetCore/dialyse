package com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs.PagesDesFournisseurs.FiltreDesFournisseurs;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsulterLesFournisseursParPageTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));

    private FiltreDesFournisseurs filtre;
    private DemandeDePage demande;
    private final ConsulterLesFournisseursParPage consulter = new ConsulterLesFournisseursParPage((c, f, d) -> {
        filtre = f;
        demande = d;
        return PageDeResultats.decouper(List.of(), d);
    });

    @Test
    void defaut_raison_sociale_ascendante() {
        consulter.page(CENTRE, new ParametresDePage(null, null, null), null, "", null, null);
        assertEquals(new DemandeDePage(0, 20, "raisonSociale", false), demande);
        assertEquals(new FiltreDesFournisseurs(null, null, null, null), filtre);
    }

    @Test
    void recherche_et_filtres_normalises() {
        consulter.page(CENTRE, new ParametresDePage(3, 10, "contact,DESC"), " Acme ", "F1", "Pharma", " DUPONT");
        assertEquals(new DemandeDePage(3, 10, "contact", true), demande);
        assertEquals(new FiltreDesFournisseurs("acme", "f1", "pharma", "dupont"), filtre);
    }

    @Test
    void tri_hors_liste_blanche_refuse() {
        assertEquals("TRI_INVALIDE", assertThrows(PaginationInvalide.class,
                () -> consulter.page(CENTRE, new ParametresDePage(0, 20, "telephone"), null, null, null, null)).code());
        assertEquals("TAILLE_INVALIDE", assertThrows(PaginationInvalide.class,
                () -> consulter.page(CENTRE, new ParametresDePage(0, 0, null), null, null, null, null)).code());
    }
}
