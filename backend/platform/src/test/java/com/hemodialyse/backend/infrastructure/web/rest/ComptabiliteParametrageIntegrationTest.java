package com.hemodialyse.backend.infrastructure.web.rest;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paramétrage comptable sans développement, sur une vraie base : plan comptable, compte client par payeur, modèles de
 * pièces, saisie et extourne — avec l'isolement par centre de chaque opération.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ComptabiliteParametrageIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99994000-0000-0000-0000-000000000001");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99994000-0000-0000-0000-000000000002");
    private static final UUID CNAS = UUID.fromString("99994000-0000-0000-0000-000000000011");
    private static final UUID MUTUELLE = UUID.fromString("99994000-0000-0000-0000-000000000012");
    private static final UUID PAYEUR_AUTRE_CENTRE = UUID.fromString("99994000-0000-0000-0000-000000000013");
    private static final String BASE = "/api/v1/comptabilite";
    private static final String LOYER = json("""
            {'code':'loyer','libelle':'Loyer du centre','journal':'AC','actif':true,'lignes':[
              {'sens':'DEBIT','compte':'613','libelle':'Loyer'},
              {'sens':'DEBIT','compte':'4456'},
              {'sens':'CREDIT','compte':'401'}]}
            """);
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ComptabiliteUseCase comptabilite;
    private MockMvc mockMvc;

    private static RequestPostProcessor role(UUID centre, String role) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "u", "", List.of(role), true));
    }

    private static RequestPostProcessor admin(UUID centre) {
        return role(centre, "ADMIN");
    }

    private static String json(String corps) {
        return corps.replace('\'', '"');
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        payeur(CNAS, CENTRE, "CNAS-16", "CNAS Alger");
        payeur(MUTUELLE, CENTRE, "MUT-1", "Mutuelle X");
        payeur(PAYEUR_AUTRE_CENTRE, AUTRE_CENTRE, "CNAS-31", "CNAS Oran");
    }

    @AfterEach
    void cleanup() {
        for (UUID centre : List.of(CENTRE, AUTRE_CENTRE)) {
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN (SELECT id FROM ecritures_comptables WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM modeles_piece_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM modeles_piece WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_payeurs WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM journaux_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mapping_comptable WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM centre_payeur WHERE center_id = ?", centre);
        }
    }

    private void payeur(UUID id, UUID centre, String code, String nom) {
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", id, centre,
                UUID.randomUUID(), code, nom);
    }

    private BigDecimal solde(String compte) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(l.montant_debit), 0) - COALESCE(SUM(l.montant_credit), 0) "
                + "FROM lignes_ecriture l JOIN ecritures_comptables e ON e.id = l.ecriture_id "
                + "WHERE e.center_id = ? AND l.compte_scf = ?", BigDecimal.class, CENTRE, compte).setScale(2);
    }

    private String idModele() {
        return jdbc.queryForObject("SELECT CAST(id AS VARCHAR(36)) FROM modeles_piece WHERE center_id = ? AND code = 'LOYER'",
                String.class, CENTRE);
    }

    @Test
    void the_chart_of_accounts_is_paged_searchable_and_scoped_to_the_centre() throws Exception {
        mockMvc.perform(get(BASE + "/comptes").param("size", "5").with(admin(CENTRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(20))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].numero").value("322"));
        mockMvc.perform(get(BASE + "/comptes").param("recherche", "banq").with(admin(CENTRE)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].numero").value("512"));

        mockMvc.perform(put(BASE + "/comptes/411210").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'libelle':'Clients — CNAS','actif':true}")).with(admin(CENTRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("411210"));
        mockMvc.perform(get(BASE + "/comptes").param("recherche", "CNAS").with(admin(CENTRE)))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get(BASE + "/comptes").param("recherche", "411210").with(admin(AUTRE_CENTRE)))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(BASE + "/comptes").param("centerId", CENTRE.toString()).with(admin(AUTRE_CENTRE)))
                .andExpect(status().isForbidden());

        // utilisé par le paramétrage : ni suppression ni désactivation ; libre : supprimé
        mockMvc.perform(delete(BASE + "/comptes/706").with(admin(CENTRE)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("COMPTE_UTILISE"));
        mockMvc.perform(put(BASE + "/comptes/512").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'libelle':'Banque','actif':false}")).with(admin(CENTRE)))
                .andExpect(jsonPath("$.code").value("COMPTE_UTILISE"));
        mockMvc.perform(delete(BASE + "/comptes/626").with(admin(CENTRE))).andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/comptes/626").with(admin(CENTRE)))
                .andExpect(jsonPath("$.code").value("COMPTE_INTROUVABLE"));
        mockMvc.perform(put(BASE + "/comptes/41-1").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'libelle':'x','actif':true}")).with(admin(CENTRE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_new_client_is_only_a_payer_with_its_account_and_invoices_follow() throws Exception {
        mockMvc.perform(get(BASE + "/payeurs").with(admin(CENTRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].nom").value("CNAS Alger"))
                .andExpect(jsonPath("$.items[0].compte").doesNotExist());

        // compte hors plan refusé ; payeur d'un autre centre introuvable
        mockMvc.perform(put(BASE + "/payeurs/" + CNAS + "/compte").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'compte':'411210'}")).with(admin(CENTRE)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("COMPTE_INCONNU"));
        mockMvc.perform(put(BASE + "/payeurs/" + PAYEUR_AUTRE_CENTRE + "/compte").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'compte':'411500'}")).with(admin(CENTRE)))
                .andExpect(jsonPath("$.code").value("PAYEUR_INTROUVABLE"));

        mockMvc.perform(put(BASE + "/comptes/411210").contentType(MediaType.APPLICATION_JSON)
                .content(json("{'libelle':'Clients — CNAS','actif':true}")).with(admin(CENTRE)));
        mockMvc.perform(put(BASE + "/payeurs/" + CNAS + "/compte").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'compte':'411210'}")).with(admin(CENTRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compte").value("411210"))
                .andExpect(jsonPath("$.code").value("CNAS-16"));
        mockMvc.perform(get(BASE + "/payeurs").param("recherche", "cnas").with(admin(CENTRE)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].compte").value("411210"));

        // facture du payeur paramétré, d'un payeur sans compte, d'un patient qui paie lui-même
        for (UUID payeur : new UUID[]{CNAS, MUTUELLE, null}) {
            comptabilite.genererEcritureFacturation(new ComptabiliteUseCase.GenererEcritureFacturationCommand(CENTRE,
                    UUID.randomUUID(), "FACT-" + payeur, UUID.randomUUID(), payeur, new BigDecimal("1000.00"),
                    BigDecimal.ZERO, new BigDecimal("1000.00"), LocalDate.of(2026, 10, 5), "Séances"));
        }
        assertEquals(new BigDecimal("1000.00"), solde("411210"));
        assertEquals(new BigDecimal("1000.00"), solde("411500"));
        assertEquals(new BigDecimal("1000.00"), solde("411100"));

        // un compte porté par un payeur ne se désactive pas ; vide = retour au compte par défaut
        mockMvc.perform(put(BASE + "/comptes/411210").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'libelle':'Clients — CNAS','actif':false}")).with(admin(CENTRE)))
                .andExpect(jsonPath("$.code").value("COMPTE_UTILISE"));
        mockMvc.perform(put(BASE + "/payeurs/" + CNAS + "/compte").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'compte':''}")).with(admin(CENTRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compte").doesNotExist());
    }

    @Test
    void a_new_kind_of_piece_is_a_model_and_pieces_are_entered_then_reversed() throws Exception {
        mockMvc.perform(post(BASE + "/modeles").contentType(MediaType.APPLICATION_JSON).content(LOYER)
                        .with(admin(CENTRE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LOYER"))
                .andExpect(jsonPath("$.lignes.length()").value(3))
                .andExpect(jsonPath("$.lignes[0].libelle").value("Loyer"));
        mockMvc.perform(post(BASE + "/modeles").contentType(MediaType.APPLICATION_JSON).content(LOYER)
                        .with(admin(CENTRE)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MODELE_CODE_EXISTANT"));
        mockMvc.perform(get(BASE + "/modeles").with(admin(CENTRE))).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get(BASE + "/modeles").with(admin(AUTRE_CENTRE))).andExpect(jsonPath("$.total").value(0));
        String modele = idModele();

        // le journal et les comptes d'un modèle sont protégés
        mockMvc.perform(delete(BASE + "/journaux/AC").with(admin(CENTRE)))
                .andExpect(jsonPath("$.code").value("JOURNAL_UTILISE"));
        mockMvc.perform(delete(BASE + "/comptes/613").with(admin(CENTRE)))
                .andExpect(jsonPath("$.code").value("COMPTE_UTILISE"));

        // saisie par le secrétariat : montants seulement
        String piece = json("{'modeleId':'%s','date':'2026-10-05','libelle':'Loyer octobre','montants':[100000,19000,119000]}"
                .formatted(modele));
        mockMvc.perform(post(BASE + "/pieces").contentType(MediaType.APPLICATION_JSON).content(piece)
                        .with(role(CENTRE, "SECRETAIRE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroPiece").value("AC-2026-000001"))
                .andExpect(jsonPath("$.journalCode").value("AC"))
                .andExpect(jsonPath("$.total").value(119000.00));
        assertEquals(new BigDecimal("100000.00"), solde("613"));
        assertEquals(new BigDecimal("19000.00"), solde("4456"));
        assertEquals(new BigDecimal("-119000.00"), solde("401"));

        // déséquilibrée, ou depuis un autre centre : refusée
        mockMvc.perform(post(BASE + "/pieces").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'modeleId':'%s','date':'2026-10-05','montants':[100,0,90]}".formatted(modele)))
                        .with(admin(CENTRE)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ECRITURE_DESEQUILIBREE"));
        mockMvc.perform(post(BASE + "/pieces").contentType(MediaType.APPLICATION_JSON).content(piece)
                        .with(admin(AUTRE_CENTRE)))
                .andExpect(jsonPath("$.code").value("MODELE_INTROUVABLE"));

        // la pièce apparaît dans le journal, marquée « saisie » ; elle s'extourne une fois, par l'administrateur
        mockMvc.perform(get(BASE + "/ecritures").param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("journalCode", "AC").with(admin(CENTRE)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].saisie").value(true));
        String ecriture = jdbc.queryForObject("SELECT CAST(id AS VARCHAR(36)) FROM ecritures_comptables "
                + "WHERE center_id = ? AND numero_piece = 'AC-2026-000001'", String.class, CENTRE);
        mockMvc.perform(post(BASE + "/pieces/" + ecriture + "/extourne").with(role(CENTRE, "SECRETAIRE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/pieces/" + ecriture + "/extourne").with(admin(AUTRE_CENTRE)))
                .andExpect(jsonPath("$.code").value("ECRITURE_INTROUVABLE"));
        mockMvc.perform(post(BASE + "/pieces/" + ecriture + "/extourne").with(admin(CENTRE)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE + "/pieces/" + ecriture + "/extourne").with(admin(CENTRE)))
                .andExpect(jsonPath("$.code").value("PIECE_DEJA_EXTOURNEE"));
        assertEquals(new BigDecimal("0.00"), solde("613"));
        assertEquals(new BigDecimal("0.00"), solde("401"));

        // modèle modifié puis supprimé ; ses pièces demeurent
        mockMvc.perform(put(BASE + "/modeles/" + modele).contentType(MediaType.APPLICATION_JSON)
                        .content(LOYER.replace("Loyer du centre", "Loyer mensuel")).with(admin(CENTRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Loyer mensuel"));
        mockMvc.perform(delete(BASE + "/modeles/" + modele).with(admin(CENTRE))).andExpect(status().isNoContent());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ?",
                Integer.class, CENTRE));
    }

    @Test
    void only_an_administrator_changes_the_parametrisation() throws Exception {
        RequestPostProcessor secretaire = role(CENTRE, "SECRETAIRE");

        mockMvc.perform(get(BASE + "/comptes").with(secretaire)).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/modeles").with(secretaire)).andExpect(status().isOk());
        mockMvc.perform(put(BASE + "/comptes/999").contentType(MediaType.APPLICATION_JSON)
                .content(json("{'libelle':'x','actif':true}")).with(secretaire)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/modeles").contentType(MediaType.APPLICATION_JSON).content(LOYER).with(secretaire))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/payeurs").with(secretaire)).andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/payeurs/" + CNAS + "/compte").contentType(MediaType.APPLICATION_JSON)
                .content(json("{'compte':'411500'}")).with(secretaire)).andExpect(status().isForbidden());
    }
}
