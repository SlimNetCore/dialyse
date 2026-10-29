package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Politique de couverture des absences à l'application d'une proposition (RG-PLN-091 à 094, RG-PLN-088, revue P8 M5) : pour chaque
 * vacation proposée qui n'existait pas, elle juge l'éligibilité du remplaçant face à l'état du centre ({@link RemplacantEligible}) et
 * relie le remplacé. Le remplacé est l'infirmier ABSENT ce jour-là, normalement prévu sur la case au roulement (salle, créneau, jour
 * de la semaine), PAS ENCORE remplacé par une vacation précédente de la même application ; à défaut, aucun (couverture sans remplacé).
 * Plusieurs absents prévus sur la même case sont pris par ordre croissant d'identifiant. Une instance sert une seule application
 * (elle retient les remplacés déjà pris).
 */
public final class CouvertureDesAbsences {

    private final Personnel personnel;
    private final RessourcesDuCentre ressources;
    private final Map<UUID, InfirmierDeLaPresence> fiches = new HashMap<>();
    private final Set<String> dejaRemplaces = new HashSet<>();
    public CouvertureDesAbsences(Personnel personnel, RessourcesDuCentre ressources) {
        this.personnel = personnel;
        this.ressources = ressources;
        personnel.infirmiers().forEach(i -> fiches.put(i.id(), i));
    }

    /**
     * @throws RegleDePlanificationViolee {@code REMPLACEMENT_INELIGIBLE} : remplaçant inconnu, absent ce jour, déjà prévu sur le
     *                                    créneau, ou non habilité pour une salle d'isolement
     */
    public Affectation affecter(VacationPlanifiee vacation) {
        InfirmierDeLaPresence fiche = fiches.get(vacation.infirmier().valeur());
        RemplacantEligible.Situation situation = new RemplacantEligible.Situation(vacation.infirmier(), fiche != null,
                fiche != null && fiche.habiliteIsolement(), ressources.estIsolement(vacation.salle()),
                estAbsent(vacation.infirmier().valeur(), vacation.date()), estDejaPrevu(vacation));
        RemplacantEligible remplacant = RemplacantEligible.prouver(situation);
        return new Affectation(remplacant, absentPrevuSurLaCase(vacation).orElse(null));
    }

    private boolean estAbsent(UUID infirmier, LocalDate jour) {
        return personnel.absences().stream().anyMatch(a -> a.infirmier().equals(infirmier) && a.couvre(jour));
    }

    private boolean estDejaPrevu(VacationPlanifiee v) {
        boolean auRoulement = personnel.affectations().stream().anyMatch(a -> a.infirmier().equals(v.infirmier().valeur())
                && a.creneau().equals(v.creneau().valeur()) && a.jours().contains(v.jour()));
        boolean remplacant = personnel.remplacements().stream().anyMatch(r -> r.infirmier().equals(v.infirmier().valeur())
                && r.date().equals(v.date()) && r.creneau().equals(v.creneau().valeur()));
        return auRoulement || remplacant;
    }

    /**
     * Les absents ce jour-là normalement prévus sur la case de cette vacation, par ordre d'identifiant : les remplacés possibles.
     * Le cas d'usage verrouille leurs fiches avec celles des remplaçants (une absence retirée en parallèle ne laisse pas un remplacé
     * qui n'est plus absent, revue P8 m2).
     */
    public List<InfirmierId> remplacesPossibles(VacationPlanifiee v) {
        return personnel.affectations().stream()
                .filter((AffectationPrevue a) -> a.salle().equals(v.salle().valeur()) && a.creneau().equals(v.creneau().valeur())
                        && a.jours().contains(v.jour()))
                .map(AffectationPrevue::infirmier)
                .filter(i -> estAbsent(i, v.date()))
                .distinct().sorted(Comparator.comparing(UUID::toString))
                .map(InfirmierId::new).toList();
    }

    private Optional<InfirmierId> absentPrevuSurLaCase(VacationPlanifiee v) {
        return remplacesPossibles(v).stream()
                .filter(candidat -> dejaRemplaces.add(candidat.valeur() + "|" + v.date() + "|" + v.creneau().valeur()))
                .findFirst();
    }

    /**
     * Le remplaçant dont l'éligibilité est prouvée et le remplacé relié (nul s'il n'y en a pas).
     */
    public record Affectation(RemplacantEligible remplacant, InfirmierId remplace) {
    }
}
