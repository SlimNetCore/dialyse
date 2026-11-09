package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La composition des indicateurs de la société (PD6) : règles reproduites du legacy, sans lecture ni framework. */
class IndicateursDeLaSocieteTest {

    private static final UUID SOCIETE = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Periode PERIODE = new Periode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 11));
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 4, 12, 8, 0, 0, 0, ZoneOffset.UTC);

    private static MarqueurClinique marqueur(String dansCible) {
        return new MarqueurClinique(10L, dansCible == null ? null : new BigDecimal(dansCible), null, null);
    }

    private static IndicateursCliniquesDuCentre clinique(String ktV, String hb, boolean observance) {
        return new IndicateursCliniquesDuCentre(marqueur(ktV), marqueur(hb), marqueur(null), marqueur(null), marqueur(null), 0L, 0L, 0L, null,
                observance, 0L, 0L, 0L);
    }

    private static StockDuCentre stock(long sousSeuil, long perimes, long proches, String valeur) {
        return new StockDuCentre(3, sousSeuil, perimes, proches, new BigDecimal(valeur));
    }

    private static SocieteSuivie societe(CentreDeLaSociete... centres) {
        return new SocieteSuivie(SOCIETE, "Société", List.of(centres));
    }

    private static final IndicateursCliniquesDuCentre CONSOLIDES = clinique("77.5", "55", false);

    @Test
    void une_societe_sans_centre_a_des_totaux_cliniques_publies_un_stock_a_zero_et_aucune_alerte() {
        var i = IndicateursDeLaSociete.composer(societe(), PERIODE, MAINTENANT, new CliniquesDesCentres(Map.of(), CONSOLIDES), Map.of());

        assertTrue(i.centres().isEmpty());
        assertTrue(i.alertes().isEmpty());
        assertEquals(CONSOLIDES, i.totaux().clinique());
        assertEquals(new BigDecimal("0.00"), i.totaux().stock().valeurStock());
        assertEquals(true, i.totaux().actif());
        assertEquals(null, i.totaux().centreId());
    }

    @Test
    void les_totaux_additionnent_les_valeurs_non_arrondies_des_centres_puis_arrondissent_une_fois() {
        var i = IndicateursDeLaSociete.composer(societe(new CentreDeLaSociete(A, "A", true), new CentreDeLaSociete(B, "B", false)), PERIODE,
                MAINTENANT, new CliniquesDesCentres(Map.of(A, clinique("90", "90", false), B, clinique("90", "90", false)), CONSOLIDES),
                Map.of(A, stock(0, 0, 0, "0.0030"), B, stock(0, 0, 0, "0.0030")));

        assertEquals(new BigDecimal("0.00"), i.centres().get(0).stock().valeurStock());
        assertEquals(new BigDecimal("0.00"), i.centres().get(1).stock().valeurStock());
        assertEquals(new BigDecimal("0.01"), i.totaux().stock().valeurStock());
        assertEquals(6, i.totaux().stock().articlesActifs());
        assertEquals(false, i.centres().get(1).actif());
    }

    @Test
    void un_centre_inconnu_du_stock_a_un_stock_vide_et_le_consolide_clinique_est_celui_du_dossier_medical() {
        var i = IndicateursDeLaSociete.composer(societe(new CentreDeLaSociete(A, "A", true)), PERIODE, MAINTENANT,
                new CliniquesDesCentres(Map.of(A, clinique("90", "90", false)), CONSOLIDES), Map.of());

        assertEquals(StockDuCentre.VIDE.arrondi(), i.centres().get(0).stock());
        assertEquals(CONSOLIDES, i.totaux().clinique());
    }

    @Test
    void les_alertes_lisent_les_taux_masques_l_observance_brute_et_le_stock_non_masque_dans_l_ordre_des_centres() {
        var i = IndicateursDeLaSociete.composer(societe(new CentreDeLaSociete(A, "A", true), new CentreDeLaSociete(B, "B", true)), PERIODE,
                MAINTENANT, new CliniquesDesCentres(Map.of(A, clinique("79.9", "49.9", true), B, clinique(null, null, false)), CONSOLIDES),
                Map.of(A, stock(2, 1, 0, "10"), B, stock(0, 0, 4, "10")));

        assertEquals(List.of(PolitiqueDAlertes.KTV_CONFORMITE_BASSE, PolitiqueDAlertes.HB_HORS_CIBLE, PolitiqueDAlertes.OBSERVANCE_EN_RETARD,
                        PolitiqueDAlertes.STOCK_SOUS_SEUIL, PolitiqueDAlertes.LOTS_PERIMES, PolitiqueDAlertes.LOTS_PEREMPTION_PROCHE),
                i.alertes().stream().map(AlerteLevee::code).toList());
        assertEquals(List.of(A, A, A, A, A, B), i.alertes().stream().map(AlerteLevee::centreId).toList());
    }
}
