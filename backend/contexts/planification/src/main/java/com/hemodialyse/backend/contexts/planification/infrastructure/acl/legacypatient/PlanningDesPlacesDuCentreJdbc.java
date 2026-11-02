package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.planification.application.LectureDesRessources;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Le planning des places du centre sur une période (P9, ADR 0069) : salles, créneaux, jours d'ouverture et fermetures lus par
 * {@link LectureDesRessources} (Organisation, paramétrage, calendrier clinique), places des patients lues sur les colonnes de place
 * de {@code patients} (aucune migration). Un patient sorti (transféré, décédé, greffé, guéri) garde sa place jusqu'à son dernier jour
 * ({@link FinDOccupation}) ; un patient en sommeil garde la sienne (choix prudent du legacy, évite un double placement à son retour) ;
 * un séjour pas encore commencé reste compté ; une place déjà libérée au début de la période est ignorée. Remplace l'ancienne lecture
 * du legacy ({@code PlanningDonneesJdbcAdapter}) : mêmes règles, mêmes requêtes, bornées au centre.
 */
public class PlanningDesPlacesDuCentreJdbc implements PlanningDesPlaces {

    private final JdbcTemplate jdbc;
    private final LectureDesRessources ressources;

    public PlanningDesPlacesDuCentreJdbc(JdbcTemplate jdbc, LectureDesRessources ressources) {
        this.jdbc = jdbc;
        this.ressources = ressources;
    }

    @Override
    public Planning sur(UUID centre, LocalDate du, LocalDate au) {
        RessourcesDuCentre r = ressources.duCentre(new CentreId(centre), du);
        List<Salle> salles = r.salles().stream().map(s -> new Salle(s.id().valeur(), s.nom(), s.isolement())).toList();
        List<Creneau> creneaux = r.creneaux().stream().map(c -> new Creneau(c.id().valeur(), c.libelle(), c.ordre())).toList();
        List<Fermeture> fermetures = r.fermetures().stream()
                .filter(f -> !f.date().isBefore(du) && !f.date().isAfter(au))
                .map(f -> new Fermeture(f.date(), f.motif())).toList();
        Set<DayOfWeek> ouverts = EnumSet.noneOf(DayOfWeek.class);
        r.joursOuverts().forEach(j -> ouverts.add(j.jourCivil()));
        return new Planning(salles, creneaux, occupations(centre, du), ouverts, fermetures);
    }

    private List<Occupation> occupations(UUID centre, LocalDate du) {
        String sql = "SELECT salle_id, position_id, etat_patient, date_evenement_etat, date_admission, jour_dimanche, jour_lundi, "
                + "jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi FROM patients "
                + "WHERE center_id = ? AND salle_id IS NOT NULL AND position_id IS NOT NULL";
        return jdbc.query(sql, (rs, i) -> {
            Set<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
            for (DayOfWeek jour : DayOfWeek.values()) {
                if (rs.getBoolean("jour_" + nomDeColonne(jour))) jours.add(jour);
            }
            Date evenement = rs.getDate("date_evenement_etat");
            LocalDate dernierJour = FinDOccupation.dernierJourOccupe(rs.getString("etat_patient"),
                    evenement == null ? null : evenement.toLocalDate()).orElse(null);
            Date admission = rs.getDate("date_admission");
            return new Occupation(rs.getObject("salle_id", UUID.class), rs.getObject("position_id", UUID.class), jours,
                    admission == null ? null : admission.toLocalDate(), dernierJour);
        }, centre).stream()
                .filter(o -> !o.jours().isEmpty() && (o.dernierJour() == null || !du.isAfter(o.dernierJour())))
                .toList();
    }

    private static String nomDeColonne(DayOfWeek jour) {
        return switch (jour) {
            case SUNDAY -> "dimanche";
            case MONDAY -> "lundi";
            case TUESDAY -> "mardi";
            case WEDNESDAY -> "mercredi";
            case THURSDAY -> "jeudi";
            case FRIDAY -> "vendredi";
            case SATURDAY -> "samedi";
        };
    }
}
