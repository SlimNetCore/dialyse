package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort.RefItem;
import com.hemodialyse.backend.domain.referential.port.ReferentialUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administration des référentiels : saisie, import CSV vérifié, cloisonnement par centre, droits, cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ReferentialAdminIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99995000-0000-0000-0000-0000000000a1");
    private static final UUID CENTRE_B = UUID.fromString("99995000-0000-0000-0000-0000000000b1");
    private static final String BASE = "/api/v1/admin/referentials";
    // Les générateurs ne sont plus un référentiel administrable génériquement ici (module GMAO v2) :
    // ils sont gérés comme des équipements GMAO via /gmao/equipements.
    private static final List<String> TABLES = List.of("salle", "centre_payeur", "agence",
            "caisse_assurance", "forfait", "position_creneau", "medecin", "transporteur");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReferentialUseCase referentials;
    private MockMvc mockMvc;

    private static UserPrincipal principal(String role, UUID centerId) {
        return UserPrincipal.create(UUID.randomUUID().toString(), centerId.toString(), "zt-ref-" + role, "",
                List.of(role), true);
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
    }

    @AfterEach
    void cleanup() {
        for (String table : TABLES) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        }
    }

    @Test
    void describesTheEightReferentialsInImportOrder() throws Exception {
        // Depuis le module GMAO v2, les générateurs ne sont plus un référentiel administrable
        // génériquement ici (8 référentiels au lieu de 9) : voir /gmao/equipements.
        mockMvc.perform(get(BASE).with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].importOrder").value(1))
                .andExpect(jsonPath("$[7].slug").value("centres-payeurs"));
    }

    @Test
    void createsUpdatesAndSearchesWithReferencesResolvedByCode() throws Exception {
        mockMvc.perform(post(BASE + "/caisses").with(user(principal("ADMIN", CENTRE_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"code\":\"CNAS\",\"nom\":\"Caisse nationale\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.values.typeCaisse").value("STANDARD"));

        String agence = mockMvc.perform(post(BASE + "/agences").with(user(principal("ADMIN", CENTRE_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"code\":\"AG1\",\"nom\":\"Agence Alger\",\"caisse\":\"cnas\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.references.caisse").value("CNAS · Caisse nationale"))
                .andReturn().getResponse().getContentAsString();
        String agenceId = agence.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(put(BASE + "/agences/{id}", agenceId).with(user(principal("ADMIN", CENTRE_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"code\":\"AG1\",\"nom\":\"Agence Alger Centre\",\"caisse\":\"CNAS\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.nom").value("Agence Alger Centre"));

        mockMvc.perform(get(BASE + "/agences").param("search", "alger").with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].values.code").value("AG1"));
    }

    // invalidInputReturnsFieldIssues et importChecksTheFileThenImportsIt (validation + import en masse du
    // référentiel "generateurs") sont retirés avec le référentiel générique GENERATEUR (module GMAO v2) —
    // compromis assumé : la validation par référence et le rapport d'import restent couverts par
    // ReferentialAdminDomainServiceTest sur un autre référentiel (AGENCE → CAISSE) ; l'import en masse des
    // équipements n'a pas d'équivalent pour l'instant (voir le plan GMAO v2, section Risques).

    @Test
    void deleteIsRefusedWhileAChildStillReferencesTheRow() throws Exception {
        UUID caisse = UUID.randomUUID();
        UUID agence = UUID.randomUUID();
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, 'CNAS', 'CNAS', 'STANDARD')", caisse, CENTRE_A);
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, 'AG1', 'Agence 1')", agence, CENTRE_A, caisse);

        mockMvc.perform(delete(BASE + "/caisses/{id}", caisse).with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REFERENTIAL_IN_USE"));

        mockMvc.perform(delete(BASE + "/agences/{id}", agence).with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/caisses/{id}", caisse).with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isNoContent());
        assertThat(count("caisse_assurance", CENTRE_A)).isZero();
    }

    @Test
    void anAdminOnlySeesAndChangesItsOwnCenter() throws Exception {
        UUID salleB = UUID.randomUUID();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, 'SB', 'Salle B')", salleB, CENTRE_B);

        mockMvc.perform(get(BASE + "/salles").param("centerId", CENTRE_B.toString())
                        .with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/salles").with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(delete(BASE + "/salles/{id}", salleB).with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REFERENTIAL_NOT_FOUND"));
        assertThat(count("salle", CENTRE_B)).isEqualTo(1);
    }

    @Test
    void nonAdminRolesAreRefused() throws Exception {
        mockMvc.perform(get(BASE + "/salles").with(user(principal("INFIRMIER", CENTRE_A))))
                .andExpect(status().isForbidden());
    }

    @Test
    void writesEvictTheReferentialCacheOfTheCenterOnly() throws Exception {
        CenterId centreA = CenterId.of(CENTRE_A);
        CenterId centreB = CenterId.of(CENTRE_B);
        jdbc.update("INSERT INTO transporteur (id, center_id, nom) VALUES (?, ?, 'Transport B')", UUID.randomUUID(), CENTRE_B);
        assertThat(referentials.transporteurs(centreA)).isEmpty();          // mis en cache (vide)
        assertThat(referentials.transporteurs(centreB)).hasSize(1);         // mis en cache

        mockMvc.perform(post(BASE + "/transporteurs").with(user(principal("ADMIN", CENTRE_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"nom\":\"Ambulances Rouiba\",\"telephone\":\"0795 00 61 36\"}}"))
                .andExpect(status().isCreated());

        assertThat(referentials.transporteurs(centreA)).extracting(RefItem::nom).containsExactly("Ambulances Rouiba");
        // Le cache du centre B n'est pas touché : une écriture directe en base n'y apparaît pas.
        jdbc.update("INSERT INTO transporteur (id, center_id, nom) VALUES (?, ?, 'Transport B2')", UUID.randomUUID(), CENTRE_B);
        assertThat(referentials.transporteurs(centreB)).hasSize(1);
    }

    @Test
    void downloadsImportTemplates() throws Exception {
        mockMvc.perform(get(BASE + "/forfaits/template").param("format", "csv").with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("Code;Libellé;Prix"));
        mockMvc.perform(get(BASE + "/forfaits/template").with(user(principal("ADMIN", CENTRE_A))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/vnd.openxmlformats")));
    }

    private long count(String table, UUID centerId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Long.class, centerId);
        return n == null ? 0 : n;
    }
}



