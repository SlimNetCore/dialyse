package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.DetailPayeur;
import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;
import com.hemodialyse.backend.contexts.organisation.domain.ReferentielsCommunsRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferentielsCommunsServiceTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));

    private static EntreeReferentiel entree(String code) {
        return new EntreeReferentiel(UUID.randomUUID().toString(), code, "Libellé " + code,
                null, null, null, null);
    }

    private ReferentielsCommunsService nouveauService() {
        return new ReferentielsCommunsService(new FakeReferentiels());
    }

    @Test
    void lister_centres_payeurs_du_centre() {
        List<EntreeReferentiel> items = nouveauService().centresPayeurs(CENTRE);
        assertEquals(1, items.size());
        assertEquals("CNAS", items.get(0).code());
    }

    @Test
    void lister_agences_du_centre() {
        assertEquals(1, nouveauService().agences(CENTRE).size());
    }

    @Test
    void lister_caisses_du_centre() {
        assertEquals(1, nouveauService().caisses(CENTRE).size());
    }

    @Test
    void lister_centres_payeurs_details() {
        List<DetailPayeur> details = nouveauService().centresPayeursDetails(CENTRE);
        assertEquals(1, details.size());
        assertEquals("CNAS", details.get(0).codeCentrePayeur());
    }

    @Test
    void lister_etats_patients_statiques() {
        List<EntreeReferentiel> etats = nouveauService().etatsPatients();
        assertEquals(8, etats.size());
        assertTrue(etats.stream().anyMatch(e -> e.code().equals("PERMANENT")));
    }

    private static class FakeReferentiels implements ReferentielsCommunsRepository {
        @Override
        public List<EntreeReferentiel> centresPayeurs(CentreId centreId) {
            return List.of(entree("CNAS"));
        }

        @Override
        public List<DetailPayeur> centresPayeursDetails(CentreId centreId) {
            return List.of(new DetailPayeur("1", "CNAS", "CNAS Alger", null, "AG1", "Agence 1",
                    "CA1", "Caisse 1"));
        }

        @Override
        public List<EntreeReferentiel> agences(CentreId centreId) {
            return List.of(entree("AG1"));
        }

        @Override
        public List<EntreeReferentiel> caisses(CentreId centreId) {
            return List.of(entree("CA1"));
        }
    }
}
