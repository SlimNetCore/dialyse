package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fournisseurs et emplacements (RG-STK-003) ; nettoyage des valeurs (KB-STK-09 corrigé) ; pagination (RG-STK-007).
 */
class ReferentielSimpleTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());

    @Test
    void un_fournisseur_exige_une_raison_sociale_et_nait_actif_nettoye() {
        assertEquals("FOURNISSEUR_RAISON_SOCIALE_REQUISE", assertThrows(RefusDuReferentiel.class,
                () -> Fournisseur.referencer(UUID.randomUUID(), CENTRE, "F", "  ", null, null, null)).code());
        Fournisseur f = Fournisseur.referencer(UUID.randomUUID(), CENTRE, " ", " Pharma Sud ", " M. B ", "", " a@b.dz ");
        assertEquals("Pharma Sud", f.raisonSociale());
        assertEquals("M. B", f.contact());
        assertEquals("a@b.dz", f.email());
        assertNull(f.code());
        assertNull(f.telephone());
        assertTrue(f.actif());
    }

    @Test
    void un_emplacement_exige_un_libelle_et_nait_actif_nettoye() {
        assertEquals("EMPLACEMENT_LIBELLE_REQUIS", assertThrows(RefusDuReferentiel.class,
                () -> Emplacement.creer(UUID.randomUUID(), CENTRE, "E", null)).code());
        Emplacement e = Emplacement.creer(UUID.randomUUID(), CENTRE, " E1 ", " Armoire froide ");
        assertEquals("E1", e.code());
        assertEquals("Armoire froide", e.libelle());
        assertTrue(e.actif());
    }

    @Test
    void la_pagination_est_bornee() {
        assertEquals(new Pagination(0, 2), Pagination.demandee(-3, 2));
        assertEquals(new Pagination(1, 1), Pagination.demandee(1, 0));
        assertEquals(new Pagination(0, 200), Pagination.demandee(0, 500));
        assertEquals(40L, Pagination.demandee(2, 20).decalage());
    }
}



