package com.hemodialyse.backend.application.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase.Synchronisation;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comptabilisation du stock sur une vraie base : lecture des bons de réception, des sorties et des inventaires,
 * écritures équilibrées par compte, rejeu sans doublon, mise à jour de l'écriture du jour et isolation par centre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ComptabiliteStockIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99993000-0000-0000-0000-000000000001");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99993000-0000-0000-0000-000000000002");
    private static final UUID SERINGUE = UUID.fromString("99993000-0000-0000-0000-000000000011");
    private static final UUID EPO = UUID.fromString("99993000-0000-0000-0000-000000000012");
    private static final UUID ARTICLE_AUTRE_CENTRE = UUID.fromString("99993000-0000-0000-0000-000000000013");
    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 9);

    @Autowired
    private ComptabiliteStockUseCase comptabiliteStock;
    @Autowired
    private ComptabiliteUseCase comptabilite;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private WebApplicationContext context;

    private static Timestamp instant(LocalDate jour, int heure) {
        return Timestamp.from(jour.atTime(heure, 0).toInstant(ZoneOffset.UTC));
    }

    private static RequestPostProcessor admin(UUID centre) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "admin", "",
                List.of("ADMIN"), true));
    }

    @BeforeEach
    void seed() {
        cleanup();
        article(SERINGUE, CENTRE, "SER-10", null, null, "12.00");
        article(EPO, CENTRE, "EPO-4000", "321", "6021", "2500.00");
        article(ARTICLE_AUTRE_CENTRE, AUTRE_CENTRE, "SER-10", null, null, "9.00");

        // réception validée : 100 seringues à 10 + 4 EPO à 2500 ; un brouillon ne se comptabilise pas
        UUID bon = reception(CENTRE, "BR-2026-0001", LocalDate.of(2026, 9, 3), "VALIDE");
        ligneReception(bon, SERINGUE, "100", "10.00");
        ligneReception(bon, EPO, "4", "2500.00");
        ligneReception(reception(CENTRE, "BR-2026-0002", LocalDate.of(2026, 9, 4), "BROUILLON"), SERINGUE, "5", "10.00");
        ligneReception(reception(AUTRE_CENTRE, "BR-2026-0001", LocalDate.of(2026, 9, 3), "VALIDE"),
                ARTICLE_AUTRE_CENTRE, "7", "9.00");

        // sorties du 5 : 20 seringues au PMP appliqué 10, 1 EPO sans prix (PMP après mouvement) ; sortie du 6
        mouvement(CENTRE, SERINGUE, "SORTIE", "20", "10.00", null, instant(LocalDate.of(2026, 9, 5), 9));
        mouvement(CENTRE, EPO, "SORTIE", "1", null, "2500.00", instant(LocalDate.of(2026, 9, 5), 15));
        mouvement(CENTRE, SERINGUE, "SORTIE", "3", "10.00", null, instant(LocalDate.of(2026, 9, 6), 9));
        mouvement(CENTRE, SERINGUE, "ENTREE", "100", "10.00", null, instant(LocalDate.of(2026, 9, 3), 9));
        mouvement(AUTRE_CENTRE, ARTICLE_AUTRE_CENTRE, "SORTIE", "2", "9.00", null, instant(LocalDate.of(2026, 9, 5), 9));

        // inventaire clôturé : 2 seringues en trop (PMP 10), 1 EPO manquante (PMP 2500) ; un inventaire en cours est ignoré
        UUID inventaire = inventaire(CENTRE, "INV-2026-0001", LocalDate.of(2026, 9, 20), "CLOTURE");
        ligneInventaire(inventaire, CENTRE, 1, SERINGUE, "77", "79", "10.00");
        ligneInventaire(inventaire, CENTRE, 2, EPO, "3", "2", "2500.00");
        ligneInventaire(inventaire(CENTRE, "INV-2026-0002", LocalDate.of(2026, 9, 25), "EN_COURS"), CENTRE, 1, SERINGUE,
                "79", "70", "10.00");
    }

    @AfterEach
    void cleanup() {
        for (UUID centre : List.of(CENTRE, AUTRE_CENTRE)) {
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN (SELECT id FROM ecritures_comptables WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM inventaire_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM bons_reception_lignes WHERE bon_reception_id IN (SELECT id FROM bons_reception WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM bons_reception WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", centre);
        }
    }

    @Test
    void receptions_outputs_and_inventories_are_posted_with_the_right_accounts() {
        Synchronisation resultat = comptabiliteStock.synchroniser(CENTRE, DEBUT, FIN, AUJOURDHUI);

        assertEquals(new Synchronisation(1, 2, 1, 0, 0), resultat);
        // réception : stock (322 centre, 321 article) / factures non parvenues
        assertEquals(new BigDecimal("1000.00"), solde("Réception BR-2026-0001", "322"));
        assertEquals(new BigDecimal("10000.00"), solde("Réception BR-2026-0001", "321"));
        assertEquals(new BigDecimal("-11000.00"), solde("Réception BR-2026-0001", "408"));
        // sorties du 5 : consommation (602 centre, 6021 article) / stock
        assertEquals(new BigDecimal("200.00"), solde("Sorties de stock du 05/09/2026", "602"));
        assertEquals(new BigDecimal("2500.00"), solde("Sorties de stock du 05/09/2026", "6021"));
        assertEquals(new BigDecimal("-200.00"), solde("Sorties de stock du 05/09/2026", "322"));
        assertEquals(new BigDecimal("-2500.00"), solde("Sorties de stock du 05/09/2026", "321"));
        assertEquals(new BigDecimal("30.00"), solde("Sorties de stock du 06/09/2026", "602"));
        // inventaire : boni 20, mali 2500
        assertEquals(new BigDecimal("20.00"), solde("Écarts d'inventaire INV-2026-0001", "322"));
        assertEquals(new BigDecimal("-2500.00"), solde("Écarts d'inventaire INV-2026-0001", "321"));
        assertEquals(new BigDecimal("-20.00"), solde("Écarts d'inventaire INV-2026-0001", "757"));
        assertEquals(new BigDecimal("2500.00"), solde("Écarts d'inventaire INV-2026-0001", "657"));

        assertEquals("AC-2026-000001", numero("Réception BR-2026-0001"));
        assertEquals(List.of("ST-2026-000001", "ST-2026-000002", "ST-2026-000003"), jdbc.queryForList(
                "SELECT numero_piece FROM ecritures_comptables WHERE center_id = ? AND journal_code = 'ST' ORDER BY numero_piece",
                String.class, CENTRE));
        assertEquals(0, ecritures(AUTRE_CENTRE), "le stock d'un autre centre n'est jamais comptabilisé ici");
        assertEquals(0, new BigDecimal("0.00").compareTo(jdbc.queryForObject(
                "SELECT SUM(l.montant_debit) - SUM(l.montant_credit) FROM lignes_ecriture l JOIN ecritures_comptables e "
                        + "ON e.id = l.ecriture_id WHERE e.center_id = ?", BigDecimal.class, CENTRE)));
    }

    @Test
    void replaying_creates_nothing_and_a_late_movement_updates_the_entry_of_its_day() {
        comptabiliteStock.synchroniser(CENTRE, DEBUT, FIN, AUJOURDHUI);
        int avant = ecritures(CENTRE);
        String numeroDuJour = numero("Sorties de stock du 06/09/2026");

        assertEquals(new Synchronisation(0, 0, 0, 0, 0), comptabiliteStock.synchroniser(CENTRE, DEBUT, FIN, AUJOURDHUI));
        assertEquals(avant, ecritures(CENTRE));

        mouvement(CENTRE, SERINGUE, "SORTIE", "5", "10.00", null, instant(LocalDate.of(2026, 9, 6), 18));
        Synchronisation apres = comptabiliteStock.synchroniser(CENTRE, DEBUT, FIN, AUJOURDHUI);

        assertEquals(new Synchronisation(0, 1, 0, 0, 0), apres);
        assertEquals(avant, ecritures(CENTRE), "l'écriture du jour est mise à jour, pas doublée");
        assertEquals(new BigDecimal("80.00"), solde("Sorties de stock du 06/09/2026", "602"));
        assertEquals(numeroDuJour, numero("Sorties de stock du 06/09/2026"), "le numéro de pièce est conservé");
    }

    @Test
    void once_the_period_is_closed_a_late_movement_goes_to_a_complement_dated_today() {
        comptabiliteStock.synchroniser(CENTRE, DEBUT, FIN, AUJOURDHUI);
        comptabilite.cloturerPeriode(new ComptabiliteUseCase.CloturerPeriodeCommand(CENTRE, YearMonth.of(2026, 9), "admin"));

        mouvement(CENTRE, SERINGUE, "SORTIE", "5", "10.00", null, instant(LocalDate.of(2026, 9, 6), 18));
        Synchronisation apres = comptabiliteStock.synchroniser(CENTRE, DEBUT, FIN, AUJOURDHUI);

        assertEquals(new Synchronisation(0, 0, 0, 1, 0), apres);
        assertEquals(new BigDecimal("30.00"), solde("Sorties de stock du 06/09/2026", "602"), "écriture clôturée intacte");
        assertEquals(new BigDecimal("50.00"), solde("Complément sorties de stock du 06/09/2026", "602"));
        assertEquals(AUJOURDHUI, jdbc.queryForObject(
                "SELECT date_ecriture FROM ecritures_comptables WHERE center_id = ? AND libelle = ?", LocalDate.class,
                CENTRE, "Complément sorties de stock du 06/09/2026"));
    }

    @Test
    void the_endpoint_posts_the_stock_of_the_session_centre_only() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String corps = "{\"centerId\":\"%s\",\"from\":\"2026-09-01\",\"to\":\"2026-09-30\"}";

        mockMvc.perform(post("/api/v1/comptabilite/stock/synchroniser").contentType(MediaType.APPLICATION_JSON)
                        .content(corps.formatted(CENTRE)).with(admin(AUTRE_CENTRE)))
                .andExpect(status().isForbidden());
        assertEquals(0, ecritures(CENTRE));

        mockMvc.perform(post("/api/v1/comptabilite/stock/synchroniser").contentType(MediaType.APPLICATION_JSON)
                        .content(corps.formatted(CENTRE)).with(admin(CENTRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receptions").value(1))
                .andExpect(jsonPath("$.joursSorties").value(2))
                .andExpect(jsonPath("$.inventaires").value(1));
        mockMvc.perform(post("/api/v1/comptabilite/stock/synchroniser").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2024-01-01\",\"to\":\"2026-09-30\"}").with(admin(CENTRE)))
                .andExpect(status().isBadRequest());
    }

    private int ecritures(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ?", Integer.class, centre);
    }

    private String numero(String libelle) {
        return jdbc.queryForObject("SELECT numero_piece FROM ecritures_comptables WHERE center_id = ? AND libelle = ?",
                String.class, CENTRE, libelle);
    }

    /**
     * Solde (débit − crédit) d'un compte dans l'écriture du centre portant ce libellé.
     */
    private BigDecimal solde(String libelle, String compte) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(l.montant_debit), 0) - COALESCE(SUM(l.montant_credit), 0) "
                        + "FROM lignes_ecriture l JOIN ecritures_comptables e ON e.id = l.ecriture_id "
                        + "WHERE e.center_id = ? AND e.libelle = ? AND l.compte_scf = ?", BigDecimal.class,
                CENTRE, libelle, compte).setScale(2);
    }

    private void article(UUID id, UUID centre, String code, String compteStock, String compteCharge, String pmp) {
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, "
                        + "gere_par_lot, active, pmp_courant, compte_stock, compte_charge) "
                        + "VALUES (?, ?, ?, ?, 'u', 0, 0, FALSE, TRUE, ?, ?, ?)",
                id, centre, code, code, new BigDecimal(pmp), compteStock, compteCharge);
    }

    private UUID reception(UUID centre, String reference, LocalDate date, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, date_reception, statut) VALUES (?, ?, ?, ?, ?)",
                id, centre, reference, date, statut);
        return id;
    }

    private void ligneReception(UUID bon, UUID article, String quantite, String prix) {
        jdbc.update("INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire) "
                + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), bon, article, new BigDecimal(quantite), new BigDecimal(prix));
    }

    private void mouvement(UUID centre, UUID article, String type, String quantite, String prix, String pmpApres,
                           Timestamp quand) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, "
                        + "pmp_apres, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, article, type, new BigDecimal(quantite),
                prix == null ? null : new BigDecimal(prix), pmpApres == null ? null : new BigDecimal(pmpApres), quand);
    }

    private UUID inventaire(UUID centre, String reference, LocalDate date, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, centre, reference, date, statut, instant(date, 8));
        return id;
    }

    private void ligneInventaire(UUID inventaire, UUID centre, int position, UUID article, String theorique,
                                 String comptee, String pmp) {
        jdbc.update("INSERT INTO inventaire_lignes (id, inventaire_id, center_id, position_ligne, article_id, "
                        + "quantite_theorique, quantite_comptee, pmp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), inventaire, centre, position, article, new BigDecimal(theorique),
                new BigDecimal(comptee), new BigDecimal(pmp));
    }
}
