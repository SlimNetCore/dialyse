package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsFournis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link ForfaitsFournis} : le forfait imposé sur la séance (colonnes {@code forfait_override_*}
 * de {@code seances}, SE5) prime ; sinon celui de la prise en charge qui couvre le jour (effective d'abord, sinon demandée, la plus
 * récente d'abord) ; sinon la plus récente prise en charge qui porte un forfait. Mêmes requêtes que le legacy (parité).
 */
@Component
public class ForfaitsFournisLegacy implements ForfaitsFournis {

    private static final String SELECTION = """
            SELECT f.id AS forfait_id, f.code AS forfait_code, f.libelle AS forfait_nom, f.prix AS forfait_prix
            FROM prise_en_charge p
            INNER JOIN forfait f ON f.id = COALESCE(p.forfait_effectif_id, p.forfait_demande_id) AND f.center_id = p.center_id
            WHERE p.center_id = ? AND p.patient_id = ?
            """;
    private static final String ORDRE = """
            ORDER BY CASE WHEN p.forfait_effectif_id IS NOT NULL THEN 0 ELSE 1 END, p.created_at DESC
            """;

    private static final RowMapper<Forfait> DE_LA_PRISE_EN_CHARGE = (rs, i) -> new Forfait(rs.getObject("forfait_id", UUID.class),
            rs.getString("forfait_code"), rs.getString("forfait_nom"), rs.getBigDecimal("forfait_prix"), 0, null, null, false);

    private final JdbcTemplate jdbc;

    public ForfaitsFournisLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Forfait> courant(UUID centre, UUID seance, UUID patient, LocalDate jour) {
        Optional<Forfait> impose = jdbc.query("""
                SELECT forfait_override_id, forfait_override_code, forfait_override_nom, forfait_override_prix,
                       forfait_override_updated_at, forfait_override_updated_by
                FROM seances WHERE id = ? AND center_id = ? AND forfait_override_id IS NOT NULL
                """, rs -> rs.next() ? Optional.of(new Forfait(rs.getObject("forfait_override_id", UUID.class),
                rs.getString("forfait_override_code"), rs.getString("forfait_override_nom"), rs.getBigDecimal("forfait_override_prix"),
                null, rs.getObject("forfait_override_updated_at", OffsetDateTime.class), rs.getString("forfait_override_updated_by"),
                true)) : Optional.<Forfait>empty(), seance, centre);
        if (impose.isPresent()) return impose;

        Date date = Date.valueOf(jour);
        Optional<Forfait> couvrant = jdbc.query(SELECTION + """
                  AND ((p.date_debut_effectif IS NOT NULL AND ? >= p.date_debut_effectif
                        AND (? <= p.date_fin_effectif OR p.date_fin_effectif IS NULL))
                    OR (p.date_debut_demande IS NOT NULL AND ? >= p.date_debut_demande
                        AND (? <= p.date_fin_demande OR p.date_fin_demande IS NULL)))
                """ + ORDRE, DE_LA_PRISE_EN_CHARGE, centre, patient, date, date, date, date).stream().findFirst();
        if (couvrant.isPresent()) return couvrant;

        return jdbc.query(SELECTION + " AND COALESCE(p.forfait_effectif_id, p.forfait_demande_id) IS NOT NULL\n" + ORDRE,
                DE_LA_PRISE_EN_CHARGE, centre, patient).stream().findFirst();
    }
}
