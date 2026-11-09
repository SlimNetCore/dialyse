package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.application.LecturesDesIndicateursCliniques;
import com.hemodialyse.backend.contexts.dossiermedical.application.LecturesDesIndicateursCliniques.Cible;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.IndicateursCliniquesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.PorteursDUnCatheterJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.StatistiquesMedicalesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.TraitementDeLAnemieJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.TraitementDeLAnemiePublie;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des lectures reprises de la plateforme par DM9 (ADR 0101) sur les tables réelles, H2 et PostgreSQL 16 : porteurs d'un cathéter
 * ({@code ContraintesDesPatientsJdbc:34-37}), statistiques médicales ({@code PatientStatsQueryService}), indicateurs cliniques
 * ({@code DirectionIndicatorsQueryService}), traitement de l'anémie ({@code DirectionBreakdownQueryService:217-224}). Mêmes cas que les
 * goldens DM5 {@code planification}, DM8 {@code stats} et {@code direction}, DM9 {@code pediatrie}, {@code egalite_de_dates},
 * {@code un_centre_masque}, et que le rejeu M2 à M6 de {@code dossier-medical-dm9-pg16-rejeu.sh}.
 */
abstract class LecturesReprisesDeLaPlateformeContrat {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000d9a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000d9b2");
    static final LocalDate J = LocalDate.of(2026, 4, 10);
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    static final String[] TABLES = {"abords_vasculaires", "resultats_analyses", "prescriptions_medicales", "serologies_patient",
            "alertes_observance", "bilans_pre_greffe", "administrations_anemie"};

    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (String table : TABLES) {
            for (Object centre : new Object[]{A, B}) {
                jdbc().update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    private static UUID patient(int n) {
        return new UUID(0xD9, n);
    }

    private void abord(UUID centre, UUID patient, String type, LocalDate fin, boolean actif) {
        jdbc().update("INSERT INTO abords_vasculaires (id, patient_id, center_id, type_abord, date_creation, date_fin, actif, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), patient, centre, type, Date.valueOf(J.minusDays(30)),
                fin == null ? null : Date.valueOf(fin), actif, T0);
    }

    private void analyse(UUID centre, UUID patient, LocalDate date, String hb, String ktv, String ferritine) {
        jdbc().update("INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, hb_g_dl, kt_v_mensuel, ferritine_ng_ml, "
                        + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), patient, centre, Date.valueOf(date),
                hb == null ? null : new BigDecimal(hb), ktv == null ? null : new BigDecimal(ktv), ferritine == null ? null : new BigDecimal(ferritine),
                T0, T0);
    }

    private void prescription(UUID centre, UUID patient, LocalDate date, String epoDose, String poidsSec, OffsetDateTime maj) {
        jdbc().update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, epo_dose_ui, epo_voie, poids_sec_cible_kg, "
                        + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), patient, centre, Date.valueOf(date),
                epoDose == null ? null : Integer.valueOf(epoDose), epoDose == null ? null : "SC", poidsSec == null ? null : new BigDecimal(poidsSec),
                T0, maj);
    }

    private void serologie(UUID centre, UUID patient, String marqueur, String resultat, LocalDate prelevement) {
        jdbc().update("INSERT INTO serologies_patient (id, center_id, patient_id, marqueur, resultat, date_prelevement, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), centre, patient, marqueur, resultat, Date.valueOf(prelevement), T0, T0);
    }

    private void alerte(UUID centre, UUID patient, String type, boolean resolue) {
        jdbc().update("INSERT INTO alertes_observance (id, center_id, patient_id, type_alerte, type_traitement, periode_debut, periode_fin, "
                        + "doses_attendues, doses_administrees, created_at, resolved_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), centre,
                patient, type, "EPO", Date.valueOf(J.minusDays(7)), Date.valueOf(J), 3, 1, T0, resolue ? T0 : null);
    }

    private void bilan(UUID centre, UUID patient, String statut, LocalDate greffe) {
        jdbc().update("INSERT INTO bilans_pre_greffe (id, patient_id, center_id, statut, date_greffe, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), patient, centre, statut, greffe == null ? null : Date.valueOf(greffe), T0, T0);
    }

    private void administration(UUID centre, UUID patient, String type, boolean administree, LocalDate jour) {
        jdbc().update("INSERT INTO administrations_anemie (id, patient_id, center_id, type_traitement, date_administration, administree, created_at) "
                + "VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), patient, centre, type, Date.valueOf(jour), administree, T0);
    }

    // ------------------------------------------------------------------ cathéters (M2)

    @Test
    void les_porteurs_d_un_catheter_sont_ceux_d_un_kt_actif_non_termine_au_jour_lus_par_le_centre_de_l_abord() {
        var porteurs = new PorteursDUnCatheterJdbc(jdbc());
        assertThat(porteurs.porteursDUnCatheter(A, J)).isEmpty();
        abord(A, patient(1), "KT_TUNNELISE", null, true);
        abord(A, patient(2), "KT_AIGU", J.minusDays(1), true);
        abord(A, patient(3), "KT_AIGU", J, true);
        abord(A, patient(4), "FAV", null, true);
        abord(A, patient(5), "KT_TUNNELISE", null, false);
        abord(B, patient(6), "KT_TUNNELISE", null, true);
        abord(A, patient(1), "KT_AIGU", null, true);
        assertThat(porteurs.porteursDUnCatheter(A, J)).containsExactlyInAnyOrder(patient(1), patient(3));
        assertThat(porteurs.porteursDUnCatheter(A, J.minusDays(1))).containsExactlyInAnyOrder(patient(1), patient(2), patient(3));
        assertThat(porteurs.porteursDUnCatheter(B, J)).containsExactly(patient(6));
    }

    // ------------------------------------------------------------------ statistiques (M6)

    @Test
    void les_moyennes_sont_celles_de_la_periode_au_centre_et_valent_zero_sans_donnee() {
        StatistiquesMedicalesPubliees stats = new StatistiquesMedicalesJdbc(jdbc());
        assertThat(stats.moyennes(A, patient(1), null, null)).isEqualTo(new StatistiquesMedicalesPubliees.Moyennes(0.0, 0.0, 0.0));
        analyse(A, patient(1), J.plusDays(10), "10", "1.2", "100");
        analyse(A, patient(1), J.plusDays(10), "12", null, null);
        analyse(A, patient(1), J.plusDays(20), "14", "1.6", null);
        analyse(A, patient(1), J.plusDays(40), "20", "2", "300");
        analyse(B, patient(1), J.plusDays(10), "99", "9", "999");
        var m = stats.moyennes(A, patient(1), J, J.plusDays(30));
        assertThat(m.hemoglobine()).isEqualTo(12.0);
        assertThat(m.ktV()).isEqualTo(1.4);
        assertThat(m.ferritine()).isEqualTo(100.0);
        assertThat(stats.moyennes(A, patient(1), null, null).hemoglobine()).isEqualTo(14.0);
        assertThat(stats.moyennes(A, patient(1), J.plusDays(40), null).hemoglobine()).isEqualTo(20.0);
        assertThat(stats.moyennes(A, patient(1), null, J.plusDays(10)).hemoglobine()).isEqualTo(11.0);
    }

    @Test
    void les_tendances_et_les_lignes_d_export_sont_servies_par_date_croissante_avec_les_colonnes_du_sgbd() {
        StatistiquesMedicalesPubliees stats = new StatistiquesMedicalesJdbc(jdbc());
        analyse(A, patient(1), J.plusDays(20), "14", "1.6", null);
        analyse(A, patient(1), J.plusDays(10), "10", "1.2", "100");
        analyse(A, patient(1), J.plusDays(10), "12", null, null);
        List<Map<String, Object>> tendance = stats.tendanceDesBilans(A, patient(1), J, J.plusDays(30));
        assertThat(tendance).hasSize(3);
        assertThat(tendance.get(2).get("DATE_PRELEVEMENT")).hasToString(J.plusDays(20).toString());
        assertThat(new BigDecimal(tendance.get(2).get("HB_G_DL").toString())).isEqualByComparingTo("14");
        assertThat(tendance.get(0).keySet()).hasSize(4);
        // KB-MED-109 : les deux lignes du même jour sont servies, dans un ordre laissé au SGBD.
        assertThat(tendance.subList(0, 2)).extracting(r -> new BigDecimal(r.get("HB_G_DL").toString()).intValue()).containsExactlyInAnyOrder(10, 12);
        List<Map<String, Object>> export = stats.lignesDExport(A, patient(1), null, null);
        assertThat(export).hasSize(3);
        assertThat(export.get(0).keySet()).hasSize(6);
        assertThat(export.get(2).get("CALCIUM_MG_DL")).isNull();
        prescription(A, patient(1), J.plusDays(5), "4000", null, T0);
        prescription(A, patient(1), J.plusDays(1), null, "70", T0);
        List<Map<String, Object>> epo = stats.tendanceDesPrescriptionsDEpo(A, patient(1), J, J.plusDays(30));
        assertThat(epo).hasSize(2);
        assertThat(epo.get(0).get("EPO_DOSE_UI")).isNull();
        assertThat(epo.get(1).get("EPO_DOSE_UI")).hasToString("4000");
        assertThat(epo.get(1).keySet()).hasSize(6);
    }

    @Test
    void le_poids_sec_en_vigueur_est_celui_de_la_derniere_prescription_qui_en_porte_datee_au_plus_tard_du_jour() {
        StatistiquesMedicalesPubliees stats = new StatistiquesMedicalesJdbc(jdbc());
        assertThat(stats.poidsSecCibleEnVigueur(A, patient(1), J)).isEmpty();
        prescription(A, patient(1), J.minusDays(10), null, "70", T0);
        prescription(A, patient(1), J.minusDays(5), null, null, T0);
        prescription(A, patient(1), J.minusDays(2), null, "72.5", T0);
        prescription(A, patient(1), J.minusDays(2), null, "73", T0.plusHours(1));
        prescription(A, patient(1), J.plusDays(2), null, "80", T0);
        assertThat(stats.poidsSecCibleEnVigueur(A, patient(1), J)).contains(73.0);
        assertThat(stats.poidsSecCibleEnVigueur(A, patient(1), J.minusDays(3))).contains(70.0);
        assertThat(stats.poidsSecCibleEnVigueur(A, patient(1), J.plusDays(2))).contains(80.0);
        assertThat(stats.poidsSecCibleEnVigueur(B, patient(1), J)).isEmpty();
    }

    // ------------------------------------------------------------------ indicateurs cliniques (M3, M4, M5)

    @Test
    void la_derniere_valeur_de_chaque_patient_sur_la_periode_compte_autant_de_fois_qu_il_a_de_bilans_ce_jour_la() {
        LecturesDesIndicateursCliniques lectures = new IndicateursCliniquesJdbc(jdbc());
        for (int i = 1; i <= 5; i++) analyse(A, patient(i), J, "11", null, null);
        analyse(A, patient(1), J.minusDays(5), "8", null, null);
        analyse(A, patient(6), J.plusDays(1), "9", null, null);
        analyse(A, patient(6), J.plusDays(1), "9.5", null, null);
        analyse(A, patient(7), J.plusDays(2), null, "1.5", null);
        analyse(A, patient(8), J.plusDays(60), "7", null, null);
        for (int i = 1; i <= 3; i++) analyse(B, patient(10 + i), J, "9", null, null);
        var hb = lectures.dernieresValeurs(Cible.HEMOGLOBINE, List.of(A, B), J.minusDays(10), J.plusDays(30));
        assertThat(hb.get(A)).hasSize(7);
        assertThat(hb.get(A).stream().filter(v -> v.compareTo(new BigDecimal("8")) == 0)).isEmpty();
        assertThat(hb.get(B)).hasSize(3);
        assertThat(lectures.dernieresValeurs(Cible.KT_V, List.of(A, B), J.minusDays(10), J.plusDays(30)).get(A)).hasSize(1);
        assertThat(lectures.dernieresValeurs(Cible.PTH, List.of(A), J.minusDays(10), J.plusDays(30))).isEmpty();
    }

    @Test
    void les_porteurs_positifs_comptent_les_patients_distincts_dont_le_dernier_resultat_au_plus_tard_a_la_fin_de_periode_est_positif() {
        LecturesDesIndicateursCliniques lectures = new IndicateursCliniquesJdbc(jdbc());
        for (int i = 1; i <= 5; i++) serologie(A, patient(i), "AG_HBS", "POSITIF", J);
        serologie(A, patient(6), "AG_HBS", "POSITIF", J.minusDays(10));
        serologie(A, patient(6), "AG_HBS", "NEGATIF", J);
        serologie(A, patient(7), "AG_HBS", "POSITIF", J.minusDays(10));
        serologie(A, patient(7), "AG_HBS", "DOUTEUX", J);
        serologie(A, patient(8), "AG_HBS", "NEGATIF", J.minusDays(10));
        serologie(A, patient(8), "AG_HBS", "POSITIF", J.plusDays(10));
        serologie(A, patient(9), "AC_VHC", "POSITIF", J);
        serologie(B, patient(11), "AG_HBS", "POSITIF", J);
        var positifs = lectures.porteursPositifs(MarqueurSerologique.AG_HBS, List.of(A, B), J);
        assertThat(positifs.get(A)).isEqualTo(5L);
        assertThat(positifs.get(B)).isEqualTo(1L);
        assertThat(lectures.porteursPositifs(MarqueurSerologique.AG_HBS, List.of(A), J.plusDays(10)).get(A)).isEqualTo(6L);
        assertThat(lectures.porteursPositifs(MarqueurSerologique.AC_VHC, List.of(A), J).get(A)).isEqualTo(1L);
        assertThat(lectures.porteursPositifs(MarqueurSerologique.VIH_AC, List.of(A), J)).isEmpty();
    }

    @Test
    void l_observance_en_retard_et_les_greffes_sont_comptees_par_centre() {
        LecturesDesIndicateursCliniques lectures = new IndicateursCliniquesJdbc(jdbc());
        alerte(A, patient(1), "RETARD_CONSTATE", false);
        alerte(A, patient(1), "RETARD_CONSTATE", false);
        alerte(A, patient(2), "RETARD_CONSTATE", true);
        alerte(A, patient(3), "SOUS_DOSAGE", false);
        alerte(B, patient(4), "RETARD_CONSTATE", false);
        var retard = lectures.patientsEnRetardDObservance(List.of(A, B));
        assertThat(retard.get(A)).isEqualTo(1L);
        assertThat(retard.get(B)).isEqualTo(1L);
        for (int i = 1; i <= 5; i++) bilan(A, patient(i), "INSCRIT_LISTE_ATTENTE", null);
        bilan(A, patient(6), "BILAN_EN_COURS", null);
        bilan(A, patient(7), "GREFFE_REALISEE", J);
        bilan(A, patient(8), "GREFFE_REALISEE", J.minusDays(400));
        for (int i = 1; i <= 4; i++) bilan(B, patient(10 + i), "INSCRIT_LISTE_ATTENTE", null);
        var greffes = lectures.greffes(List.of(A, B), J.minusDays(30), J.plusDays(30));
        assertThat(greffes.get(A)).isEqualTo(new LecturesDesIndicateursCliniques.Greffes(5, 1, 1));
        assertThat(greffes.get(B)).isEqualTo(new LecturesDesIndicateursCliniques.Greffes(4, 0, 0));
    }

    // ------------------------------------------------------------------ anémie (Pilotage)

    @Test
    void les_administrations_de_l_anemie_sont_comptees_par_centre_sur_la_periode_bornes_incluses() {
        TraitementDeLAnemiePublie anemie = new TraitementDeLAnemieJdbc(jdbc());
        assertThat(anemie.parCentre(List.of(A, B), J, J.plusDays(10))).isEmpty();
        assertThat(anemie.parCentre(List.of(), J, J.plusDays(10))).isEmpty();
        administration(A, patient(1), "EPO", true, J);
        administration(A, patient(1), "EPO", true, J.plusDays(3));
        administration(A, patient(2), "EPO", true, J.plusDays(10));
        administration(A, patient(2), "FER_INJECTABLE", true, J.plusDays(5));
        administration(A, patient(3), "EPO", false, J.plusDays(5));
        administration(A, patient(3), "FER_INJECTABLE", false, J.plusDays(5));
        administration(A, patient(4), "EPO", true, J.plusDays(11));
        administration(B, patient(5), "FER_INJECTABLE", true, J);
        var comptes = anemie.parCentre(List.of(A, B), J, J.plusDays(10));
        assertThat(comptes.get(A)).isEqualTo(new TraitementDeLAnemiePublie.AdministrationsDuCentre(2, 1, 3, 1, 2));
        assertThat(comptes.get(B)).isEqualTo(new TraitementDeLAnemiePublie.AdministrationsDuCentre(0, 1, 0, 1, 0));
    }
}
