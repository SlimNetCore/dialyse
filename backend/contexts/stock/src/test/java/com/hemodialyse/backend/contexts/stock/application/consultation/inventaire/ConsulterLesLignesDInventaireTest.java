package com.hemodialyse.backend.contexts.stock.application.consultation.inventaire;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.inventaire.PagesDesLignesDInventaire.EtatDesLignes;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.PaginationInvalide;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsulterLesLignesDInventaireTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));

    static final class LignesEnMemoire implements PagesDesLignesDInventaire {
        final Set<UUID> inventaires = new HashSet<>();
        Filtres filtres;
        DemandeDePage demande;

        @Override
        public boolean existe(CentreId centre, UUID inventaire) {
            return inventaires.contains(inventaire);
        }

        @Override
        public PageDeResultats<LigneDInventaire> lignes(CentreId centre, UUID inventaire, Filtres filtres, DemandeDePage demande) {
            this.filtres = filtres;
            this.demande = demande;
            return new PageDeResultats<>(List.of(), 0, demande.page(), demande.taille());
        }
    }

    private final LignesEnMemoire fake = new LignesEnMemoire();
    private final ConsulterLesLignesDInventaire lignes = new ConsulterLesLignesDInventaire(fake);
    private final UUID inventaire = UUID.randomUUID();

    @Test
    void par_defaut_toutes_les_lignes_dans_l_ordre_de_la_feuille() {
        fake.inventaires.add(inventaire);
        lignes.lignes(CENTRE, inventaire, null, null, null, null, null);
        assertEquals(new DemandeDePage(0, 20, "position", false), fake.demande);
        assertEquals(EtatDesLignes.TOUTES, fake.filtres.etat());
        assertTrue(fake.filtres.recherche().isEmpty());
    }

    @Test
    void etat_et_recherche_transmis() {
        fake.inventaires.add(inventaire);
        lignes.lignes(CENTRE, inventaire, 1, 100, "ecart,desc", "ECARTS", " LOT-7 ");
        assertEquals(EtatDesLignes.ECARTS, fake.filtres.etat());
        assertEquals("lot-7", fake.filtres.recherche().orElseThrow());
        assertEquals(new DemandeDePage(1, 100, "ecart", true), fake.demande);
    }

    @Test
    void un_seul_etat_connu_et_tri_de_la_liste_blanche() {
        fake.inventaires.add(inventaire);
        assertEquals(PaginationInvalide.FILTRE_INVALIDE, assertThrows(PaginationInvalide.class,
                () -> lignes.lignes(CENTRE, inventaire, null, null, null, "ECARTS,COMPTEES", null)).code());
        assertEquals(PaginationInvalide.FILTRE_INVALIDE, assertThrows(PaginationInvalide.class,
                () -> lignes.lignes(CENTRE, inventaire, null, null, null, "ecarts", null)).code());
        assertEquals(PaginationInvalide.TRI_INVALIDE, assertThrows(PaginationInvalide.class,
                () -> lignes.lignes(CENTRE, inventaire, null, null, "position", null, null)).code());
    }

    @Test
    void inventaire_inconnu_du_centre() {
        assertEquals("INVENTORY_NOT_FOUND", assertThrows(RefusDuReferentiel.class,
                () -> lignes.lignes(CENTRE, UUID.randomUUID(), null, null, null, null, null)).code());
    }
}
