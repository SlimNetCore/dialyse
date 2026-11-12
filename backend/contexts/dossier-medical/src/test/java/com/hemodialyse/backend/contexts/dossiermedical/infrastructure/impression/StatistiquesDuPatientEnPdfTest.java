package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.impression;

import com.hemodialyse.backend.contexts.dossiermedical.application.SeancesDuPatient.TensionsDeSeance;
import com.hemodialyse.backend.contexts.dossiermedical.application.StatistiquesMedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.StatistiquesParamedicales;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDeStatistiques;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** PL4 (ex {@code PatientStatsQueryService.exportPdf}) : le PDF des statistiques, HTML identique a celui de la plateforme, valeurs echappees. */
class StatistiquesDuPatientEnPdfTest {

    private final CentreId centre = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private final UUID patient = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private final AtomicReference<String> html = new AtomicReference<>();
    private final AtomicReference<UUID> centreHabille = new AtomicReference<>();

    private final StatistiquesDuPatientEnPdf impression = new StatistiquesDuPatientEnPdf((corps, c) -> {
        html.set(corps);
        centreHabille.set(c);
        return corps;
    }, Clock.fixed(Instant.parse("2026-03-15T10:07:00Z"), ZoneOffset.UTC));

    private static Map<String, Object> ligne(Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) m.put((String) paires[i], paires[i + 1]);
        return m;
    }

    private static StatistiquesParamedicales vide() {
        return new StatistiquesParamedicales(0, 0.0, 0.0, 0.0, null, List.of(), List.<TensionsDeSeance>of());
    }

    private static StatistiquesMedicales sansMesure() {
        return new StatistiquesMedicales(0.0, 0.0, 0.0, List.of(), List.of());
    }

    @Test
    void should_produire_un_pdf_habille_aux_couleurs_du_centre() {
        byte[] pdf = impression.enPdf(centre, patient, new PeriodeDeStatistiques(LocalDate.of(2026, 1, 1), null), vide(), sansMesure());

        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(centreHabille.get()).isEqualTo(centre.valeur());
        assertThat(html.get()).contains("<h1>Rapport statistiques patient</h1>")
                .contains("<div class='muted'>Centre: " + centre.valeur() + " | Patient: " + patient
                        + " | Periode: 2026-01-01 -> - | Genere le: 2026-03-15 10:07</div>");
    }

    @Test
    void should_afficher_les_compteurs_entiers_et_les_moyennes_a_deux_decimales() {
        StatistiquesParamedicales paramedicales = new StatistiquesParamedicales(6, 70.456, 68.0, 1800.5, 66.0, List.of(), List.of());
        StatistiquesMedicales medicales = new StatistiquesMedicales(11.234, 1.3, 300.0, List.of(), List.of());

        impression.enPdf(centre, patient, new PeriodeDeStatistiques(null, null), paramedicales, medicales);

        assertThat(html.get()).contains("<div class='kpi'>Seances: 6</div>")
                .contains("Poids avant moyen (kg): 70.46").contains("Poids apres moyen (kg): 68.00")
                .contains("UF reelle moyenne (mL): 1800.50").contains("Hb moyenne (g/dL): 11.23").contains("Kt/V moyen: 1.30")
                .contains("Ferritine moyenne (ng/mL): 300.00");
    }

    @Test
    void should_afficher_aucune_donnee_dans_les_deux_tableaux_vides() {
        impression.enPdf(centre, patient, new PeriodeDeStatistiques(null, null), vide(), sansMesure());

        assertThat(html.get().split("<tr><td colspan='4'>Aucune donnee</td></tr>", -1)).hasSize(3);
    }

    @Test
    void should_lister_les_poids_et_les_bilans_en_lisant_les_cles_en_minuscules_ou_en_majuscules() {
        StatistiquesParamedicales paramedicales = new StatistiquesParamedicales(1, 70.0, 68.0, 0.0, null,
                List.of(ligne("date_seance", "2026-01-10", "poids_avant_kg", 70.0, "poids_apres_kg", 68.0, "poids_sec_cible_kg", null)), List.of());
        StatistiquesMedicales medicales = new StatistiquesMedicales(11.0, 1.2, 300.0,
                List.of(ligne("DATE_PRELEVEMENT", "2026-01-12", "HB_G_DL", 11.0, "KT_V_MENSUEL", 1.2, "FERRITINE_NG_ML", "<b>300</b>")), List.of());

        impression.enPdf(centre, patient, new PeriodeDeStatistiques(null, null), paramedicales, medicales);

        assertThat(html.get()).contains("<tr><td>2026-01-10</td><td>70.0</td><td>68.0</td><td></td></tr>")
                .contains("<tr><td>2026-01-12</td><td>11.0</td><td>1.2</td><td>&lt;b&gt;300&lt;/b&gt;</td></tr>");
    }
}
