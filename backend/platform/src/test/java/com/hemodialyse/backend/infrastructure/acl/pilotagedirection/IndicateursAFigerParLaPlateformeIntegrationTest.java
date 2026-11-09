package com.hemodialyse.backend.infrastructure.acl.pilotagedirection;

import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursAFigerFournis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PD2 (ADR 0103, revue m2) : le port à valeur opaque {@link IndicateursAFigerFournis} ne type pas ce qu'il remet ; ce test garde la FORME du
 * JSON des indicateurs figés dans un instantané : celle de {@code /indicators} (modèle de la plateforme), sérialisée par le même
 * {@code ObjectMapper}. Un changement du modèle des indicateurs modifierait silencieusement les instantanés futurs : il fait échouer ce test.
 * Dates fixes : indépendant de l'heure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class IndicateursAFigerParLaPlateformeIntegrationTest {

    private static final UUID SOCIETE = UUID.fromString("99996100-0000-0000-0000-000000000001");
    private static final UUID CENTRE = UUID.fromString("99996100-0000-0000-0000-0000000000a1");

    @Autowired
    private IndicateursAFigerFournis port;
    @Autowired
    private DirectionIndicatorsQueryService indicateurs;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparer() {
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-IF1", "Société ZT-IF1");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE, "ZT-IF1-C", "Centre IF", SOCIETE);
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-IF1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-IF1%'");
    }

    @Test
    void les_indicateurs_a_figer_ont_la_forme_json_de_la_route_indicators() {
        LocalDate debut = LocalDate.of(2026, 10, 1);
        LocalDate fin = LocalDate.of(2026, 10, 31);

        JsonNode figes = mapper.valueToTree(port.pour(SOCIETE, debut, fin));
        JsonNode attendus = mapper.valueToTree(indicateurs.indicators(SOCIETE, debut, fin));

        List<String> cles = new ArrayList<>();
        figes.propertyNames().forEach(cles::add);
        assertThat(cles).containsExactly("societeId", "from", "to", "generatedAt", "seuilAnonymat", "centres", "totaux", "alertes");
        // l'horodatage de calcul diffère d'un appel à l'autre : tout le reste est identique
        ((tools.jackson.databind.node.ObjectNode) figes).remove("generatedAt");
        ((tools.jackson.databind.node.ObjectNode) attendus).remove("generatedAt");
        assertThat(figes).isEqualTo(attendus);
        assertThat(figes.path("centres")).hasSize(1);
        assertThat(figes.path("centres").get(0).path("nom").asString()).isEqualTo("Centre IF");
        assertThat(figes.path("totaux").path("clinique").isObject()).isTrue();
        assertThat(figes.path("totaux").path("stock").isObject()).isTrue();
    }
}
