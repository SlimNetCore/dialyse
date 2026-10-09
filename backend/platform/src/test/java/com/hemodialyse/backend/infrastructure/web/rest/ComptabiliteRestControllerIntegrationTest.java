package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ComptabiliteRestControllerIntegrationTest {

    private static final UUID CENTER_ID = UUID.fromString("99992000-0000-0000-0000-000000000001");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99992000-0000-0000-0000-000000000002");
    private static final UUID SOURCE_ID = UUID.fromString("99992000-0000-0000-0000-000000000101");
    private static final UUID TIERS_ID = UUID.fromString("99992000-0000-0000-0000-000000000201");
    private static final LocalDate DATE_ECRITURE = LocalDate.of(2026, 8, 15);
    private static final String BASE = "/api/v1/comptabilite";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EcritureComptableRepositoryPort ecritureRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    private static RequestPostProcessor admin(UUID centre) {
        return user(UserPrincipal.create(UUID.randomUUID().toString(), centre.toString(), "admin", "",
                List.of("ADMIN"), true));
    }

    private static String mapping(String compteStock, String journalSortie) {
        return """
                {"compteVentes":"706","compteClientPatient":"411100","compteClientDefaut":"411500",
                 "compteBanque":"512","compteCaisse":"530","compteTVACollectee":"44571",
                 "compteStock":"%s","compteConsommation":"6022","compteFacturesNonParvenues":"408",
                 "compteBoniInventaire":"757","compteMaliInventaire":"657",
                 "journaux":{"STOCK_SORTIE":"%s"}}
                """.formatted(compteStock, journalSortie);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        cleanup();
        seedEcriture();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void search_should_return_saved_ecriture_for_center_and_period() throws Exception {
        mockMvc.perform(get(BASE + "/ecritures")
                        .param("centerId", CENTER_ID.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("page", "0")
                        .param("size", "20")
                        .with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].centerId").value(CENTER_ID.toString()))
                .andExpect(jsonPath("$.items[0].numeroPiece").value("VE-2026-000001"))
                .andExpect(jsonPath("$.items[0].journalCode").value("VE"))
                .andExpect(jsonPath("$.items[0].statut").value("VALIDEE"))
                .andExpect(jsonPath("$.items[0].lignes.length()").value(2));
    }

    @Test
    void search_should_filter_by_statut_and_by_journal_when_requested() throws Exception {
        mockMvc.perform(get(BASE + "/ecritures")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("statut", "VALIDEE")
                        .param("journalCode", "ve")
                        .with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get(BASE + "/ecritures")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("statut", "EXPORTEE")
                        .with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get(BASE + "/ecritures")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("journalCode", "OD")
                        .with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void exporter_should_mark_ecriture_as_exported_without_null_created_at() throws Exception {
        mockMvc.perform(get(BASE + "/ecritures/export")
                        .param("centerId", CENTER_ID.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("journalCode", "VE")
                        .with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("journal-VE-2026-08-01.csv")));

        Integer exportedCount = jdbc.queryForObject(
                "SELECT COUNT(1) FROM ecritures_comptables WHERE center_id = ? AND statut = 'EXPORTEE' AND created_at IS NOT NULL",
                Integer.class,
                CENTER_ID
        );

        assertEquals(1, exportedCount);
    }

    @Test
    void an_administrator_never_reads_nor_configures_the_accounting_of_another_centre() throws Exception {
        // l'écriture existe, mais dans un autre centre que celui de la session
        mockMvc.perform(get(BASE + "/ecritures")
                        .param("centerId", CENTER_ID.toString())
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .with(admin(AUTRE_CENTRE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/ecritures")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .with(admin(AUTRE_CENTRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(BASE + "/mapping").param("centerId", CENTER_ID.toString()).with(admin(AUTRE_CENTRE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/journaux/OD").param("centerId", CENTER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"libelle\":\"OD\",\"actif\":true}")
                        .with(admin(AUTRE_CENTRE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void the_default_parametrisation_is_served_until_the_centre_changes_it() throws Exception {
        mockMvc.perform(get(BASE + "/mapping").with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteStock").value("322"))
                .andExpect(jsonPath("$.compteConsommation").value("602"))
                .andExpect(jsonPath("$.compteFacturesNonParvenues").value("408"))
                .andExpect(jsonPath("$.journaux.VENTE").value("VE"))
                .andExpect(jsonPath("$.journaux.STOCK_RECEPTION").value("AC"))
                .andExpect(jsonPath("$.journaux.STOCK_SORTIE").value("ST"));
        mockMvc.perform(get(BASE + "/journaux").with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items[0].code").value("VE"))
                .andExpect(jsonPath("$.items[0].actif").value(true));
        mockMvc.perform(get(BASE + "/journaux").param("page", "1").param("size", "2").with(admin(CENTER_ID)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items[0].code").value("CA"));
    }

    @Test
    void journals_and_accounts_are_configurable_per_centre() throws Exception {
        // un journal inconnu ne peut pas être choisi pour une opération
        mockMvc.perform(put(BASE + "/mapping").contentType(MediaType.APPLICATION_JSON)
                        .content(mapping("32", "OD")).with(admin(CENTER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("JOURNAL_INCONNU"));

        mockMvc.perform(put(BASE + "/journaux/od").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Opérations diverses\",\"actif\":true}").with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OD"));
        // un compte absent du plan comptable du centre ne peut pas être paramétré : il faut d'abord l'y ajouter
        mockMvc.perform(put(BASE + "/mapping").contentType(MediaType.APPLICATION_JSON)
                        .content(mapping("32", "OD")).with(admin(CENTER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMPTE_INCONNU"));
        for (String compte : List.of("32", "6022")) {
            mockMvc.perform(put(BASE + "/comptes/" + compte).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"libelle\":\"Compte " + compte + "\",\"actif\":true}").with(admin(CENTER_ID)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put(BASE + "/mapping").contentType(MediaType.APPLICATION_JSON)
                        .content(mapping("32", "OD")).with(admin(CENTER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteClientDefaut").value("411500"))
                .andExpect(jsonPath("$.compteStock").value("32"))
                .andExpect(jsonPath("$.journaux.STOCK_SORTIE").value("OD"))
                .andExpect(jsonPath("$.journaux.VENTE").value("VE"));

        // relu tel qu'enregistré ; l'autre centre garde les valeurs par défaut
        mockMvc.perform(get(BASE + "/mapping").with(admin(CENTER_ID)))
                .andExpect(jsonPath("$.compteConsommation").value("6022"))
                .andExpect(jsonPath("$.journaux.STOCK_SORTIE").value("OD"));
        mockMvc.perform(get(BASE + "/mapping").with(admin(AUTRE_CENTRE)))
                .andExpect(jsonPath("$.compteStock").value("322"))
                .andExpect(jsonPath("$.journaux.STOCK_SORTIE").value("ST"));
        mockMvc.perform(get(BASE + "/journaux").with(admin(CENTER_ID))).andExpect(jsonPath("$.total").value(6));
        mockMvc.perform(get(BASE + "/journaux").with(admin(AUTRE_CENTRE))).andExpect(jsonPath("$.total").value(5));

        // utilisé par une opération : ni désactivation ni suppression ; porteur d'écritures : pas de suppression
        mockMvc.perform(delete(BASE + "/journaux/OD").with(admin(CENTER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("JOURNAL_UTILISE"));
        mockMvc.perform(put(BASE + "/journaux/OD").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Opérations diverses\",\"actif\":false}").with(admin(CENTER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("JOURNAL_UTILISE"));
        mockMvc.perform(delete(BASE + "/journaux/CA").with(admin(CENTER_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("JOURNAL_UTILISE"));
        mockMvc.perform(put(BASE + "/mapping").contentType(MediaType.APPLICATION_JSON)
                        .content(mapping("32", "ST")).with(admin(CENTER_ID)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/journaux/OD").with(admin(CENTER_ID))).andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/journaux/ZZ").with(admin(CENTER_ID)))
                .andExpect(jsonPath("$.code").value("JOURNAL_INTROUVABLE"));
    }

    @Test
    void a_secretary_reads_the_journals_but_cannot_change_the_parametrisation() throws Exception {
        RequestPostProcessor secretaire = user(UserPrincipal.create(UUID.randomUUID().toString(), CENTER_ID.toString(),
                "secretaire", "", List.of("SECRETAIRE"), true));

        mockMvc.perform(get(BASE + "/journaux").with(secretaire)).andExpect(status().isOk());
        mockMvc.perform(put(BASE + "/journaux/OD").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"OD\",\"actif\":true}").with(secretaire))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/mapping").with(secretaire)).andExpect(status().isForbidden());
    }

    private void seedEcriture() {
        EcritureComptable ecriture = new EcritureComptable(
                UUID.randomUUID(),
                CENTER_ID,
                JournalCode.VE,
                DATE_ECRITURE,
                DATE_ECRITURE,
                "VE-2026-000001",
                "Fact. FACT-2026-000001 - Patient test",
                List.of(
                        LigneEcriture.debit("411500", "Client", new BigDecimal("1190.00"), TIERS_ID, List.of()),
                        LigneEcriture.credit("706", "Prestation", new BigDecimal("1190.00"), null, List.of())
                ),
                StatutEcriture.VALIDEE,
                SOURCE_ID
        );
        ecritureRepository.save(ecriture);
    }

    private void cleanup() {
        for (UUID centre : List.of(CENTER_ID, AUTRE_CENTRE)) {
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN (SELECT id FROM ecritures_comptables WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM journaux_comptables WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mapping_comptable WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM comptes_comptables WHERE center_id = ?", centre);
        }
    }
}
