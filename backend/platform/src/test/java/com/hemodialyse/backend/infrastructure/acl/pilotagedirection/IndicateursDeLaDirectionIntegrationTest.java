package com.hemodialyse.backend.infrastructure.acl.pilotagedirection;

import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PD6 (ADR 0106, reprend les tests des anciens traducteurs de la plateforme PD2, PD3 et PD4) : le Pilotage compose les indicateurs de la
 * direction. Ce test garde (1) la FORME du JSON gravé dans un instantané et servi par {@code /indicators} : ordre exact des clés à chaque
 * niveau (un changement des enregistrements du langage publié modifierait silencieusement les instantanés futurs) ; (2) la traduction
 * champ par champ vers le port du rapport de synthèse. Dates fixes : indépendant de l'heure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class IndicateursDeLaDirectionIntegrationTest {

    private static final UUID SOCIETE = UUID.fromString("99996000-0000-0000-0000-000000000011");
    private static final UUID CENTRE = UUID.fromString("99996000-0000-0000-0000-0000000000b1");
    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 4, 11);

    @Autowired
    private IndicateursDeLaDirection indicateurs;
    @Autowired
    private IndicateursDeLaDirectionFournis rapport;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparer() {
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-RP1", "Société ZT-RP1");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE, "ZT-RP1-C", "Centre RP", SOCIETE);
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot, active) "
                        + "VALUES (?,?,?,?,?,?,?,?,TRUE,TRUE)", UUID.fromString("99996000-0000-0000-0000-0000000000c1"), CENTRE, "ZT-RP1-ART", "Article RP", "U",
                new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("3.50"));
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM articles WHERE code LIKE 'ZT-RP1%'");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-RP1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-RP1%'");
    }

    private static List<String> cles(JsonNode noeud) {
        List<String> cles = new ArrayList<>();
        noeud.propertyNames().forEach(cles::add);
        return cles;
    }

    @Test
    void les_indicateurs_ont_la_forme_json_figee_des_instantanes_et_de_la_route_indicators() {
        JsonNode json = mapper.valueToTree(indicateurs.indicateurs(SOCIETE, DEBUT, FIN));

        assertThat(cles(json)).containsExactly("societeId", "from", "to", "generatedAt", "seuilAnonymat", "centres", "totaux", "alertes");
        assertThat(json.path("seuilAnonymat").asInt()).isEqualTo(5);
        JsonNode centre = json.path("centres").get(0);
        assertThat(json.path("centres")).hasSize(1);
        assertThat(cles(centre)).containsExactly("centerId", "nom", "actif", "clinique", "stock");
        assertThat(cles(centre.path("clinique"))).containsExactly("ktV", "hemoglobine", "phosphore", "pth", "albumine", "vhbPositifs",
                "vhcPositifs", "vihPositifs", "patientsObservanceEnRetard", "greffeListeAttente", "greffeBilanEnCours", "greffesPeriode");
        assertThat(cles(centre.path("clinique").path("ktV"))).containsExactly("evalues", "pctDansCible", "pctSousCible", "pctAuDessus");
        assertThat(cles(centre.path("stock"))).containsExactly("articlesActifs", "articlesSousSeuil", "lotsPerimes", "lotsPeremptionProche",
                "valeurStock");
        assertThat(cles(json.path("totaux"))).containsExactly("centerId", "nom", "actif", "clinique", "stock");
        assertThat(cles(json.path("alertes").get(0))).containsExactly("centerId", "centre", "code", "severity", "valeur");
        assertThat(json.path("totaux").path("centerId").isNull()).isTrue();
        assertThat(json.path("totaux").path("actif").asBoolean()).isTrue();
    }

    @Test
    void le_port_du_rapport_reprend_chaque_champ_des_indicateurs_composes() {
        var attendus = indicateurs.indicateurs(SOCIETE, DEBUT, FIN);
        var traduits = rapport.pour(SOCIETE, DEBUT, FIN);

        assertThat(traduits.centres()).hasSameSizeAs(attendus.centres()).hasSize(1);
        assertThat(traduits.alertes()).hasSameSizeAs(attendus.alertes()).hasSize(1);
        var alerte = traduits.alertes().get(0);
        var alerteAttendue = attendus.alertes().get(0);
        assertThat(alerte.centre()).isEqualTo("Centre RP").isEqualTo(alerteAttendue.centre());
        assertThat(alerte.code()).isEqualTo("STOCK_SOUS_SEUIL").isEqualTo(alerteAttendue.code());
        assertThat(alerte.severity()).isEqualTo(alerteAttendue.severity().name());
        assertThat(alerte.valeur()).isEqualByComparingTo(alerteAttendue.valeur());

        verifier(traduits.centres().get(0), "Centre RP", attendus.centres().get(0));
        verifier(traduits.totaux(), null, attendus.totaux());
        assertThat(traduits.totaux().stock().valeurStock()).isEqualByComparingTo("7.00");
    }

    private static BigDecimal pct(IndicateursDeLaDirection.Marqueur m) {
        return m == null ? null : m.pctDansCible();
    }

    private static void verifier(IndicateursDeLaDirectionFournis.IndicateursDuCentre t, String nom, IndicateursDeLaDirection.IndicateursDuCentre a) {
        assertThat(t.nom()).isEqualTo(nom);
        var ac = a.clinique();
        var tc = t.clinique();
        assertThat(tc.ktV()).isEqualTo(pct(ac.ktV()));
        assertThat(tc.hemoglobine()).isEqualTo(pct(ac.hemoglobine()));
        assertThat(tc.phosphore()).isEqualTo(pct(ac.phosphore()));
        assertThat(tc.pth()).isEqualTo(pct(ac.pth()));
        assertThat(tc.albumine()).isEqualTo(pct(ac.albumine()));
        assertThat(tc.vhbPositifs()).isEqualTo(ac.vhbPositifs());
        assertThat(tc.vhcPositifs()).isEqualTo(ac.vhcPositifs());
        assertThat(tc.vihPositifs()).isEqualTo(ac.vihPositifs());
        assertThat(tc.patientsObservanceEnRetard()).isEqualTo(ac.patientsObservanceEnRetard());
        assertThat(tc.greffeListeAttente()).isEqualTo(ac.greffeListeAttente());
        assertThat(tc.greffeBilanEnCours()).isEqualTo(ac.greffeBilanEnCours());
        assertThat(tc.greffesPeriode()).isEqualTo(ac.greffesPeriode());
        assertThat(t.stock().articlesActifs()).isEqualTo(a.stock().articlesActifs()).isEqualTo(1L);
        assertThat(t.stock().articlesSousSeuil()).isEqualTo(a.stock().articlesSousSeuil()).isEqualTo(1L);
        assertThat(t.stock().lotsPerimes()).isEqualTo(a.stock().lotsPerimes());
        assertThat(t.stock().lotsPeremptionProche()).isEqualTo(a.stock().lotsPeremptionProche());
        assertThat(t.stock().valeurStock()).isEqualTo(a.stock().valeurStock());
    }
}
