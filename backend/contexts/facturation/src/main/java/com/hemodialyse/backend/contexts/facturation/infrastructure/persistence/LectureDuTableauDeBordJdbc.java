package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois.RepartitionParCaisse;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois.RepartitionParSituation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Lecture JDBC du tableau de bord de facturation (mêmes tables et mêmes requêtes que le legacy, slice F5) : {@code factures}
 * (totaux enregistrés), {@code seances}, {@code agence} et {@code caisse_assurance}. Les jointures du référentiel ne filtrent
 * pas par centre, comme avant (KB-FAC-65).
 */
public class LectureDuTableauDeBordJdbc implements LectureDuTableauDeBord {

    private final JdbcTemplate jdbc;

    public LectureDuTableauDeBordJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TableauDeBordDuMois du(CentreId centre, YearMonth mois) {
        Date debut = Date.valueOf(mois.atDay(1));
        Date fin = Date.valueOf(mois.atEndOfMonth());
        Object[] criteres = {centre.valeur(), debut, fin};

        BigDecimal ttc = valeur("SELECT COALESCE(SUM(total_ttc), 0) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                BigDecimal.class, BigDecimal.ZERO, criteres);
        BigDecimal ht = valeur("SELECT COALESCE(SUM(total_ht), 0) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                BigDecimal.class, BigDecimal.ZERO, criteres);
        Long seances = valeur("SELECT COUNT(1) FROM seances WHERE center_id = ? AND statut = 'FACTUREE' AND date_seance BETWEEN ? AND ?",
                Long.class, 0L, criteres);
        Long patients = valeur("SELECT COUNT(DISTINCT patient_id) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                Long.class, 0L, criteres);
        Long factures = valeur("SELECT COUNT(1) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                Long.class, 0L, criteres);

        List<RepartitionParCaisse> parCaisse = jdbc.query("""
                SELECT COALESCE(ca.code, 'INCONNU') AS insurance_code,
                       COALESCE(ca.nom, 'Inconnu') AS insurance_label,
                       COUNT(DISTINCT s.id) AS seances_count,
                       COUNT(DISTINCT f.patient_id) AS patients_count
                FROM factures f
                LEFT JOIN agence a ON a.id = f.agence_id_snapshot
                LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id
                LEFT JOIN seances s ON s.facture_id = f.id AND s.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                GROUP BY COALESCE(ca.code, 'INCONNU'), COALESCE(ca.nom, 'Inconnu')
                ORDER BY patients_count DESC, insurance_code ASC
                """,
                (rs, i) -> new RepartitionParCaisse(rs.getString("insurance_code"), rs.getString("insurance_label"),
                        rs.getLong("seances_count"), rs.getLong("patients_count")),
                criteres);

        List<RepartitionParSituation> parSituation = jdbc.query("""
                SELECT COALESCE(f.patient_status_snapshot, 'INCONNU') AS status_code,
                       COALESCE(f.patient_status_snapshot, 'Inconnu') AS status_label,
                       COUNT(DISTINCT s.id) AS seances_count
                FROM factures f
                LEFT JOIN seances s ON s.facture_id = f.id AND s.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                GROUP BY COALESCE(f.patient_status_snapshot, 'INCONNU'), COALESCE(f.patient_status_snapshot, 'Inconnu')
                ORDER BY seances_count DESC, status_code ASC
                """,
                (rs, i) -> new RepartitionParSituation(rs.getString("status_code"), rs.getString("status_label"),
                        rs.getLong("seances_count")),
                criteres);

        return new TableauDeBordDuMois(centre.valeur(), mois, ttc, ht, seances, patients, factures, parCaisse, parSituation);
    }

    private <T> T valeur(String sql, Class<T> type, T parDefaut, Object[] criteres) {
        return Optional.ofNullable(jdbc.queryForObject(sql, type, criteres)).orElse(parDefaut);
    }
}
