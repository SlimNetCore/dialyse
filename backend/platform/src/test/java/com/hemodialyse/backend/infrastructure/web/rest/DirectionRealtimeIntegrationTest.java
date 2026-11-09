package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.contexts.pilotagedirection.application.EnregistrerLesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HorlogeDuPilotage;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SuivreLeTempsReel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Changement;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.tableaudebord.ConstatsDuTableauDeBordParLeTableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.diffusion.DiffusionParLeCanalFourni;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.HistoriqueDesAlertesJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.organisation.SocieteDUnCentrePublie;
import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.AnnuaireDesSocietesJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.DiffusionDuTableauDeBordFournie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.DiffusionDuTableauDeBordFournie.TableauDeBordModifie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Temps réel du tableau de bord : tout changement d'un indicateur affiché est détecté et diffusé, uniquement pour
 * les sociétés dont la direction est connectée, sans jamais transmettre de donnée nominative.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DirectionRealtimeIntegrationTest {

    private static final UUID SOC = UUID.fromString("99993000-0000-0000-0000-000000000001");
    private static final UUID CENTRE = UUID.fromString("99993000-0000-0000-0000-0000000000a1");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TableauDeBordDeLaDirection tableau;
    @Autowired
    private IndicateursDeLaDirection indicators;
    /**
     * Suivi isolé du contexte (diffusion capturée) : pas de bean simulé, donc pas de second contexte Spring.
     */
    private SuivreLeTempsReel realtime;
    private DiffusionDuTableauDeBordFournie port;

    private static List<String> names(List<Changement> changes) {
        return changes.stream().map(Changement::nom).toList();
    }

    @BeforeEach
    void setup() {
        port = mock(DiffusionDuTableauDeBordFournie.class);
        HorlogeDuPilotage horloge = new HorlogeDuPilotage(java.time.Clock.systemUTC());
        realtime = new SuivreLeTempsReel(new ConstatsDuTableauDeBordParLeTableauDeBord(tableau, indicators),
                new EnregistrerLesAlertes(new HistoriqueDesAlertesJdbc(jdbc), horloge), new SocieteDUnCentrePublie(new AnnuaireDesSocietesJdbc(jdbc)),
                new DiffusionParLeCanalFourni(port), horloge);
        cleanup();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOC, "ZT-RT1", "Société RT");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE, "ZT-RT1-A", "Centre RT", SOC);
    }

    @AfterEach
    void cleanup() {
        realtime.abonneRetire(SOC);
        for (String table : List.of("facture_reglements", "factures", "seances", "articles", "patients")) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", CENTRE);
        }
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-RT%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-RT%'");
    }

    @Test
    void a_change_of_any_displayed_indicator_is_detected_and_published() {
        realtime.abonneAjoute(SOC);
        assertTrue(realtime.rafraichir(SOC).isEmpty(), "premier calcul : simple instantané de référence");
        verify(port, never()).publish(any(), any());

        UUID patient = patient();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), patient, CENTRE, Date.valueOf(LocalDate.now()), "VALIDEE");
        Date today = Date.valueOf(LocalDate.now());
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), CENTRE, patient, "ZT-RT-F1", today, today, today, new BigDecimal("19.00"),
                new BigDecimal("1000"), new BigDecimal("190"), new BigDecimal("1190"));

        List<Changement> changes = realtime.rafraichir(SOC);
        assertTrue(names(changes).contains("seances"));
        assertTrue(names(changes).contains("caTtc"));
        assertTrue(names(changes).contains("factures"));
        assertTrue(changes.stream().allMatch(c -> "Centre RT".equals(c.centre())));

        ArgumentCaptor<TableauDeBordModifie> event = ArgumentCaptor.forClass(TableauDeBordModifie.class);
        verify(port, times(1)).publish(eq(SOC), event.capture());
        assertEquals("DASHBOARD_CHANGED", event.getValue().type());
        assertEquals(SOC, event.getValue().societeId());
        assertFalse(event.getValue().changes().isEmpty());

        // rien n'a changé depuis : aucune nouvelle diffusion
        reset(port);
        assertTrue(realtime.rafraichir(SOC).isEmpty());
        verify(port, never()).publish(any(), any());
    }

    @Test
    void a_new_alert_is_reported_as_a_change() {
        realtime.abonneAjoute(SOC);
        realtime.rafraichir(SOC);
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, gere_par_lot, active) "
                        + "VALUES (?,?,?,?,?,?,?,TRUE,TRUE)",
                UUID.randomUUID(), CENTRE, "ZT-RT-ART", "Article", "U", new BigDecimal("1"), new BigDecimal("10"));
        List<Changement> changes = realtime.rafraichir(SOC);
        Changement raised = changes.stream().filter(c -> "STOCK_SOUS_SEUIL".equals(c.nom())).findFirst().orElseThrow();
        assertEquals("ALERTES", raised.famille());
        assertEquals(0, raised.avant().signum());
    }

    @Test
    void only_watched_societes_are_signalled_and_recomputed() {
        // aucune direction connectée : le signal est ignoré, rien n'est recalculé ni publié
        realtime.unCentreAChange(CENTRE);
        realtime.traiterLesSignalements();
        verify(port, never()).publish(any(), any());
        assertFalse(realtime.estSuivie(SOC));

        realtime.abonneAjoute(SOC);
        assertTrue(realtime.estSuivie(SOC));
        realtime.abonneRetire(SOC);
        assertFalse(realtime.estSuivie(SOC));
    }

    @Test
    void a_signal_from_a_centre_triggers_the_recomputation_of_its_societe() {
        realtime.abonneAjoute(SOC);
        realtime.traiterLesSignalements();                  // baseline (marquée à l'abonnement)
        UUID patient = patient(); // un seul patient : effectif masqué, donc aucune variation publiable en soi
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), patient, CENTRE, Date.valueOf(LocalDate.now()), "VALIDEE");
        realtime.unCentreAChange(CENTRE);      // un événement applicatif du centre
        realtime.traiterLesSignalements();
        ArgumentCaptor<TableauDeBordModifie> event = ArgumentCaptor.forClass(TableauDeBordModifie.class);
        verify(port, times(1)).publish(eq(SOC), event.capture());
        assertTrue(event.getValue().changes().stream().anyMatch(c -> "seances".equals(c.name())));
    }

    @Test
    void a_masked_headcount_never_leaks_through_a_change() {
        realtime.abonneAjoute(SOC);
        realtime.rafraichir(SOC);
        patient();
        patient(); // 2 patients : sous le seuil d'anonymat
        assertTrue(realtime.rafraichir(SOC).stream().noneMatch(c -> c.famille().equals("PATIENTS")),
                "aucune variation d'effectif inférieur au seuil ne doit être diffusée");
    }

    private UUID patient() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, qualite_assure, sous_kt, epo_enabled, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP)",
                id, CENTRE, "ZT-" + id.toString().substring(0, 8), "NOM", "Prenom", "M", Date.valueOf("1970-01-01"),
                "ASS-" + id.toString().substring(0, 8), Date.valueOf("2026-01-02"), "NON_VACANCIER", "PERMANENT", "ASSURE", false);
        return id;
    }
}
