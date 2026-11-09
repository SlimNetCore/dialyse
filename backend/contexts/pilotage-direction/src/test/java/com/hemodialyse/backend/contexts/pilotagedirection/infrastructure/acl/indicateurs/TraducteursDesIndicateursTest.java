package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.indicateurs;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies.IndicateursCliniques;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies.IndicateursCliniquesDesCentres;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies.RepartitionFaceALaCible;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.dossiermedical.CliniquesParLeDossierMedical;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.stock.StockParLeStock;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Alerte;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection.Gravite;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Clinique;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Indicateurs;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Marqueur;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection.Stock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.IndicateursDuStock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Traducteurs table-driven du Pilotage vers les langages publiés du Dossier médical, du Stock et vers le port du rapport (PD6). */
class TraducteursDesIndicateursTest {

    private static final UUID SOCIETE = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 3, 31);

    private static RepartitionFaceALaCible repartition(long base) {
        return new RepartitionFaceALaCible(base, BigDecimal.valueOf(base + 1), BigDecimal.valueOf(base + 2), BigDecimal.valueOf(base + 3));
    }

    private static IndicateursCliniques cliniques(long base, boolean observance) {
        return new IndicateursCliniques(repartition(base), repartition(base + 10), repartition(base + 20), repartition(base + 30),
                repartition(base + 40), base + 1, base + 2, base + 3, base + 4, observance, base + 5, base + 6, base + 7);
    }

    @Test
    void les_indicateurs_cliniques_du_dossier_medical_sont_traduits_champ_par_champ_et_la_periode_est_transmise() {
        List<Object> demande = new java.util.ArrayList<>();
        IndicateursCliniquesPublies dossierMedical = (centres, du, au) -> {
            demande.addAll(List.of(centres, du, au));
            return new IndicateursCliniquesDesCentres(Map.of(A, cliniques(1, true)), cliniques(100, false));
        };

        var lus = new CliniquesParLeDossierMedical(dossierMedical).desCentres(List.of(A), new Periode(DEBUT, FIN));

        assertEquals(List.of(List.of(A), DEBUT, FIN), demande);
        var a = lus.parCentre().get(A);
        assertEquals(1L, a.ktV().evalues());
        assertEquals(BigDecimal.valueOf(2), a.ktV().pctDansCible());
        assertEquals(BigDecimal.valueOf(3), a.ktV().pctSousCible());
        assertEquals(BigDecimal.valueOf(4), a.ktV().pctAuDessus());
        assertEquals(11L, a.hemoglobine().evalues());
        assertEquals(21L, a.phosphore().evalues());
        assertEquals(31L, a.pth().evalues());
        assertEquals(41L, a.albumine().evalues());
        assertEquals(List.of(2L, 3L, 4L, 5L, 6L, 7L, 8L), List.of(a.vhbPositifs(), a.vhcPositifs(), a.vihPositifs(),
                a.patientsObservanceEnRetard(), a.greffeListeAttente(), a.greffeBilanEnCours(), a.greffesPeriode()));
        assertTrue(a.observanceEnRetard());
        assertEquals(100L, lus.consolides().ktV().evalues());
        assertEquals(false, lus.consolides().observanceEnRetard());
    }

    @Test
    void le_stock_du_stock_est_traduit_a_la_date_demandee_et_un_centre_inconnu_reste_absent() {
        List<Object> demande = new java.util.ArrayList<>();
        IndicateursDuStock stock = (centres, date) -> {
            demande.addAll(List.of(centres, date));
            return Map.of(A, new IndicateursDuStock.Indicateur(5, 2, new BigDecimal("12.3400"), 1, 3));
        };

        var lus = new StockParLeStock(stock).aLaDate(List.of(A, SOCIETE), FIN);

        assertEquals(List.of(List.of(A, SOCIETE), FIN), demande);
        assertEquals(1, lus.size());
        var s = lus.get(A);
        assertEquals(List.of(5L, 2L, 1L, 3L), List.of(s.articlesActifs(), s.articlesSousSeuil(), s.lotsPerimes(), s.lotsProches()));
        assertEquals(new BigDecimal("12.3400"), s.valeurStock());
        assertNull(lus.get(SOCIETE));
    }

    @Test
    void le_port_du_rapport_ne_retient_que_le_taux_dans_la_cible_et_nomme_les_centres_pas_les_totaux() {
        Marqueur m = new Marqueur(9L, new BigDecimal("81.5"), new BigDecimal("10"), new BigDecimal("8.5"));
        Marqueur masque = new Marqueur(null, null, null, null);
        Clinique k = new Clinique(m, masque, m, m, m, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        var centre = new IndicateursDeLaDirection.IndicateursDuCentre(A, "Centre A", true, k, new Stock(8, 7, 6, 5, new BigDecimal("4.00")));
        var totaux = new IndicateursDeLaDirection.IndicateursDuCentre(null, null, true, k, new Stock(8, 7, 6, 5, new BigDecimal("4.00")));
        Alerte alerte = new Alerte(A, "Centre A", "STOCK_SOUS_SEUIL", Gravite.CRITICAL, BigDecimal.TEN);
        IndicateursDeLaDirection source = (societe, du, au) -> new Indicateurs(SOCIETE, du, au, null, 5, List.of(centre), totaux, List.of(alerte));

        var p = new IndicateursDuRapportParLesIndicateursPublies(source).pour(SOCIETE, DEBUT, FIN);

        var c = p.centres().get(0);
        assertEquals("Centre A", c.nom());
        assertNull(p.totaux().nom());
        assertEquals(new BigDecimal("81.5"), c.clinique().ktV());
        assertNull(c.clinique().hemoglobine());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), List.of(c.clinique().vhbPositifs(), c.clinique().vhcPositifs(),
                c.clinique().vihPositifs(), c.clinique().patientsObservanceEnRetard(), c.clinique().greffeListeAttente(),
                c.clinique().greffeBilanEnCours(), c.clinique().greffesPeriode()));
        assertEquals(List.of(8L, 7L, 6L, 5L), List.of(c.stock().articlesActifs(), c.stock().articlesSousSeuil(), c.stock().lotsPerimes(),
                c.stock().lotsPeremptionProche()));
        assertEquals(new BigDecimal("4.00"), p.totaux().stock().valeurStock());
        assertEquals("CRITICAL", p.alertes().get(0).severity());
        assertEquals("STOCK_SOUS_SEUIL", p.alertes().get(0).code());
    }
}
