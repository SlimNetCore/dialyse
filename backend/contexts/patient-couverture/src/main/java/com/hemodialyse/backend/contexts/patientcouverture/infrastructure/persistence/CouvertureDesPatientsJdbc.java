package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter du port publié {@link CouvertureDesPatients} sur {@code prise_en_charge}, {@code attestation_droit} et {@code forfait} :
 * les requêtes d'Absences (forfait applicable), de Facturation (prises en charge tarifées) et de Séance (éligibilité à la
 * facturation, PEC validée), reprises À L'IDENTIQUE (PC5, comportement inchangé).
 */
public class CouvertureDesPatientsJdbc implements CouvertureDesPatients {

    // ---- forfait applicable (Absences, KB-ABS-16)

    private static final String SELECT_FORFAIT = "SELECT f.id, f.libelle, f.prix FROM prise_en_charge p "
            + "JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id) AND f.center_id = p.center_id "
            + "WHERE p.center_id = ? AND p.patient_id = ? ";
    private static final String ORDRE = "ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END, "
            + "p.created_at DESC LIMIT 1";

    // ---- éligibilité à la facturation (Séance)

    /**
     * Prise en charge avec forfait dont une période couvre la date : la période <b>effective</b> (début et fin ensemble) ou, à
     * défaut, la période <b>demandée</b>. Ne pas mélanger les bornes : une période effective ouverte reste valable.
     */
    private static final String PEC_COUVRANTE = """
            FROM prise_en_charge p
            WHERE p.center_id = ?
              AND p.patient_id = ?
              AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL
              AND (
                    (p.date_debut_effectif IS NOT NULL AND ? >= p.date_debut_effectif
                        AND (? <= p.date_fin_effectif OR p.date_fin_effectif IS NULL))
                 OR (p.date_debut_demande IS NOT NULL AND ? >= p.date_debut_demande
                        AND (? <= p.date_fin_demande OR p.date_fin_demande IS NULL))
              )
            """;
    private static final String VALIDEE = " AND p.statut = 'VALIDEE' ";
    private static final String ATTESTATION_COUVRANTE = """
              AND EXISTS (
                  SELECT 1
                  FROM attestation_droit a
                  WHERE a.center_id = p.center_id
                    AND a.patient_id = p.patient_id
                    AND ? >= a.date_debut
                    AND (? <= a.date_fin OR a.date_fin IS NULL)
              )
            """;

    private final JdbcTemplate jdbc;

    public CouvertureDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static ForfaitApplicable forfait(UUID id, String libelle, java.math.BigDecimal prix) {
        return new ForfaitApplicable(id, libelle, prix);
    }

    @Override
    public Optional<ForfaitApplicable> forfaitApplicable(UUID centre, UUID patient, LocalDate date) {
        Date jour = Date.valueOf(date);
        Optional<ForfaitApplicable> couvrant = jdbc.query(SELECT_FORFAIT
                        + "AND ((p.date_debut_effectif IS NOT NULL AND ? >= p.date_debut_effectif "
                        + "AND (? <= p.date_fin_effectif OR p.date_fin_effectif IS NULL)) "
                        + "OR (p.date_debut_demande IS NOT NULL AND ? >= p.date_debut_demande "
                        + "AND (? <= p.date_fin_demande OR p.date_fin_demande IS NULL))) " + ORDRE,
                (rs, i) -> forfait(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3)),
                centre, patient, jour, jour, jour, jour).stream().findFirst();
        if (couvrant.isPresent()) return couvrant;
        return jdbc.query(SELECT_FORFAIT + "AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL " + ORDRE,
                (rs, i) -> forfait(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3)),
                centre, patient).stream().findFirst();
    }

    @Override
    public List<PriseEnChargeTarifee> prisesEnChargeTarifees(UUID centre, UUID patient) {
        return jdbc.query("""
                        SELECT p.forfait_effectif_id, f.id AS forfait_id, f.libelle, f.prix,
                               p.date_debut_effectif, p.date_fin_effectif, p.date_debut_demande, p.date_fin_demande, p.created_at
                        FROM prise_en_charge p
                        INNER JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id)
                                             AND f.center_id = p.center_id
                        WHERE p.center_id = ? AND p.patient_id = ?
                        """,
                (rs, ligne) -> {
                    Timestamp creeLe = rs.getTimestamp("created_at");
                    return new PriseEnChargeTarifee(rs.getObject("forfait_id", UUID.class), rs.getString("libelle"),
                            rs.getBigDecimal("prix"), rs.getObject("forfait_effectif_id", UUID.class) != null,
                            jour(rs.getDate("date_debut_effectif")), jour(rs.getDate("date_fin_effectif")),
                            jour(rs.getDate("date_debut_demande")), jour(rs.getDate("date_fin_demande")),
                            creeLe == null ? null : creeLe.toInstant());
                },
                centre, patient);
    }

    @Override
    public Optional<CauseDeNonFacturation> causeDeNonFacturation(UUID centre, UUID patient, LocalDate jour) {
        Date j = Date.valueOf(jour);
        if (compter("SELECT COUNT(1) " + PEC_COUVRANTE, centre, patient, j, j, j, j) == 0) {
            return Optional.of(CauseDeNonFacturation.AUCUNE_PEC_COUVRANTE);
        }
        if (compter("SELECT COUNT(1) " + PEC_COUVRANTE + VALIDEE, centre, patient, j, j, j, j) == 0) {
            return Optional.of(CauseDeNonFacturation.PEC_NON_VALIDEE);
        }
        if (compter("SELECT COUNT(1) " + PEC_COUVRANTE + VALIDEE + ATTESTATION_COUVRANTE, centre, patient, j, j, j, j, j, j) == 0) {
            return Optional.of(CauseDeNonFacturation.ATTESTATION_ABSENTE);
        }
        return Optional.empty();
    }

    @Override
    public boolean pecValidee(UUID centre, UUID patient, UUID pec) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM prise_en_charge WHERE id = ? AND center_id = ? AND patient_id = ? AND statut = 'VALIDEE'",
                Integer.class, pec, centre, patient);
        return n != null && n > 0;
    }

    private long compter(String sql, UUID centre, UUID patient, Object... dates) {
        Object[] args = new Object[2 + dates.length];
        args[0] = centre;
        args[1] = patient;
        System.arraycopy(dates, 0, args, 2, dates.length);
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }

    private static LocalDate jour(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
