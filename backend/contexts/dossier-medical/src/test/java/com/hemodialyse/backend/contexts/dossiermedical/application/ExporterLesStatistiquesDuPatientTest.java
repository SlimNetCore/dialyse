package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.FormatDExport;
import com.hemodialyse.backend.contexts.dossiermedical.domain.FormatDExportInconnu;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeInversee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.hemodialyse.backend.contexts.dossiermedical.application.FixtureDesStatistiques.CENTRE;
import static com.hemodialyse.backend.contexts.dossiermedical.application.FixtureDesStatistiques.PATIENT;
import static com.hemodialyse.backend.contexts.dossiermedical.application.FixtureDesStatistiques.ligne;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PL4 : l'export CSV ou PDF des statistiques d'un patient. Ordre des gardes : centre (403), periode (400 INVALID_DATE_RANGE), format
 * (400 INVALID_EXPORT_FORMAT), patient (404), puis lecture. Le CSV a six colonnes entre guillemets doubles ; le PDF recoit les deux volets.
 */
class ExporterLesStatistiquesDuPatientTest {

    private final FixtureDesStatistiques f = new FixtureDesStatistiques();
    private final AtomicReference<StatistiquesParamedicales> paramedicalesImprimees = new AtomicReference<>();
    private final AtomicReference<StatistiquesMedicales> medicalesImprimees = new AtomicReference<>();
    private final ImpressionDesStatistiquesDuPatient impression = (centre, patient, periode, paramedicales, medicales) -> {
        f.appels.add("impression");
        paramedicalesImprimees.set(paramedicales);
        medicalesImprimees.set(medicales);
        return new byte[]{'%', 'P', 'D', 'F'};
    };
    private final ExporterLesStatistiquesDuPatient exporter =
            new ExporterLesStatistiquesDuPatient(f.session, f.patients, f.lectureDesSeances, f.medicales, f.horloge, impression);

    @Test
    void should_exporter_un_csv_de_six_colonnes_entre_guillemets_avec_son_en_tete() {
        f.exportBilans = List.of(
                ligne("DATE_PRELEVEMENT", "2026-01-10", "HB_G_DL", 10, "KT_V_MENSUEL", null, "FERRITINE_NG_ML", 300, "PHOSPHORE_MG_DL", 5.5,
                        "CALCIUM_MG_DL", "9\"x"),
                ligne("DATE_PRELEVEMENT", "2026-02-10"));

        ExportDesStatistiques export = exporter.executer(null, PATIENT, null, null, " CSV ");

        assertThat(export.format()).isEqualTo(FormatDExport.CSV);
        assertThat(export.csv()).isEqualTo("date_prelevement,hb_g_dl,kt_v_mensuel,ferritine_ng_ml,phosphore_mg_dl,calcium_mg_dl\n"
                + "\"2026-01-10\",\"10\",,\"300\",\"5.5\",\"9\"\"x\"\n"
                + "\"2026-02-10\",,,,,\n");
    }

    @Test
    void should_rendre_l_en_tete_seul_sans_donnee() {
        ExportDesStatistiques export = exporter.executer(null, PATIENT, null, null, "csv");

        assertThat(export.csv()).isEqualTo("date_prelevement,hb_g_dl,kt_v_mensuel,ferritine_ng_ml,phosphore_mg_dl,calcium_mg_dl\n");
    }

    @Test
    void should_imprimer_les_deux_volets_pour_un_pdf() {
        f.moyennes = new com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees.Moyennes(11.0, 1.2, 300.0);

        ExportDesStatistiques export = exporter.executer(CENTRE.valeur(), PATIENT, LocalDate.of(2026, 1, 1), null, "pdf");

        assertThat(export.format()).isEqualTo(FormatDExport.PDF);
        assertThat(export.pdf()).startsWith("%PDF".getBytes());
        assertThat(paramedicalesImprimees.get()).isNotNull();
        assertThat(medicalesImprimees.get().hemoglobine()).isEqualTo(11.0);
    }

    @Test
    void should_garder_le_centre_puis_la_periode_puis_le_format_puis_le_patient() {
        assertThatThrownBy(() -> exporter.executer(UUID.randomUUID(), PATIENT, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), "xml"))
                .isInstanceOf(AccesRefuse.class);
        assertThat(f.appels).containsExactly("session");

        f.appels.clear();
        assertThatThrownBy(() -> exporter.executer(null, PATIENT, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), "xml"))
                .isInstanceOf(PeriodeInversee.class);

        assertThatThrownBy(() -> exporter.executer(null, UUID.randomUUID(), null, null, "xml")).isInstanceOf(FormatDExportInconnu.class);
        assertThatThrownBy(() -> exporter.executer(null, PATIENT, null, null, null)).isInstanceOf(FormatDExportInconnu.class);
        assertThat(f.appels).containsOnly("session");

        assertThatThrownBy(() -> exporter.executer(null, UUID.randomUUID(), null, null, "csv")).isInstanceOf(PatientIntrouvable.class);
        assertThat(f.appels).doesNotContain("export", "impression", "seances");
    }
}
