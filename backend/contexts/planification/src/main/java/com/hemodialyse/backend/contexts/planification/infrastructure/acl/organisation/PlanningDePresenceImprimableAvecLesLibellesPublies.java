package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation.LibelleDeCreneau;
import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence;
import com.hemodialyse.backend.contexts.planification.application.PlanningDePresenceImprimable;
import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine.LigneDuRoulement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traducteur vers organisation.v1 (DOC5e, ADR 0197) : le roulement de la semaine, que la Planification possède, reçoit le nom de la salle
 * et le code et le libellé du créneau, lus par lot dans {@link LibellesDeLOrganisation} (bornés au centre). Une ligne dont la salle ou le
 * créneau n'a pas de libellé pour ce centre (disparu, ou d'un autre centre) n'est pas imprimée, comme l'ancienne jointure interne sur les
 * tables des salles et des créneaux. L'ordre est celui de l'ancienne requête : jour, code du créneau, salle, situation (par son libellé),
 * infirmier, chaque texte comparé par points de code (ce que donne une base en locale C et H2 ; l'ancienne requête laissait ce tri au
 * SGBD, donc à la locale de la base).
 */
public class PlanningDePresenceImprimableAvecLesLibellesPublies implements PlanningDePresenceImprimable {

    private static final Comparator<LigneDuPlanningDePresence> ORDRE = Comparator
            .comparing(LigneDuPlanningDePresence::jour)
            .thenComparing(LigneDuPlanningDePresence::creneauCode)
            .thenComparing(LigneDuPlanningDePresence::salle)
            .thenComparing(l -> l.situation().libelle())
            .thenComparing(LigneDuPlanningDePresence::infirmier);

    private final RoulementImprimableDeLaSemaine roulement;
    private final LibellesDeLOrganisation organisation;

    public PlanningDePresenceImprimableAvecLesLibellesPublies(RoulementImprimableDeLaSemaine roulement, LibellesDeLOrganisation organisation) {
        this.roulement = roulement;
        this.organisation = organisation;
    }

    @Override
    public List<LigneDuPlanningDePresence> lignes(CentreId centre, LocalDate debutDeSemaine) {
        List<LigneDuRoulement> lignes = roulement.lignes(centre, debutDeSemaine);
        if (lignes.isEmpty()) return List.of();
        Set<UUID> salles = new HashSet<>();
        Set<UUID> creneaux = new HashSet<>();
        for (LigneDuRoulement l : lignes) {
            salles.add(l.salle());
            creneaux.add(l.creneau());
        }
        Map<UUID, String> noms = organisation.nomsDesSalles(centre.valeur(), salles);
        Map<UUID, LibelleDeCreneau> libelles = organisation.creneauxDuCentre(centre.valeur(), creneaux);
        return lignes.stream()
                .filter(l -> noms.containsKey(l.salle()) && libelles.containsKey(l.creneau()))
                .map(l -> new LigneDuPlanningDePresence(l.jour(), noms.get(l.salle()), libelles.get(l.creneau()).code(),
                        libelles.get(l.creneau()).libelle(), l.infirmier(), l.situation()))
                .sorted(ORDRE)
                .toList();
    }
}
