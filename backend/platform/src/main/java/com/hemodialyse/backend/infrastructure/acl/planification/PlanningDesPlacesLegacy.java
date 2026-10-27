package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlanningDesPlaces;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link PlanningDesPlaces} (ACL vers le planning des places du legacy, slice P5,
 * retirée avec elle) : salles et créneaux du centre, patients placés avec leurs jours et leurs bornes d'occupation, jours d'ouverture,
 * salles d'isolement et fermetures de la période. Traducteur des types legacy vers ceux du langage publié.
 */
@Component
public class PlanningDesPlacesLegacy implements PlanningDesPlaces {

    private final PlanningDonneesPort planning;

    public PlanningDesPlacesLegacy(PlanningDonneesPort planning) {
        this.planning = planning;
    }

    @Override
    public Planning sur(UUID centre, LocalDate du, LocalDate au) {
        DonneesPlanning d = planning.chargerPeriode(centre, du, au);
        Set<UUID> isolement = d.sallesIsolement() == null ? Set.of() : d.sallesIsolement();
        return new Planning(
                d.salles().stream().map(s -> new Salle(s.id(), s.nom(), isolement.contains(s.id()))).toList(),
                d.creneaux().stream().map(c -> new Creneau(c.id(), c.libelle(), c.ordre())).toList(),
                d.occupations().stream().map(o -> new Occupation(o.salleId(), o.creneauId(), civils(o.jours()), o.premierJour(),
                        o.dernierJour())).toList(),
                d.joursOuverts() == null ? Set.of() : civils(d.joursOuverts()),
                d.fermetures() == null ? List.of() : d.fermetures().stream().map(f -> new Fermeture(f.date(), f.motif())).toList());
    }

    /** Dimanche = 0 dans le legacy, 7 chez {@link DayOfWeek}. */
    private static Set<DayOfWeek> civils(Set<JourSemaine> jours) {
        Set<DayOfWeek> civils = EnumSet.noneOf(DayOfWeek.class);
        jours.forEach(j -> civils.add(DayOfWeek.of(j.ordinal() == 0 ? 7 : j.ordinal())));
        return civils;
    }
}
