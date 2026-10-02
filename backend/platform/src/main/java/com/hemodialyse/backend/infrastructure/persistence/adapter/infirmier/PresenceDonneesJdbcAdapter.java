package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.PresenceDonneesPort;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Données du planning de présence d'un centre sur une période : placement des patients (via le port du planning),
 * infirmiers actifs, roulement, absences chevauchant la période et remplacements de la période.
 */
@Component
public class PresenceDonneesJdbcAdapter implements PresenceDonneesPort {

    private final JdbcTemplate jdbc;
    private final PlanningDonneesPort planning;
    private final PlanningParametresPort parametres;

    public PresenceDonneesJdbcAdapter(JdbcTemplate jdbc, PlanningDonneesPort planning,
                                      PlanningParametresPort parametres) {
        this.jdbc = jdbc;
        this.planning = planning;
        this.parametres = parametres;
    }

    @Override
    public DonneesPresence charger(UUID centerId, LocalDate du, LocalDate au) {
        List<InfirmierRef> infirmiers = jdbc.query(
                "SELECT id, nom, prenom, qualification, habilite_isolement FROM infirmier "
                        + "WHERE center_id = ? AND actif = TRUE ORDER BY nom, prenom",
                (rs, i) -> {
                    String prenom = rs.getString("prenom");
                    String nom = rs.getString("nom");
                    return new InfirmierRef(rs.getObject("id", UUID.class), prenom == null ? nom : prenom + " " + nom,
                            QualificationInfirmier.valueOf(rs.getString("qualification")),
                            rs.getBoolean("habilite_isolement"));
                }, centerId);

        List<AffectationInfirmier> affectations = jdbc.query(
                "SELECT a.id, a.center_id, a.infirmier_id, a.salle_id, a.creneau_id, a.jours "
                        + "FROM infirmier_affectation a JOIN infirmier i ON i.id = a.infirmier_id AND i.center_id = a.center_id "
                        + "WHERE a.center_id = ? AND i.actif = TRUE",
                (rs, i) -> new AffectationInfirmier(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                        rs.getObject("infirmier_id", UUID.class), rs.getObject("salle_id", UUID.class),
                        rs.getObject("creneau_id", UUID.class), AffectationInfirmierJdbcAdapter.decoder(rs.getString("jours"))),
                centerId);

        List<AbsenceInfirmier> absences = jdbc.query(
                "SELECT id, center_id, infirmier_id, date_debut, date_fin, type, motif FROM infirmier_absence "
                        + "WHERE center_id = ? AND date_debut <= ? AND date_fin >= ?",
                (rs, i) -> new AbsenceInfirmier(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                        rs.getObject("infirmier_id", UUID.class), rs.getDate("date_debut").toLocalDate(),
                        rs.getDate("date_fin").toLocalDate(), TypeAbsence.valueOf(rs.getString("type")),
                        rs.getString("motif")),
                centerId, Date.valueOf(au), Date.valueOf(du));

        List<RemplacementInfirmier> remplacements = jdbc.query(
                "SELECT id, center_id, date_jour, salle_id, creneau_id, infirmier_id, remplace_infirmier_id "
                        + "FROM infirmier_remplacement WHERE center_id = ? AND date_jour BETWEEN ? AND ?",
                (rs, i) -> new RemplacementInfirmier(rs.getObject("id", UUID.class),
                        rs.getObject("center_id", UUID.class), rs.getDate("date_jour").toLocalDate(),
                        rs.getObject("salle_id", UUID.class), rs.getObject("creneau_id", UUID.class),
                        rs.getObject("infirmier_id", UUID.class), rs.getObject("remplace_infirmier_id", UUID.class)),
                centerId, Date.valueOf(du), Date.valueOf(au));

        return new DonneesPresence(planning.chargerPeriode(centerId, du, au),
                parametres.lire(centerId).patientsParInfirmier(), infirmiers, affectations, absences, remplacements);
    }
}
