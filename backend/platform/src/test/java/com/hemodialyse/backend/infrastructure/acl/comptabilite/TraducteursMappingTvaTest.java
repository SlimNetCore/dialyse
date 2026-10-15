package com.hemodialyse.backend.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.MappingComptableDuCentre.MappingPublie;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.OperationComptable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Traducteurs de l'ACL du mapping et de la règle de TVA (langage publié du contexte comptabilite vers l'ancien modèle encore lu
 * par la génération des écritures et par les Absences) : table de correspondance, sans base.
 */
class TraducteursMappingTvaTest {

    private static final UUID CENTRE = UUID.randomUUID();

    private static Map<String, String> journaux(String vente, String banque, String caisse, String reception, String sortie,
                                                String inventaire) {
        Map<String, String> j = new LinkedHashMap<>();
        j.put("VENTE", vente);
        j.put("REGLEMENT_BANQUE", banque);
        j.put("REGLEMENT_CAISSE", caisse);
        j.put("STOCK_RECEPTION", reception);
        j.put("STOCK_SORTIE", sortie);
        j.put("STOCK_INVENTAIRE", inventaire);
        return j;
    }

    @Test
    void le_mapping_publie_est_traduit_compte_par_compte_et_journal_par_journal() {
        MappingPublie publie = new MappingPublie(CENTRE, "7061", "411101", "411501", "5121", "5301", "44572", "3221", "6021",
                "4081", "7571", "6571", journaux("OD", "BQ", "CA", "AC", "ST", "OD"));

        MappingComptable ancien = MappingComptableDuContexte.traduire(publie);

        assertEquals(CENTRE, ancien.centerId());
        assertEquals("7061", ancien.compteVentes());
        assertEquals("411101", ancien.compteClientPatient());
        assertEquals("411501", ancien.compteClientDefaut());
        assertEquals("5121", ancien.compteBanque());
        assertEquals("5301", ancien.compteCaisse());
        assertEquals("44572", ancien.compteTVACollectee());
        assertEquals("3221", ancien.stock().stock());
        assertEquals("6021", ancien.stock().consommation());
        assertEquals("4081", ancien.stock().facturesNonParvenues());
        assertEquals("7571", ancien.stock().boniInventaire());
        assertEquals("6571", ancien.stock().maliInventaire());
        assertEquals(JournalCode.de("OD"), ancien.journalDe(OperationComptable.VENTE));
        assertEquals(JournalCode.BQ, ancien.journalDe(OperationComptable.REGLEMENT_BANQUE));
        assertEquals(JournalCode.CA, ancien.journalDe(OperationComptable.REGLEMENT_CAISSE));
        assertEquals(JournalCode.AC, ancien.journalDe(OperationComptable.STOCK_RECEPTION));
        assertEquals(JournalCode.ST, ancien.journalDe(OperationComptable.STOCK_SORTIE));
        assertEquals(JournalCode.de("OD"), ancien.journalDe(OperationComptable.STOCK_INVENTAIRE));
    }

    @Test
    void un_mapping_sans_compte_de_tva_collectee_reste_sans_compte() {
        MappingPublie publie = new MappingPublie(CENTRE, "706", "411100", "411500", "512", "530", null, "322", "602", "408",
                "757", "657", journaux("VE", "BQ", "CA", "AC", "ST", "ST"));

        assertNull(MappingComptableDuContexte.traduire(publie).compteTVACollectee());
    }

    @Test
    void la_regle_publiee_est_traduite_avec_son_exoneration_son_taux_saisi_et_ses_dates() {
        var parametrage = new ParametrageFiscalDuContexte((centre, type, date) -> Optional.of(
                new ParametrageFiscal.RegleTvaApplicable(type, new BigDecimal("150.00"), true, LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-12-31"), "Article 13")));

        var ancienne = parametrage.findActiveAt(CENTRE, "HEMODIALYSE", LocalDate.parse("2026-05-01")).orElseThrow();

        assertEquals("HEMODIALYSE", ancienne.typePrestation());
        assertEquals(new BigDecimal("150.00"), ancienne.tauxApplique());
        assertTrue(ancienne.exonere());
        assertEquals(LocalDate.parse("2026-01-01"), ancienne.dateDebutValidite());
        assertEquals(LocalDate.parse("2026-12-31"), ancienne.dateFinValidite());
        assertEquals("Article 13", ancienne.texteReference());
    }

    @Test
    void une_regle_ouverte_et_non_exoneree_est_traduite_sans_date_de_fin() {
        var parametrage = new ParametrageFiscalDuContexte((centre, type, date) -> Optional.of(
                new ParametrageFiscal.RegleTvaApplicable(type, new BigDecimal("9.00"), false, LocalDate.parse("2026-01-01"), null, null)));

        var ancienne = parametrage.findActiveAt(CENTRE, "HEMODIALYSE", LocalDate.parse("2026-05-01")).orElseThrow();

        assertFalse(ancienne.exonere());
        assertNull(ancienne.dateFinValidite());
    }

    @Test
    void sans_regle_publiee_rien_n_est_traduit() {
        var parametrage = new ParametrageFiscalDuContexte((centre, type, date) -> Optional.empty());

        assertTrue(parametrage.findActiveAt(CENTRE, "HEMODIALYSE", LocalDate.parse("2026-05-01")).isEmpty());
    }
}
