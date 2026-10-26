package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.RoulementEtAbsencesDesInfirmiers;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.PresenceDonneesPort;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Données du planning de présence d'un centre sur une période : placement des patients (via le port du planning), infirmiers
 * actifs, puis roulement, absences chevauchant la période et remplacements de la période, lus par le langage publié du contexte
 * Planification (slice P3) et traduits en modèle de présence.
 */
@Component
public class PresenceDonneesJdbcAdapter implements PresenceDonneesPort {

    private final JdbcTemplate jdbc;
    private final PlanningDonneesPort planning;
    private final PlanningParametresPort parametres;
    private final RoulementEtAbsencesDesInfirmiers roulement;

    public PresenceDonneesJdbcAdapter(JdbcTemplate jdbc, PlanningDonneesPort planning, PlanningParametresPort parametres,
                                      RoulementEtAbsencesDesInfirmiers roulement) {
        this.jdbc = jdbc;
        this.planning = planning;
        this.parametres = parametres;
        this.roulement = roulement;
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

        List<AffectationInfirmier> affectations = roulement.affectationsDesInfirmiersActifs(centerId).stream()
                .map(a -> new AffectationInfirmier(a.id(), centerId, a.infirmier(), a.salle(), a.creneau(), jours(a.jours())))
                .toList();

        List<AbsenceInfirmier> absences = roulement.absencesSurLaPeriode(centerId, du, au).stream()
                .map(a -> new AbsenceInfirmier(a.id(), centerId, a.infirmier(), a.debut(), a.fin(), TypeAbsence.valueOf(a.type()),
                        a.motif()))
                .toList();

        List<RemplacementInfirmier> remplacements = roulement.remplacementsSurLaPeriode(centerId, du, au).stream()
                .map(r -> new RemplacementInfirmier(r.id(), centerId, r.date(), r.salle(), r.creneau(), r.infirmier(), r.remplace()))
                .toList();

        return new DonneesPresence(planning.chargerPeriode(centerId, du, au),
                parametres.lire(centerId).patientsParInfirmier(), infirmiers, affectations, absences, remplacements);
    }

    private static Set<JourSemaine> jours(Set<java.time.DayOfWeek> civils) {
        Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
        civils.forEach(j -> jours.add(JourSemaine.de(j)));
        return jours;
    }
}
