package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.MesuresDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletParamedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Les volets paramédicaux sur la table RÉELLE {@code volet_paramedical} (aucune colonne ajoutée ; SE7f, ADR 0100). Lecture et effacement
 * bornés au centre, comme la requête dérivée {@code findBySeanceIdAndCenterId} du legacy : un volet de la même séance posé sous un autre
 * centre est invisible et l'INSERT échoue alors sur {@code UNIQUE (seance_id)} (Flyway V1 ; KB-SEA-94, reproduit). La mise à jour remplace
 * les dix mesures et {@code updated_at}, jamais {@code created_at}. Les bornes des colonnes (varchar 30 et 100) refusent les textes trop
 * longs par une erreur d'accès aux données (reproduit, DM1).
 */
public class VoletsParamedicauxJdbc implements VoletsParamedicaux {

    static final String COLONNES = "id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, duree_minutes, debit_sang_ml_min, "
            + "ultrafiltration_ml, anticoagulant, type_dialysat, incidents, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public VoletsParamedicauxJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<VoletParamedical> deLaSeance(CentreId centre, SeanceId seance) {
        return jdbc.query("SELECT " + COLONNES + " FROM volet_paramedical WHERE seance_id = ? AND center_id = ? "
                        + "ORDER BY created_at ASC NULLS LAST, id ASC",
                (rs, i) -> VoletParamedical.restituer(rs.getObject("id", UUID.class), centre, seance,
                        MesuresDeSeance.restituees(rs.getBigDecimal("poids_avant_kg"), rs.getBigDecimal("poids_apres_kg"), rs.getString("ta_avant"),
                                rs.getString("ta_apres"), entier(rs.getObject("duree_minutes")), entier(rs.getObject("debit_sang_ml_min")),
                                rs.getBigDecimal("ultrafiltration_ml"), rs.getString("anticoagulant"), rs.getString("type_dialysat"),
                                rs.getString("incidents")),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at"))),
                seance.valeur(), centre.valeur()).stream().findFirst();
    }

    @Override
    public void enregistrer(VoletParamedical v) {
        MesuresDeSeance m = v.mesures();
        int lignes = jdbc.update("UPDATE volet_paramedical SET poids_avant_kg = ?, poids_apres_kg = ?, ta_avant = ?, ta_apres = ?, "
                        + "duree_minutes = ?, debit_sang_ml_min = ?, ultrafiltration_ml = ?, anticoagulant = ?, type_dialysat = ?, incidents = ?, "
                        + "updated_at = ? WHERE id = ? AND center_id = ?", m.poidsAvantKg(), m.poidsApresKg(), m.tensionAvant(), m.tensionApres(),
                m.dureeMinutes(), m.debitSangMlMin(), m.ultrafiltrationMl(), m.anticoagulant(), m.typeDeDialysat(), m.incidents(),
                horodatage(v.modifieLe()), v.id(), v.centre().valeur());
        if (lignes > 0) return;
        jdbc.update("INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, duree_minutes, "
                        + "debit_sang_ml_min, ultrafiltration_ml, anticoagulant, type_dialysat, incidents, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", v.id(), v.centre().valeur(), v.seance().valeur(), m.poidsAvantKg(),
                m.poidsApresKg(), m.tensionAvant(), m.tensionApres(), m.dureeMinutes(), m.debitSangMlMin(), m.ultrafiltrationMl(),
                m.anticoagulant(), m.typeDeDialysat(), m.incidents(), horodatage(v.creeLe()), horodatage(v.modifieLe()));
    }

    @Override
    public void effacer(CentreId centre, SeanceId seance) {
        jdbc.update("DELETE FROM volet_paramedical WHERE seance_id = ? AND center_id = ?", seance.valeur(), centre.valeur());
    }

    private static Integer entier(Object valeur) {
        return valeur == null ? null : ((Number) valeur).intValue();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static OffsetDateTime horodatage(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
