package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesuresDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.horodatage;
import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.instant;

/**
 * Analyses sur la table réelle {@code resultats_analyses} (aucune colonne ni contrainte ajoutée ; {@code created_at} et {@code updated_at}
 * peuvent être nuls pour un bilan importé). Aucune énumération en base : aucune ligne n'est illisible. Verrou : {@code SELECT ... FOR
 * UPDATE} de la ligne, gardé jusqu'à la fin de la transaction ; un verrou non obtenu dans le délai (SQLSTATE 55P03) devient
 * {@link EcritureConcurrenteSurUneAnalyse}. Les lecteurs (suivi de l'anémie {@code BilansBiologiquesJdbc}, KDIGO, statistiques, FHIR) lisent
 * la même table, inchangée.
 */
public class AnalysesJdbc implements Analyses {
    private static final String MESURES = "hb_g_dl, ht_pct, plaquettes, ferritine_ng_ml, cstf_pct, epo_endogene_mui_ml, uree_pre_mg_dl, "
            + "uree_post_mg_dl, creatinine_mg_dl, kt_v_mensuel, phosphore_mg_dl, calcium_mg_dl, pth_pg_ml, albumine_g_dl, proteines_g_dl, crp_mg_l";
    private static final String COLONNES = "id, center_id, patient_id, date_prelevement, " + MESURES + ", created_at, updated_at";
    private static final String ORDRE = " ORDER BY date_prelevement DESC, created_at DESC, id DESC";

    private final JdbcTemplate jdbc;

    public AnalysesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static List<Object> mesures(MesuresDuBilan m) {
        return new ArrayList<>(java.util.Arrays.asList(m.hemoglobine(), m.hematocrite(), m.plaquettes(), m.ferritine(), m.coefficientDeSaturation(),
                m.epoEndogene(), m.ureeAvantSeance(), m.ureeApresSeance(), m.creatinine(), m.ktVMensuel(), m.phosphore(), m.calcium(), m.pth(),
                m.albumine(), m.proteines(), m.crp()));
    }

    @Override
    public void ajouter(Analyse a) {
        List<Object> valeurs = new ArrayList<>(List.of(a.id(), a.centre().valeur(), a.patient().valeur(), a.datePrelevement()));
        valeurs.addAll(mesures(a.mesures()));
        valeurs.add(horodatage(a.creeLe()));
        valeurs.add(horodatage(a.modifieLe()));
        jdbc.update("INSERT INTO resultats_analyses (" + COLONNES + ") VALUES (" + "?,".repeat(21) + "?)", valeurs.toArray());
    }

    @Override
    public void enregistrerLeRemplacement(Analyse a) {
        List<Object> valeurs = new ArrayList<>();
        valeurs.add(a.datePrelevement());
        valeurs.addAll(mesures(a.mesures()));
        valeurs.add(horodatage(a.creeLe()));
        valeurs.add(horodatage(a.modifieLe()));
        valeurs.add(a.id());
        valeurs.add(a.centre().valeur());
        valeurs.add(a.patient().valeur());
        jdbc.update("UPDATE resultats_analyses SET date_prelevement = ?, " + MESURES.replace(", ", " = ?, ") + " = ?, created_at = ?, updated_at = ? "
                + "WHERE id = ? AND center_id = ? AND patient_id = ?", valeurs.toArray());
    }

    @Override
    public Optional<Analyse> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return VerrousJdbc.verrouille(() -> jdbc.query("SELECT " + COLONNES + " FROM resultats_analyses WHERE id = ? AND patient_id = ? AND center_id = ? "
                + "FOR UPDATE", (rs, i) -> analyse(rs), id, patient.valeur(), centre.valeur()).stream().findFirst(),
                EcritureConcurrenteSurUneAnalyse::new);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return jdbc.update("DELETE FROM resultats_analyses WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(),
                centre.valeur()) > 0;
    }

    @Override
    public PageDeFiches<Analyse> duPatient(CentreId centre, PatientId patient, Periode periode, int page, int taille) {
        StringBuilder filtre = new StringBuilder("center_id = ? AND patient_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur(), patient.valeur()));
        if (periode.du() != null) {
            filtre.append(" AND date_prelevement >= ?");
            parametres.add(periode.du());
        }
        if (periode.au() != null) {
            filtre.append(" AND date_prelevement <= ?");
            parametres.add(periode.au());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM resultats_analyses WHERE " + filtre, Long.class, parametres.toArray());
        parametres.add(taille);
        parametres.add((long) page * taille);
        List<Analyse> items = jdbc.query("SELECT " + COLONNES + " FROM resultats_analyses WHERE " + filtre + ORDRE + " LIMIT ? OFFSET ?",
                (rs, i) -> analyse(rs), parametres.toArray());
        return new PageDeFiches<>(items, total == null ? 0 : total, page, taille);
    }

    private static Analyse analyse(ResultSet rs) throws SQLException {
        var mesures = new MesuresDuBilan(rs.getBigDecimal("hb_g_dl"), rs.getBigDecimal("ht_pct"), rs.getObject("plaquettes", Integer.class),
                rs.getBigDecimal("ferritine_ng_ml"), rs.getBigDecimal("cstf_pct"), rs.getBigDecimal("epo_endogene_mui_ml"),
                rs.getBigDecimal("uree_pre_mg_dl"), rs.getBigDecimal("uree_post_mg_dl"), rs.getBigDecimal("creatinine_mg_dl"),
                rs.getBigDecimal("kt_v_mensuel"), rs.getBigDecimal("phosphore_mg_dl"), rs.getBigDecimal("calcium_mg_dl"),
                rs.getBigDecimal("pth_pg_ml"), rs.getBigDecimal("albumine_g_dl"), rs.getBigDecimal("proteines_g_dl"), rs.getBigDecimal("crp_mg_l"));
        return Analyse.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), rs.getObject("date_prelevement", LocalDate.class), mesures,
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }
}
