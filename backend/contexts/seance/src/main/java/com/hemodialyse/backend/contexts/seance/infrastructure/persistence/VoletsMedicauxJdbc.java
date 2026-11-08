package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.ObservationsMedicales;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletMedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Les volets médicaux sur la table RÉELLE {@code volet_medical} (aucune colonne ajoutée). La mise à jour remplace les six observations et
 * {@code updated_at}, jamais {@code created_at}. L'unicité d'un volet par séance est tenue par la base ({@code UNIQUE (seance_id)}, Flyway V1) ET
 * par l'application : la séance est verrouillée ({@code FOR UPDATE}) avant la lecture et l'écriture, ce qui fait suivre deux saisies
 * simultanées au lieu de laisser la seconde échouer sur la contrainte.
 */
public class VoletsMedicauxJdbc implements VoletsMedicaux {

    private final JdbcTemplate jdbc;

    public VoletsMedicauxJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VoletMedical> deLaSeance(CentreId centre, SeanceId seance) {
        return jdbc.query("SELECT id, prescription, tolerance_seance, examen_clinique, resultats_biologiques, ajustements_therapeutiques, "
                        + "conclusion_medicale, created_at, updated_at FROM volet_medical WHERE seance_id = ? AND center_id = ? "
                        + "ORDER BY created_at ASC NULLS LAST, id ASC", (rs, i) -> VoletMedical.restituer(rs.getObject("id", UUID.class), centre, seance,
                        new ObservationsMedicales(rs.getString("prescription"), rs.getString("tolerance_seance"), rs.getString("examen_clinique"),
                                rs.getString("resultats_biologiques"), rs.getString("ajustements_therapeutiques"),
                                rs.getString("conclusion_medicale")),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at"))),
                seance.valeur(), centre.valeur()).stream().findFirst();
    }

    @Override
    public void enregistrer(VoletMedical v) {
        ObservationsMedicales o = v.observations();
        int lignes = jdbc.update("UPDATE volet_medical SET prescription = ?, tolerance_seance = ?, examen_clinique = ?, "
                        + "resultats_biologiques = ?, ajustements_therapeutiques = ?, conclusion_medicale = ?, updated_at = ? "
                        + "WHERE id = ? AND center_id = ?", o.prescription(), o.toleranceDeLaSeance(), o.examenClinique(),
                o.resultatsBiologiques(), o.ajustementsTherapeutiques(), o.conclusionMedicale(), horodatage(v.modifieLe()), v.id(),
                v.centre().valeur());
        if (lignes > 0) return;
        jdbc.update("INSERT INTO volet_medical (id, center_id, seance_id, prescription, tolerance_seance, examen_clinique, "
                        + "resultats_biologiques, ajustements_therapeutiques, conclusion_medicale, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", v.id(), v.centre().valeur(), v.seance().valeur(), o.prescription(),
                o.toleranceDeLaSeance(), o.examenClinique(), o.resultatsBiologiques(), o.ajustementsTherapeutiques(),
                o.conclusionMedicale(), horodatage(v.creeLe()), horodatage(v.modifieLe()));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static OffsetDateTime horodatage(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
