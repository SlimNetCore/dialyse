package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.FormatDExport;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exporte les statistiques d'un patient du centre de la session en CSV (bilans : six colonnes entre guillemets doubles, en-tete fixe, un seul
 * en-tete sans donnee) ou en PDF (volet paramedical puis volet medical) (PL4). Gardes dans l'ordre : centre (403), periode (400
 * INVALID_DATE_RANGE), format (400 INVALID_EXPORT_FORMAT), patient du centre (404). Droit : ADMIN seul.
 */
public class ExporterLesStatistiquesDuPatient {

    private static final String EN_TETE = "date_prelevement,hb_g_dl,kt_v_mensuel,ferritine_ng_ml,phosphore_mg_dl,calcium_mg_dl";
    private static final List<String> COLONNES = List.of("DATE_PRELEVEMENT", "HB_G_DL", "KT_V_MENSUEL", "FERRITINE_NG_ML", "PHOSPHORE_MG_DL",
            "CALCIUM_MG_DL");

    private final GardeDesStatistiques garde;
    private final SeancesDuPatient seances;
    private final StatistiquesMedicalesPubliees medicales;
    private final HorlogeDuCentre horloge;
    private final ImpressionDesStatistiquesDuPatient impression;

    public ExporterLesStatistiquesDuPatient(SessionCourante session, PatientsDuCentre patients, SeancesDuPatient seances,
                                            StatistiquesMedicalesPubliees medicales, HorlogeDuCentre horloge,
                                            ImpressionDesStatistiquesDuPatient impression) {
        this.garde = new GardeDesStatistiques(session, patients);
        this.seances = seances;
        this.medicales = medicales;
        this.horloge = horloge;
        this.impression = impression;
    }

    public ExportDesStatistiques executer(UUID centreDemande, UUID patient, LocalDate du, LocalDate au, String formatDemande) {
        GardeDesStatistiques.Demande demande = garde.ouvrir(centreDemande, patient, du, au);
        FormatDExport format = FormatDExport.lire(formatDemande);
        garde.exigerLePatient(demande);
        return switch (format) {
            case CSV -> ExportDesStatistiques.enCsv(csv(medicales.lignesDExport(demande.centre().valeur(), demande.patient().valeur(), du, au)));
            case PDF -> ExportDesStatistiques.enPdf(impression.enPdf(demande.centre(), demande.patient().valeur(), demande.periode(),
                    LecturesDesStatistiques.paramedicales(seances, medicales, horloge, demande),
                    LecturesDesStatistiques.medicales(medicales, demande)));
        };
    }

    private static String csv(List<Map<String, Object>> lignes) {
        String corps = lignes.stream()
                .map(ligne -> COLONNES.stream().map(colonne -> entreGuillemets(ligne.get(colonne))).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
        return corps.isBlank() ? EN_TETE + "\n" : EN_TETE + "\n" + corps + "\n";
    }

    private static String entreGuillemets(Object valeur) {
        if (valeur == null) return "";
        return '"' + String.valueOf(valeur).replace("\"", "\"\"") + '"';
    }
}
