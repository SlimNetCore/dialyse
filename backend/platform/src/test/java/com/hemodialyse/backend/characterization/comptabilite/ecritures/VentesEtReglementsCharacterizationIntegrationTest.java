package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import com.hemodialyse.backend.domain.facturation.event.FacturationValideeEvent;
import com.hemodialyse.backend.domain.reglement.event.FactureReglementRecordedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Filet C5 : écritures de ventes (politique P1, écouteur de {@code FacturationValideeEvent}), de règlement (politique
 * P2, écouteur de {@code FactureReglementRecordedEvent}), reconstitution {@code POST /rebuild} et liste des factures sans
 * écriture. Les événements sont publiés par le vrai {@link ApplicationEventPublisher} ; les factures et règlements sont
 * insérés en SQL avec des identifiants fixes ({@code <FACTURE_n>}, {@code <REGLEMENT_n>}). Observation : réponses HTTP,
 * état SQL des écritures et des lignes, exception éventuellement remontée à l'appelant.
 * <p>
 * Les goldens {@code *_nouveau_code} portent les écarts décidés le 2026-10-19 (KB-CPT-37 à 39) : comptabilisation après le
 * commit de l'appelant, chaque source dans sa propre transaction, refus rapportés et régénérables.
 */
class VentesEtReglementsCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    private static final String REBUILD = "/rebuild";
    private static final String NON_COMPTABILISEES = "/ventes/non-comptabilisees";
    /**
     * Payeur sans compte client paramétré (compte client par défaut).
     */
    private static final UUID MUTUELLE = UUID.fromString("99996300-0000-0000-0000-000000000012");
    private static final OffsetDateTime QUAND = OffsetDateTime.of(2026, 2, 1, 9, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private ApplicationEventPublisher evenements;
    @Autowired
    private PlatformTransactionManager transactions;

    @Override
    protected String theme() {
        return "ventes";
    }

    @BeforeEach
    @AfterEach
    void nettoyerFacturesEtReglements() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B, CENTRE_VIDE)) {
            jdbc.update("DELETE FROM facture_reglements WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM factures WHERE center_id = ?", centre);
        }
    }

    // ------------------------------------------------------------------ fixtures

    private void facture(int n, UUID centre, String numero, String debut, String fin, String dateFacturation,
                         UUID payeur, String ht, String tva, String ttc) {
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_full_name, "
                        + "centre_payeur_id_snapshot, period_start, period_end, date_facturation, tva_rate, total_ht, "
                        + "total_tva, total_ttc) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                fid(n), centre, UUID.randomUUID(), numero, "Patient " + n, payeur, LocalDate.parse(debut),
                LocalDate.parse(fin), LocalDate.parse(dateFacturation), new BigDecimal("19.00"), new BigDecimal(ht),
                new BigDecimal(tva), new BigDecimal(ttc));
    }

    private void factureDeJanvier(int n, String numero, String dateFacturation, UUID payeur, String ht, String tva,
                                  String ttc) {
        facture(n, CENTRE_A, numero, "2026-01-01", "2026-01-31", dateFacturation, payeur, ht, tva, ttc);
    }

    private void reglement(int n, int facture, UUID centre, String montant, String date, String saisiPar) {
        jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", rid(n), fid(facture), centre, new BigDecimal(montant),
                LocalDate.parse(date), saisiPar);
    }

    private void comptePayeur(UUID payeur, String compte) {
        jdbc.update("DELETE FROM comptes_payeurs WHERE center_id = ? AND payeur_id = ?", CENTRE_A, payeur);
        jdbc.update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, ?)", CENTRE_A, payeur,
                compte);
    }

    private void facturationValidee(UUID centre, String debut, String fin) {
        evenements.publishEvent(new FacturationValideeEvent(centre, LocalDate.parse(debut), LocalDate.parse(fin), 0, 0,
                BigDecimal.ZERO, QUAND));
    }

    private void reglementEnregistre(int facture, String montant, String date, String userId) {
        evenements.publishEvent(new FactureReglementRecordedEvent(fid(facture), CENTRE_A, new BigDecimal(montant),
                LocalDate.parse(date), userId));
    }

    private Resp rebuild(String corps, org.springframework.test.web.servlet.request.RequestPostProcessor who)
            throws Exception {
        return doPost(REBUILD, json(corps), who);
    }

    /**
     * Exécute l'action dans une transaction appelante (comme Facturation/Règlement) ; nom de l'exception au commit.
     */
    private String dansUneTransactionAppelante(Runnable action) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(s -> action.run());
            return "aucune";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    // ------------------------------------------------------------------ P1 : écritures de ventes

    @Test
    void p1_genere_une_vente_par_facture_dont_la_periode_est_incluse_dans_la_periode_validee() {
        comptePayeur(CNAS, "411210");
        factureDeJanvier(1, "F-001", "2026-02-03", CNAS, "1000.00", "190.00", "1190.00");
        factureDeJanvier(2, "F-002", "2026-02-03", MUTUELLE, "500.00", "0.00", "595.00");
        facture(3, CENTRE_A, "F-003", "2026-01-10", "2026-01-20", "2026-02-04", null, "200.00", "0.00", "200.00");
        facture(4, CENTRE_A, "F-004", "2026-01-15", "2026-02-15", "2026-02-16", null, "300.00", "0.00", "300.00");
        facture(5, CENTRE_B, "F-005", "2026-01-01", "2026-01-31", "2026-02-03", null, "400.00", "0.00", "400.00");

        assertDoesNotThrow(() -> facturationValidee(CENTRE_A, "2026-01-01", "2026-01-31"));

        goldenEcritures("p1_ventes_ecritures", CENTRE_A);
        goldenLignes("p1_ventes_lignes", CENTRE_A);
        assertEquals(0, count("ecritures_comptables", CENTRE_B));
    }

    @Test
    void p1_rejouee_ne_cree_pas_de_doublon_meme_si_le_compte_du_payeur_change() {
        comptePayeur(CNAS, "411210");
        factureDeJanvier(1, "F-001", "2026-02-03", CNAS, "1000.00", "0.00", "1000.00");
        facturationValidee(CENTRE_A, "2026-01-01", "2026-01-31");
        comptePayeur(CNAS, "411299");
        facturationValidee(CENTRE_A, "2026-01-01", "2026-01-31");

        assertEquals(1, count("ecritures_comptables", CENTRE_A));
        goldenLignes("p1_rejouee_lignes", CENTRE_A);
    }

    @Test
    void p1_une_facture_desequilibree_est_ignoree_sans_arreter_les_factures_suivantes() {
        factureDeJanvier(1, "F-001", "2026-02-01", null, "100.00", "0.00", "100.00");
        factureDeJanvier(2, "F-002", "2026-02-02", null, "100.00", "19.00", "120.00");
        factureDeJanvier(3, "F-003", "2026-02-03", null, "300.00", "0.00", "300.00");

        assertDoesNotThrow(() -> facturationValidee(CENTRE_A, "2026-01-01", "2026-01-31"));

        // KB-CPT-38 corrigé : chaque facture est indépendante ; F-002 reste à régénérer.
        goldenEcritures("p1_desequilibre_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void p1_sur_une_periode_cloturee_ne_genere_rien_et_n_echoue_pas() {
        periode(CENTRE_A, 2026, 1, true, "compta");
        factureDeJanvier(1, "F-001", "2026-02-03", null, "100.00", "0.00", "100.00");

        assertDoesNotThrow(() -> facturationValidee(CENTRE_A, "2026-01-01", "2026-01-31"));

        assertEquals(0, count("ecritures_comptables", CENTRE_A));
    }

    @Test
    void p1_dans_la_transaction_appelante_un_refus_n_annule_jamais_la_facturation() {
        ObjectNode observe = mapper.createObjectNode();
        factureDeJanvier(1, "F-001", "2026-02-03", null, "100.00", "0.00", "100.00");
        observe.put("sansCloture_exceptionAuCommit", dansUneTransactionAppelante(
                () -> facturationValidee(CENTRE_A, "2026-01-01", "2026-01-31")));
        observe.put("sansCloture_ecritures", count("ecritures_comptables", CENTRE_A));

        periode(CENTRE_A, 2026, 3, true, "compta");
        facture(2, CENTRE_A, "F-002", "2026-03-01", "2026-03-31", "2026-04-02", null, "50.00", "0.00", "50.00");
        observe.put("avecCloture_exceptionAuCommit", dansUneTransactionAppelante(() -> {
            facture(3, CENTRE_A, "F-003", "2026-05-01", "2026-05-31", "2026-06-01", null, "10.00", "0.00", "10.00");
            facturationValidee(CENTRE_A, "2026-03-01", "2026-03-31");
        }));
        observe.put("avecCloture_ecritureDeLAppelanteConservee",
                jdbc.queryForObject("SELECT COUNT(*) FROM factures WHERE id = ?", Integer.class, fid(3)));
        observe.put("avecCloture_ecritures", count("ecritures_comptables", CENTRE_A));

        // KB-CPT-37 corrigé : comptabilisation après commit, la facture de l'appelant est conservée.
        goldenNode("p1_transaction_appelante_nouveau_code", observe);
    }

    // ------------------------------------------------------------------ P2 : écritures de règlement

    @Test
    void p2_credite_le_compte_debite_par_la_vente_avec_l_id_du_reglement_retrouve_en_base() {
        comptePayeur(CNAS, "411210");
        genererVente(CENTRE_A, fid(1), "F-001", "1000.00", "0.00", "1000.00", "2026-01-31", CNAS);
        comptePayeur(CNAS, "411299");
        reglement(1, 1, CENTRE_A, "400.00", "2026-02-10", "u-1");
        reglement(2, 1, CENTRE_A, "400.00", "2026-02-10", "u-autre");

        assertDoesNotThrow(() -> reglementEnregistre(1, "400.00", "2026-02-10", "u-1"));

        goldenEcritures("p2_reglement_ecritures", CENTRE_A);
        goldenLignes("p2_reglement_lignes", CENTRE_A);
    }

    @Test
    void p2_sans_reglement_en_base_derive_un_id_rejouable_et_prend_la_valeur_absolue() {
        assertDoesNotThrow(() -> reglementEnregistre(2, "-150.00", "2026-02-11", "u-2"));
        reglementEnregistre(2, "-150.00", "2026-02-11", "u-2");

        assertEquals(1, count("ecritures_comptables", CENTRE_A));
        UUID derive = UUID.nameUUIDFromBytes((CENTRE_A + "|" + fid(2) + "|2026-02-11|-150.00|u-2").getBytes());
        assertEquals(derive, jdbc.queryForObject("SELECT source_id FROM ecritures_comptables WHERE center_id = ?",
                UUID.class, CENTRE_A));
        goldenLignes("p2_derive_negatif_lignes", CENTRE_A);
    }

    @Test
    void p2_sur_une_periode_cloturee_ne_genere_rien_et_n_echoue_pas() {
        periode(CENTRE_A, 2026, 2, true, "compta");
        reglement(1, 1, CENTRE_A, "100.00", "2026-02-10", "u-1");

        assertDoesNotThrow(() -> reglementEnregistre(1, "100.00", "2026-02-10", "u-1"));

        assertEquals(0, count("ecritures_comptables", CENTRE_A));
    }

    @Test
    void p2_dans_la_transaction_appelante_un_refus_n_annule_jamais_le_reglement() {
        ObjectNode observe = mapper.createObjectNode();
        periode(CENTRE_A, 2026, 2, true, "compta");
        observe.put("exceptionAuCommit", dansUneTransactionAppelante(() -> {
            reglement(1, 1, CENTRE_A, "100.00", "2026-02-10", "u-1");
            reglementEnregistre(1, "100.00", "2026-02-10", "u-1");
        }));
        observe.put("reglementDeLAppelanteConserve",
                jdbc.queryForObject("SELECT COUNT(*) FROM facture_reglements WHERE id = ?", Integer.class, rid(1)));
        observe.put("ecritures", count("ecritures_comptables", CENTRE_A));

        // KB-CPT-37 corrigé : le règlement est conservé, son écriture reste à régénérer.
        goldenNode("p2_transaction_appelante_nouveau_code", observe);
    }

    // ------------------------------------------------------------------ POST /rebuild et régénération

    @Test
    void rebuild_reconstitue_ventes_a_la_fin_de_leur_periode_et_reglements_puis_est_rejouable() throws Exception {
        comptePayeur(CNAS, "411210");
        factureDeJanvier(1, "F-001", "2026-02-03", CNAS, "1000.00", "190.00", "1190.00");
        factureDeJanvier(2, "F-002", "2026-02-20", null, "200.00", "0.00", "200.00");
        factureDeJanvier(3, "F-003", "2026-03-02", null, "300.00", "0.00", "300.00");
        facture(4, CENTRE_B, "F-004", "2026-01-01", "2026-01-31", "2026-02-03", null, "400.00", "0.00", "400.00");
        reglement(1, 1, CENTRE_A, "1190.00", "2026-02-25", null);
        reglement(2, 2, CENTRE_A, "50.00", "2026-03-01", "u-1");
        String corps = "{'centerId':'" + CENTRE_A + "','from':'2026-02-01','to':'2026-02-28'}";

        golden("rebuild_premier_passage_200", rebuild(corps, adminA()));
        // B10 corrigé : une vente régénérée est datée de la fin de sa période, comme par P1.
        goldenEcritures("rebuild_ecritures_nouveau_code", CENTRE_A);
        goldenLignes("rebuild_lignes", CENTRE_A);

        golden("rebuild_second_passage_200", rebuild(corps, adminA()));
        assertEquals(3, count("ecritures_comptables", CENTRE_A));
        assertEquals(0, count("ecritures_comptables", CENTRE_B));
    }

    @Test
    void rebuild_continue_apres_une_source_refusee_et_la_rapporte() throws Exception {
        periode(CENTRE_A, 2026, 1, true, "compta");
        factureDeJanvier(1, "F-001", "2026-02-03", null, "100.00", "0.00", "100.00");
        facture(2, CENTRE_A, "F-002", "2026-02-01", "2026-02-28", "2026-02-27", null, "200.00", "0.00", "200.00");
        reglement(1, 2, CENTRE_A, "200.00", "2026-02-27", "u-1");

        golden("rebuild_refus_rapporte_nouveau_code", rebuild("{'from':'2026-02-01','to':'2026-02-28'}", adminA()));
        goldenEcritures("rebuild_refus_rapporte_ecritures_nouveau_code", CENTRE_A);
    }

    @Test
    void une_facture_refusee_par_p1_est_listee_puis_regeneree_par_rebuild_a_la_fin_de_sa_periode() throws Exception {
        factureDeJanvier(1, "F-001", "2026-02-02", null, "100.00", "0.00", "100.00");
        factureDeJanvier(2, "F-002", "2026-02-03", null, "100.00", "19.00", "120.00");
        facturationValidee(CENTRE_A, "2026-01-01", "2026-01-31");
        String fevrier = "?from=2026-02-01&to=2026-02-28";

        golden("regeneration_1_facture_a_regenerer_nouveau_code", doGet(NON_COMPTABILISEES + fevrier, adminA()));
        jdbc.update("UPDATE factures SET total_ttc = ? WHERE id = ?", new BigDecimal("119.00"), fid(2));
        golden("regeneration_2_rebuild_nouveau_code", rebuild("{'from':'2026-02-01','to':'2026-02-28'}", adminA()));
        goldenEcritures("regeneration_3_ecritures_nouveau_code", CENTRE_A);
        golden("regeneration_4_plus_rien_a_regenerer_nouveau_code", doGet(NON_COMPTABILISEES + fevrier, adminA()));
    }

    @Test
    void rebuild_roles_centre_et_validation_des_dates() throws Exception {
        String fevrier = "{'from':'2026-02-01','to':'2026-02-28'}";
        golden("rebuild_secretaire_403", rebuild(fevrier, secretaireA()));
        golden("rebuild_autre_centre_403",
                rebuild("{'centerId':'" + CENTRE_B + "','from':'2026-02-01','to':'2026-02-28'}", adminA()));
        golden("rebuild_sans_from_400", rebuild("{'to':'2026-02-28'}", adminA()));
        golden("rebuild_dates_inversees_200", rebuild("{'from':'2026-02-28','to':'2026-02-01'}", adminA()));
        golden("rebuild_corps_absent_500", send(post(java.net.URI.create(BASE + REBUILD)).with(adminA())));
        Resp anonyme = send(post(java.net.URI.create(BASE + REBUILD)).contentType(MediaType.APPLICATION_JSON)
                .content(json(fevrier)));
        goldenNode("rebuild_anonyme", mapper.createObjectNode().put("status", anonyme.status()));
        golden("non_comptabilisees_secretaire_403_nouveau_code",
                doGet(NON_COMPTABILISEES + "?from=2026-02-01&to=2026-02-28", secretaireA()));
        golden("non_comptabilisees_autre_centre_403_nouveau_code",
                doGet(NON_COMPTABILISEES + "?centerId=" + CENTRE_B + "&from=2026-02-01&to=2026-02-28", adminA()));
    }
}
