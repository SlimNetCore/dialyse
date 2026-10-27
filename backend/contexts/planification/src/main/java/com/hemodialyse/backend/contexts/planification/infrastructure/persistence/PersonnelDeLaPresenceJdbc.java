package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;

import com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.Presence.RemplacementPose;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.RoulementEtAbsencesDesInfirmiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Le personnel de la présence lu dans les tables {@code infirmier}, {@code infirmier_affectation}, {@code infirmier_absence},
 * {@code infirmier_remplacement} et le ratio de {@code planning_parametres}. Seuls les infirmiers actifs comptent. Un ratio présent
 * mais inutilisable (hors 1 à 20, comme l'écriture : décision du 27/10/2026) est remplacé par le défaut et journalisé (KB-PLN-47) ; une ligne absente ou un ratio nul valent le
 * défaut en silence.
 */
public class PersonnelDeLaPresenceJdbc implements PersonnelDeLaPresence {
    private static final Logger log = LoggerFactory.getLogger(PersonnelDeLaPresenceJdbc.class);

    private final JdbcTemplate jdbc;
    private final RoulementEtAbsencesDesInfirmiers roulement;

    public PersonnelDeLaPresenceJdbc(JdbcTemplate jdbc, RoulementEtAbsencesDesInfirmiers roulement) {
        this.jdbc = jdbc;
        this.roulement = roulement;
    }

    @Override
    public Personnel lire(CentreId centre, LocalDate du, LocalDate au) {
        UUID id = centre.valeur();
        List<InfirmierDeLaPresence> infirmiers = jdbc.query(
                "SELECT id, nom, prenom, qualification, habilite_isolement FROM infirmier "
                        + "WHERE center_id = ? AND actif = TRUE ORDER BY nom, prenom, id",
                (rs, i) -> new InfirmierDeLaPresence(rs.getObject("id", UUID.class), rs.getString("nom"), rs.getString("prenom"),
                        Qualification.valueOf(rs.getString("qualification")), rs.getBoolean("habilite_isolement")), id);
        List<AffectationPrevue> affectations = roulement.affectationsDesInfirmiersActifs(id).stream()
                .map(a -> new AffectationPrevue(a.infirmier(), a.salle(), a.creneau(), jours(a.jours()))).toList();
        List<AbsenceConnue> absences = roulement.absencesSurLaPeriode(id, du, au).stream()
                .map(a -> new AbsenceConnue(a.infirmier(), a.debut(), a.fin(), TypeAbsence.valueOf(a.type()))).toList();
        List<RemplacementPose> remplacements = roulement.remplacementsSurLaPeriode(id, du, au).stream()
                .map(r -> new RemplacementPose(r.id(), r.date(), r.salle(), r.creneau(), r.infirmier())).toList();
        return new Personnel(infirmiers, affectations, absences, remplacements, ratio(id));
    }

    private RatioDeLaPresence ratio(UUID centre) {
        List<Integer> valeurs = jdbc.query("SELECT patients_par_infirmier FROM planning_parametres WHERE center_id = ?",
                (rs, i) -> {
                    int valeur = rs.getInt(1);
                    return rs.wasNull() ? null : valeur;
                }, centre);
        RatioDeLaPresence ratio = RatioDeLaPresence.depuisLaBase(valeurs.isEmpty() ? null : valeurs.getFirst());
        if (ratio.remplaceParLeDefaut()) {
            log.warn("[PRESENCE] Ratio de sécurité inutilisable en base ({}) pour le centre {} : le défaut {} est utilisé",
                    valeurs.getFirst(), centre, ratio.patientsParInfirmier());
        }
        return ratio;
    }

    private static Set<JourDeLaSemaine> jours(Set<java.time.DayOfWeek> civils) {
        Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
        civils.forEach(j -> jours.add(JourDeLaSemaine.de(j)));
        return jours;
    }
}
