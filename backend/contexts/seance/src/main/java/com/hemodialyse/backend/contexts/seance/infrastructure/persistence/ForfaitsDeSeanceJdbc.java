package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.ForfaitDeLaPriseEnCharge;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Forfait courant d'une séance (SE7f, ADR 0100). Le forfait imposé sur la séance (colonnes {@code forfait_override_*}, SE5) prime ; il est lu
 * ici, dans la table de la Séance. Sinon celui de la prise en charge qui couvre le jour (effective d'abord, sinon demandée, la plus récente
 * d'abord), sinon la plus récente prise en charge qui porte un forfait : LEC-PAT sous-slice ii (ARGENT, ADR 0177), cette part est lue par
 * le port {@link ForfaitDeLaPriseEnCharge} (langage publié de Patient et couverture), plus aucun SQL sur {@code prise_en_charge} ni
 * {@code forfait} (butoir 2027-03-31 soldé, entrées des deux gardes supprimées, sans report).
 */
public class ForfaitsDeSeanceJdbc implements ForfaitsDeSeance {

    private final JdbcTemplate jdbc;
    private final ForfaitDeLaPriseEnCharge priseEnCharge;

    public ForfaitsDeSeanceJdbc(JdbcTemplate jdbc, ForfaitDeLaPriseEnCharge priseEnCharge) {
        this.jdbc = jdbc;
        this.priseEnCharge = priseEnCharge;
    }

    @Override
    public Optional<ForfaitCourant> courant(CentreId centre, SeanceId seance, PatientId patient, LocalDate jour) {
        Optional<ForfaitCourant> impose = jdbc.query("""
                SELECT forfait_override_id, forfait_override_code, forfait_override_nom, forfait_override_prix,
                       forfait_override_updated_at, forfait_override_updated_by
                FROM seances WHERE id = ? AND center_id = ? AND forfait_override_id IS NOT NULL
                """, rs -> rs.next() ? Optional.of(new ForfaitCourant(rs.getObject("forfait_override_id", UUID.class),
                rs.getString("forfait_override_code"), rs.getString("forfait_override_nom"), rs.getBigDecimal("forfait_override_prix"),
                null, rs.getObject("forfait_override_updated_at", OffsetDateTime.class), rs.getString("forfait_override_updated_by"),
                true)) : Optional.<ForfaitCourant>empty(), seance.valeur(), centre.valeur());
        if (impose.isPresent()) return impose;
        return priseEnCharge.duJour(centre, patient, jour);
    }
}
