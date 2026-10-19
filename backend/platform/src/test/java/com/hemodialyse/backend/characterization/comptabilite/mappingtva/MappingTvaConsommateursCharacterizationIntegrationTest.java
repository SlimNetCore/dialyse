package com.hemodialyse.backend.characterization.comptabilite.mappingtva;

import com.hemodialyse.backend.domain.absence.port.ForfaitAbsencePort;
import com.hemodialyse.backend.characterization.comptabilite.GenerationDEcritures;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal.RegleTvaApplicable;
import com.hemodialyse.backend.domain.facturation.aggregate.TypeTVA;
import com.hemodialyse.backend.domain.facturation.port.TypeTvaRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Filet de caractérisation C2 — consommateurs du mapping et des règles de TVA : écritures de ventes et de règlements
 * (C5), comptabilisation du stock (C6) qui lisent le mapping, et lecteurs de {@code tva_types} hors Comptabilité
 * (Facturation : {@code TypeTvaRepositoryPort} ; Absences : {@code ForfaitAbsencePort}). Seul l'effet observable est
 * verrouillé : l'écriture générée change quand le mapping change ; la règle applicable à une date est lue par les trois
 * lecteurs. Le comportement propre des écritures (C5) et du stock (C6) relève de leurs filets.
 */
class MappingTvaConsommateursCharacterizationIntegrationTest extends MappingTvaCharacterizationSupport {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
    @Autowired
    private org.springframework.cache.CacheManager cacheManager;
    @Autowired
    private GenerationDEcritures comptabilite;
    @Autowired
    private ParametrageFiscal fiscalComptabilite;
    @Autowired
    private TypeTvaRepositoryPort fiscalFacturation;
    @Autowired
    private ForfaitAbsencePort forfaitsAbsence;

    @Override
    protected String theme() {
        return "consommateurs";
    }

    /**
     * La liste des types de TVA de la Facturation est en cache (6 h) : chaque test repart d'un cache vide.
     */
    @org.junit.jupiter.api.BeforeEach
    void viderLeCacheDesTypesDeTva() {
        cacheManager.getCache("tva-types-list").clear();
    }

    private String mapping(String ventes, String patient, String defaut, String banque, String caisse, String tva,
                           String journaux) {
        return json("{'compteVentes':'" + ventes + "','compteClientPatient':'" + patient + "','compteClientDefaut':'"
                + defaut + "','compteBanque':'" + banque + "','compteCaisse':'" + caisse + "'"
                + (tva == null ? "" : ",'compteTVACollectee':'" + tva + "'")
                + (journaux == null ? "" : ",'journaux':" + journaux) + "}");
    }

    private String mappingParDefaut() {
        return mapping("706", "411100", "411500", "512", "530", "44571", null);
    }

    private void facture(UUID payeur, String numero, String ht, String tva, String ttc) {
        comptabilite.genererEcritureFacturation(new GenerationDEcritures.Facture(CENTRE_A,
                UUID.randomUUID(), numero, UUID.randomUUID(), payeur, new BigDecimal(ht), new BigDecimal(tva),
                new BigDecimal(ttc), DATE, "Séances"));
    }

    private void reglement(UUID facture, String mode, UUID payeur, String montant) {
        comptabilite.genererEcritureReglement(new GenerationDEcritures.Reglement(CENTRE_A, facture,
                UUID.randomUUID(), new BigDecimal(montant), DATE, mode, "Règlement", payeur));
    }

    // ------------------------------------------------------------------ écritures de ventes (C5)

    @Test
    void sans_mapping_enregistre_la_vente_utilise_les_comptes_et_le_journal_par_defaut() throws Exception {
        facture(null, "FACT-PATIENT", "1000.00", "190.00", "1190.00");
        facture(CNAS, "FACT-PAYEUR", "1000.00", "190.00", "1190.00");
        goldenLignes("vente_mapping_par_defaut_lignes", CENTRE_A);
    }

    @Test
    void la_vente_suit_les_comptes_du_mapping_ventes_clients_et_tva_collectee() throws Exception {
        assertEquals(200, doPut("/mapping", mapping("615", "421", "431", "512", "530", "4456", null), adminA()).status());
        facture(null, "FACT-PATIENT", "1000.00", "190.00", "1190.00");
        facture(MUTUELLE, "FACT-PAYEUR", "1000.00", "190.00", "1190.00");
        goldenLignes("vente_mapping_personnalise_lignes", CENTRE_A);
    }

    @Test
    void le_compte_de_tva_vide_au_mapping_est_relu_en_44571_et_porte_la_tva_collectee() throws Exception {
        assertEquals(200, doPut("/mapping", mapping("615", "421", "431", "512", "530", null, null), adminA()).status());
        facture(null, "FACT-PATIENT", "1000.00", "190.00", "1190.00");
        goldenLignes("vente_tva_vide_au_mapping_lignes", CENTRE_A);
    }

    @Test
    void une_vente_sans_tva_n_ecrit_pas_de_ligne_de_tva_collectee() throws Exception {
        assertEquals(200, doPut("/mapping", mapping("615", "421", "431", "512", "530", "4456", null), adminA()).status());
        facture(null, "FACT-EXONEREE", "1000.00", "0.00", "1000.00");
        goldenLignes("vente_sans_tva_lignes", CENTRE_A);
    }

    @Test
    void le_journal_des_ventes_et_la_numerotation_suivent_le_mapping() throws Exception {
        assertEquals(200, doPut("/journaux/OD", json("{'libelle':'Opérations diverses','actif':true}"), adminA()).status());
        assertEquals(200, doPut("/mapping", mapping("706", "411100", "411500", "512", "530", "44571", "{'VENTE':'OD'}"), adminA()).status());
        facture(null, "FACT-1", "100.00", "0.00", "100.00");
        facture(null, "FACT-2", "200.00", "0.00", "200.00");
        goldenLignes("vente_journal_od_lignes", CENTRE_A);
    }

    @Test
    void un_changement_de_mapping_ne_touche_pas_les_ecritures_deja_generees_et_ne_regenere_pas_une_facture_connue() throws Exception {
        UUID facture = UUID.randomUUID();
        GenerationDEcritures.Facture cmd = new GenerationDEcritures.Facture(
                CENTRE_A, facture, "FACT-STABLE", UUID.randomUUID(), null, new BigDecimal("500.00"), BigDecimal.ZERO,
                new BigDecimal("500.00"), DATE, "Séances");
        comptabilite.genererEcritureFacturation(cmd);
        assertEquals(200, doPut("/mapping", mapping("615", "421", "431", "512", "530", "4456", null), adminA()).status());
        comptabilite.genererEcritureFacturation(cmd);
        facture(null, "FACT-NOUVELLE", "500.00", "0.00", "500.00");
        goldenLignes("vente_changement_de_mapping_apres_coup_lignes", CENTRE_A);
    }

    // ------------------------------------------------------------------ écritures de règlements (C5)

    @Test
    void le_reglement_debite_la_banque_ou_la_caisse_du_mapping_dans_le_journal_correspondant() throws Exception {
        assertEquals(200, doPut("/journaux/OD", json("{'libelle':'Opérations diverses','actif':true}"), adminA()).status());
        assertEquals(200, doPut("/mapping", mapping("706", "411100", "411500", "512", "530", "44571",
                "{'REGLEMENT_BANQUE':'OD','REGLEMENT_CAISSE':'BQ'}"), adminA()).status());
        reglement(null, "BANQUE", MUTUELLE, "300.00");
        reglement(null, "CAISSE", null, "100.00");
        reglement(null, "VIREMENT", CNAS, "50.00");
        goldenLignes("reglement_comptes_et_journaux_du_mapping_lignes", CENTRE_A);
    }

    @Test
    void le_reglement_utilise_les_comptes_banque_et_caisse_personnalises() throws Exception {
        assertEquals(200, doPut("/mapping", mapping("706", "421", "431", "401", "613", "44571", null), adminA()).status());
        reglement(null, "BANQUE", MUTUELLE, "300.00");
        reglement(null, "CAISSE", null, "100.00");
        goldenLignes("reglement_comptes_banque_et_caisse_personnalises_lignes", CENTRE_A);
    }

    @Test
    void le_reglement_solde_le_compte_que_la_facture_a_debite_meme_si_le_mapping_a_change_depuis() throws Exception {
        assertEquals(200, doPut("/mapping", mapping("706", "411100", "411500", "512", "530", "44571", null), adminA()).status());
        UUID facture = UUID.randomUUID();
        comptabilite.genererEcritureFacturation(new GenerationDEcritures.Facture(CENTRE_A, facture,
                "FACT-ANCIENNE", UUID.randomUUID(), MUTUELLE, new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal("1000.00"), DATE, "Séances"));
        assertEquals(200, doPut("/mapping", mapping("706", "421", "431", "512", "530", "44571", null), adminA()).status());
        reglement(facture, "BANQUE", MUTUELLE, "1000.00");
        reglement(UUID.randomUUID(), "BANQUE", MUTUELLE, "10.00");
        goldenLignes("reglement_compte_client_de_la_facture_lignes", CENTRE_A);
    }

    // ------------------------------------------------------------------ comptabilisation du stock (C6)

    private void fixtureStock() {
        UUID simple = UUID.fromString("99996200-0000-0000-0000-0000000000a1");
        UUID propre = UUID.fromString("99996200-0000-0000-0000-0000000000a2");
        for (Object[] a : new Object[][]{{simple, "SER-10", null, null}, {propre, "EPO-4000", "321", "6021"}}) {
            jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, "
                            + "gere_par_lot, active, pmp_courant, compte_stock, compte_charge) "
                            + "VALUES (?, ?, ?, ?, 'u', 0, 0, FALSE, TRUE, 10.00, ?, ?)",
                    a[0], CENTRE_A, a[1], a[1], a[2], a[3]);
        }
        UUID bon = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, date_reception, statut) VALUES (?, ?, ?, ?, ?)",
                bon, CENTRE_A, "BR-2026-0001", LocalDate.of(2026, 9, 3), "VALIDE");
        for (Object[] l : new Object[][]{{simple, "100", "10.00"}, {propre, "4", "2500.00"}}) {
            jdbc.update("INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), bon, l[0], new BigDecimal((String) l[1]), new BigDecimal((String) l[2]));
        }
        for (Object[] m : new Object[][]{{simple, "20", "10.00"}, {propre, "1", "2500.00"}}) {
            jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, "
                            + "pmp_apres, created_at) VALUES (?, ?, ?, 'SORTIE', ?, ?, NULL, ?)", UUID.randomUUID(), CENTRE_A,
                    m[0], new BigDecimal((String) m[1]), new BigDecimal((String) m[2]),
                    Timestamp.from(LocalDate.of(2026, 9, 5).atTime(9, 0).toInstant(ZoneOffset.UTC)));
        }
        UUID inventaire = UUID.randomUUID();
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, created_at) "
                        + "VALUES (?, ?, ?, ?, 'CLOTURE', ?)", inventaire, CENTRE_A, "INV-2026-0001", LocalDate.of(2026, 9, 20),
                Timestamp.from(LocalDate.of(2026, 9, 20).atTime(8, 0).toInstant(ZoneOffset.UTC)));
        int position = 1;
        for (Object[] l : new Object[][]{{simple, "77", "79"}, {propre, "3", "2"}}) {
            jdbc.update("INSERT INTO inventaire_lignes (id, inventaire_id, center_id, position_ligne, article_id, "
                            + "quantite_theorique, quantite_comptee, pmp) VALUES (?, ?, ?, ?, ?, ?, ?, 10.00)",
                    UUID.randomUUID(), inventaire, CENTRE_A, position++, l[0], new BigDecimal((String) l[1]), new BigDecimal((String) l[2]));
        }
    }

    private Resp synchroniserLeStock() throws Exception {
        return doPost("/stock/synchroniser", json("{'from':'2026-09-01','to':'2026-09-30'}"), adminA());
    }

    @Test
    void la_comptabilisation_du_stock_utilise_les_comptes_et_les_journaux_par_defaut_sans_mapping_enregistre() throws Exception {
        fixtureStock();
        golden("stock_mapping_par_defaut_synchronisation_200", synchroniserLeStock());
        goldenLignes("stock_mapping_par_defaut_lignes", CENTRE_A);
    }

    @Test
    void la_comptabilisation_du_stock_suit_les_comptes_et_les_journaux_du_mapping_sauf_les_comptes_propres_a_l_article() throws Exception {
        journauxParDefaut(CENTRE_A);
        journal(CENTRE_A, "OD", "Opérations diverses", true);
        assertEquals(200, doPut("/mapping", json("{'compteVentes':'706','compteClientPatient':'411100','compteClientDefaut':'411500',"
                        + "'compteBanque':'512','compteCaisse':'530','compteTVACollectee':'44571','compteStock':'613',"
                        + "'compteConsommation':'615','compteFacturesNonParvenues':'401','compteBoniInventaire':'631',"
                        + "'compteMaliInventaire':'635','journaux':{'STOCK_RECEPTION':'OD','STOCK_SORTIE':'CA','STOCK_INVENTAIRE':'BQ'}}"),
                adminA()).status());
        fixtureStock();
        golden("stock_mapping_personnalise_synchronisation_200", synchroniserLeStock());
        goldenLignes("stock_mapping_personnalise_lignes", CENTRE_A);
    }

    // ------------------------------------------------------------------ lecteurs de tva_types hors Comptabilité

    private ObjectNode lecture(LocalDate date) {
        ObjectNode n = mapper.createObjectNode();
        n.put("date", date.toString());
        Optional<RegleTvaApplicable> comptabilite = fiscalComptabilite.regleApplicable(CENTRE_A, "HEMODIALYSE", date);
        n.put("comptabilite_taux", comptabilite.map(r -> r.taux().toPlainString()).orElse("aucune"));
        n.put("comptabilite_exonere", comptabilite.map(r -> String.valueOf(r.exonere())).orElse("aucune"));
        Optional<TypeTVA> facturation = fiscalFacturation.findActiveAt(CENTRE_A, "HEMODIALYSE", date);
        n.put("facturation_taux", facturation.map(r -> r.taux().toPlainString()).orElse("aucune"));
        n.put("absences_taux", forfaitsAbsence.tauxTva(CENTRE_A, date).toPlainString());
        return n;
    }

    private void goldenLectures(String name, String... dates) {
        ArrayNode a = mapper.createArrayNode();
        for (String d : dates) a.add(lecture(LocalDate.parse(d)));
        goldenNode(name, a);
    }

    private void enregistrer(String type, String taux, boolean exonere, String debut, String fin) throws Exception {
        assertEquals(200, doPost("/regles-tva", json("{'typePrestation':'" + type + "','tauxApplique':" + taux
                + ",'exonere':" + exonere + ",'dateDebutValidite':'" + debut + "'"
                + (fin == null ? "" : ",'dateFinValidite':'" + fin + "'") + "}"), adminA()).status());
    }

    /**
     * Une règle déjà en base, telle que la Facturation ou une version antérieure de la Comptabilité l'a écrite : la route
     * refuse désormais les règles qui se chevauchent (KB-CPT-16, écart décidé) mais les lecteurs doivent encore lire de telles
     * données.
     */
    private void enBase(String type, String taux, boolean exonere, String debut, String fin) {
        regle(CENTRE_A, type, taux, exonere, debut, fin, true);
    }

    @Test
    void la_regle_applicable_a_une_date_est_la_derniere_regle_commencee_a_cette_date_lue_par_les_trois_lecteurs() throws Exception {
        enBase("HEMODIALYSE", "19.00", false, "2024-01-01", null);
        enBase("HEMODIALYSE", "9.00", false, "2026-01-01", null);
        enBase("HEMODIALYSE", "5.00", false, "2027-01-01", null);
        goldenLectures("regle_applicable_derniere_regle_commencee_lectures", "2023-12-31", "2024-01-01", "2025-12-31",
                "2026-01-01", "2026-12-31", "2027-01-01", "2030-06-15");
    }

    @Test
    void les_bornes_de_validite_sont_incluses_et_sans_regle_suivante_le_taux_est_nul_cote_absences_et_absent_ailleurs() throws Exception {
        enregistrer("HEMODIALYSE", "19.00", false, "2026-01-01", "2026-06-30");
        goldenLectures("regle_applicable_bornes_incluses_lectures", "2025-12-31", "2026-01-01", "2026-06-30", "2026-07-01");
    }

    @Test
    void une_regle_recente_terminee_laisse_revenir_une_regle_plus_ancienne_restee_ouverte() throws Exception {
        enBase("HEMODIALYSE", "19.00", false, "2024-01-01", null);
        enBase("HEMODIALYSE", "9.00", false, "2026-01-01", "2026-03-31");
        goldenLectures("regle_applicable_regle_recente_terminee_lectures", "2026-02-01", "2026-03-31", "2026-04-01", "2026-12-31");
    }

    @Test
    void une_regle_exoneree_donne_un_taux_nul_aux_absences_mais_son_taux_saisi_a_la_facturation() throws Exception {
        enregistrer("HEMODIALYSE", "19.00", true, "2026-01-01", null);
        goldenLectures("regle_exoneree_lectures", "2026-02-01");
    }

    @Test
    void seule_la_prestation_hemodialyse_exacte_est_lue_par_la_facturation_et_les_absences() throws Exception {
        enregistrer("MEDICAMENT", "9.00", false, "2026-01-01", null);
        enregistrer(" HEMODIALYSE ", "19.00", false, "2026-01-01", null);
        enregistrer("hemodialyse", "19.00", false, "2026-01-01", null);
        goldenLectures("regle_prestation_exacte_lectures", "2026-02-01");
    }

    @Test
    void une_regle_d_un_autre_centre_n_est_lue_par_aucun_lecteur() throws Exception {
        assertEquals(200, doPost("/regles-tva", json("{'typePrestation':'HEMODIALYSE','tauxApplique':9,'exonere':false,"
                + "'dateDebutValidite':'2026-01-01'}"), adminB()).status());
        goldenLectures("regle_autre_centre_lectures", "2026-02-01");
    }

    @Test
    void une_regle_desactivee_par_la_facturation_reste_listee_par_la_comptabilite_mais_n_est_lue_par_aucun_lecteur() throws Exception {
        enBase("HEMODIALYSE", "19.00", false, "2024-01-01", null);
        enBase("HEMODIALYSE", "9.00", false, "2026-01-01", null);
        UUID recente = jdbc.queryForObject("SELECT id FROM tva_types WHERE center_id = ? AND taux = 9", UUID.class, CENTRE_A);
        int statut = mvc.perform(delete(java.net.URI.create("/api/v1/tva-types/" + recente + "?centerId=" + CENTRE_A)).with(adminA()))
                .andReturn().getResponse().getStatus();
        assertEquals(204, statut);
        golden("regle_desactivee_par_la_facturation_toujours_listee_200", doGet("/regles-tva", adminA()));
        goldenLectures("regle_desactivee_lectures", "2026-02-01");
    }

    @Test
    void une_regle_enregistree_par_la_comptabilite_apparait_dans_les_types_de_tva_de_la_facturation() throws Exception {
        enregistrer("HEMODIALYSE", "19.00", false, "2026-01-01", null);
        Resp r = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(java.net.URI.create("/api/v1/tva-types?centerId=" + CENTRE_A)).with(adminA()));
        assertEquals(200, r.status());
        assertEquals(1, json(r).path("items").size());
        assertEquals("HEMODIALYSE 19.00%", json(r).path("items").get(0).path("libelle").asString());
        assertEquals("comptabilite", json(r).path("items").get(0).path("createdBy").asString());
        assertTrue(json(r).path("items").get(0).path("actif").asBoolean());
    }

    @Test
    void la_liste_en_cache_des_types_de_tva_de_la_facturation_est_invalidee_par_un_enregistrement_de_la_comptabilite() throws Exception {
        // écart décidé (KB-CPT-20) : un POST /regles-tva réussi évince le cache de 6 h (même nom que celui de la Facturation)
        String liste = "/api/v1/tva-types?centerId=" + CENTRE_A;
        Resp avant = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(liste)).with(adminA()));
        assertEquals(0, json(avant).path("items").size());
        Resp encoreEnCache = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(liste)).with(adminA()));
        assertEquals(0, json(encoreEnCache).path("items").size());
        enBase("MEDICAMENT", "5.00", false, "2026-01-01", null);
        Resp inchangee = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(liste)).with(adminA()));
        assertEquals(0, json(inchangee).path("items").size(), "une écriture hors de la route ne vide pas le cache : il sert encore la liste d'avant");
        enregistrer("HEMODIALYSE", "19.00", false, "2026-01-01", null);
        Resp apres = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(liste)).with(adminA()));
        assertEquals(2, json(apres).path("items").size(), "la liste est relue après l'enregistrement, sans attendre l'expiration");
        assertEquals(2, count("tva_types", CENTRE_A));
    }

    @Test
    void un_enregistrement_refuse_par_la_comptabilite_ne_vide_pas_le_cache_des_types_de_tva() throws Exception {
        String liste = "/api/v1/tva-types?centerId=" + CENTRE_A;
        enBase("HEMODIALYSE", "19.00", false, "2026-01-01", null);
        Resp avant = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(liste)).with(adminA()));
        assertEquals(1, json(avant).path("items").size());
        enBase("MEDICAMENT", "5.00", false, "2026-01-01", null);
        assertEquals(400, doPost("/regles-tva", json("{'typePrestation':'HEMODIALYSE','tauxApplique':9,'exonere':false,"
                + "'dateDebutValidite':'2026-06-01'}"), adminA()).status());
        Resp apres = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(liste)).with(adminA()));
        assertEquals(1, json(apres).path("items").size(), "un refus n'évince rien : la liste en cache est encore servie");
    }

    @Test
    void un_taux_superieur_a_100_accepte_par_la_comptabilite_fait_echouer_la_lecture_de_la_facturation_pas_celle_des_absences() throws Exception {
        enregistrer("HEMODIALYSE", "150.00", false, "2026-01-01", null);
        LocalDate jour = LocalDate.of(2026, 2, 1);
        assertEquals(new BigDecimal("150.00"), fiscalComptabilite.regleApplicable(CENTRE_A, "HEMODIALYSE", jour).orElseThrow().taux());
        assertEquals(new BigDecimal("150.00"), forfaitsAbsence.tauxTva(CENTRE_A, jour));
        IllegalArgumentException refus = assertThrows(IllegalArgumentException.class,
                () -> fiscalFacturation.findActiveAt(CENTRE_A, "HEMODIALYSE", jour));
        assertEquals("Le taux TVA ne peut pas dépasser 100%", refus.getMessage());
        Resp liste = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(java.net.URI.create("/api/v1/tva-types?centerId=" + CENTRE_A)).with(adminA()));
        golden("taux_superieur_a_100_liste_des_types_de_la_facturation", liste);
    }
}
