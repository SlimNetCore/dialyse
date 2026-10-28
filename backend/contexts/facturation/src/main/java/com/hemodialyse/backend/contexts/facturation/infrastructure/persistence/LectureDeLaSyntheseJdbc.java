package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Caisse;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Repartition;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lecture JDBC des données de la synthèse de facturation : caisses, répartitions et indicateurs des factures de la période,
 * totaux enregistrés (HT, TVA, TTC). Contrairement au tableau de bord, les jointures du référentiel filtrent par centre
 * (KB-FAC-65 reproduit). Valeurs vides ou blanches de caisse et de situation : « INCONNU ».
 */
public class LectureDeLaSyntheseJdbc implements LectureDeLaSynthese {

    private final JdbcTemplate jdbc;

    public LectureDeLaSyntheseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SyntheseMensuelle de(CentreId centre, LocalDate debut, LocalDate fin) {
        UUID id = centre.valeur();
        Date du = Date.valueOf(debut);
        Date au = Date.valueOf(fin);

        List<Caisse> caisses = jdbc.query("""
                SELECT DISTINCT
                       COALESCE(NULLIF(TRIM(ca.code), ''), 'INCONNU') AS caisse_code,
                       COALESCE(NULLIF(TRIM(ca.nom), ''), 'Inconnu') AS caisse_label
                FROM factures f
                LEFT JOIN agence a ON a.id = f.agence_id_snapshot AND a.center_id = f.center_id
                LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id AND ca.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                ORDER BY caisse_label ASC
                """,
                (rs, i) -> new Caisse(rs.getString("caisse_code"), rs.getString("caisse_label")), id, du, au);

        List<Repartition> parCaisse = jdbc.query("""
                SELECT COALESCE(NULLIF(TRIM(ca.code), ''), 'INCONNU') AS bucket_code,
                       COALESCE(NULLIF(TRIM(ca.nom), ''), 'Inconnu') AS bucket_label,
                       COUNT(DISTINCT f.patient_id) AS patients_count,
                       COUNT(DISTINCT s.id) AS seances_count
                FROM factures f
                LEFT JOIN agence a ON a.id = f.agence_id_snapshot AND a.center_id = f.center_id
                LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id AND ca.center_id = f.center_id
                LEFT JOIN seances s ON s.facture_id = f.id AND s.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                GROUP BY COALESCE(NULLIF(TRIM(ca.code), ''), 'INCONNU'),
                         COALESCE(NULLIF(TRIM(ca.nom), ''), 'Inconnu')
                ORDER BY patients_count DESC, seances_count DESC, bucket_code ASC
                """,
                (rs, i) -> repartition(rs.getString("bucket_code"), rs.getString("bucket_label"), rs.getLong("patients_count"),
                        rs.getLong("seances_count")), id, du, au);

        List<Repartition> parSituation = jdbc.query("""
                SELECT COALESCE(NULLIF(TRIM(f.patient_status_snapshot), ''), 'INCONNU') AS bucket_code,
                       COALESCE(NULLIF(TRIM(f.patient_status_snapshot), ''), 'Inconnu') AS bucket_label,
                       COUNT(DISTINCT f.patient_id) AS patients_count,
                       COUNT(DISTINCT s.id) AS seances_count
                FROM factures f
                LEFT JOIN seances s ON s.facture_id = f.id AND s.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                GROUP BY COALESCE(NULLIF(TRIM(f.patient_status_snapshot), ''), 'INCONNU'),
                         COALESCE(NULLIF(TRIM(f.patient_status_snapshot), ''), 'Inconnu')
                ORDER BY patients_count DESC, seances_count DESC, bucket_code ASC
                """,
                (rs, i) -> repartition(rs.getString("bucket_code"), rs.getString("bucket_label"), rs.getLong("patients_count"),
                        rs.getLong("seances_count")), id, du, au);

        Indicateurs indicateurs = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT f.id) AS total_factures,
                       COUNT(DISTINCT f.patient_id) AS total_patients,
                       COUNT(DISTINCT s.id) AS total_seances
                FROM factures f
                LEFT JOIN seances s ON s.facture_id = f.id AND s.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                """,
                (rs, i) -> new Indicateurs(rs.getLong("total_factures"), rs.getLong("total_patients"), rs.getLong("total_seances")),
                id, du, au);

        Totaux totaux = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_ht), 0) AS ht, COALESCE(SUM(total_tva), 0) AS tva, COALESCE(SUM(total_ttc), 0) AS ttc
                FROM factures
                WHERE center_id = ?
                  AND date_facturation BETWEEN ? AND ?
                """,
                (rs, i) -> new Totaux(rs.getBigDecimal("ht"), rs.getBigDecimal("tva"), rs.getBigDecimal("ttc")), id, du, au);

        return new SyntheseMensuelle(id, debut, fin, caisses, parCaisse, parSituation, indicateurs.factures(),
                indicateurs.patients(), indicateurs.seances(), totaux.ht(), totaux.tva(), totaux.ttc());
    }

    private static Repartition repartition(String code, String libelle, long patients, long seances) {
        return new Repartition(code, libelle, patients, seances);
    }

    private record Indicateurs(long factures, long patients, long seances) {
    }

    private record Totaux(BigDecimal ht, BigDecimal tva, BigDecimal ttc) {
    }
}
