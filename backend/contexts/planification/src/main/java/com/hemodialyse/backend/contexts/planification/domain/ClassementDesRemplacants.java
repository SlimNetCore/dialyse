package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Candidat;
import com.hemodialyse.backend.contexts.planification.domain.Presence.CasePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.DonneesDePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Present;
import com.hemodialyse.backend.contexts.planification.domain.Presence.RaisonDeRemplacement;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Classe les remplaçants possibles d'une case (RG-INF-051). Sont exclus : les infirmiers absents ce jour-là, ceux déjà prévus sur ce
 * créneau (dans n'importe quelle salle) et, en salle d'isolement, les non habilités. Les autres sont notés de 0 à 100 : salle connue,
 * créneau habituel, charge de la semaine, jour libre (pas de double vacation), habilitation à l'isolement ; départage par nom.
 */
public final class ClassementDesRemplacants {

    private static final int SCORE_BASE = 40;
    private static final int BONUS_SALLE_CONNUE = 15;
    private static final int BONUS_CRENEAU_HABITUEL = 10;
    private static final int BONUS_CHARGE_MAX = 20;
    private static final int MALUS_PAR_SEANCE = 4;
    private static final int BONUS_JOUR_LIBRE = 10;
    private static final int MALUS_DOUBLE_VACATION = 25;
    private static final int BONUS_ISOLEMENT = 10;
    private static final int SEUIL_CHARGE_FAIBLE = 2;

    private ClassementDesRemplacants() {
    }

    public static List<Candidat> proposer(DonneesDePresence donnees, LocalDate date, UUID salleId, UUID creneauId, int limite) {
        SemainePresence semaine = PresenceDeLaSemaine.construire(donnees, PresenceDeLaSemaine.debutDeSemaine(date));
        boolean salleIso = donnees.planning().sallesIsolement().contains(salleId);

        Set<UUID> memeCreneau = new HashSet<>();
        Set<UUID> autreCreneau = new HashSet<>();
        Map<UUID, Integer> seances = new HashMap<>();
        for (CasePresence c : semaine.cases()) {
            for (Present p : c.presents()) {
                seances.merge(p.infirmierId(), 1, Integer::sum);
                if (!c.date().equals(date)) continue;
                (c.creneauId().equals(creneauId) ? memeCreneau : autreCreneau).add(p.infirmierId());
            }
        }

        List<Candidat> candidats = new ArrayList<>();
        for (InfirmierDeLaPresence i : donnees.personnel().infirmiers()) {
            if (memeCreneau.contains(i.id()) || (salleIso && !i.habiliteIsolement())
                    || PresenceDeLaSemaine.absenceLe(donnees, i.id(), date).isPresent()) {
                continue;
            }
            candidats.add(noter(donnees, i, salleId, creneauId, salleIso, autreCreneau.contains(i.id()), seances.getOrDefault(i.id(), 0)));
        }
        candidats.sort(Comparator.comparingInt(Candidat::score).reversed().thenComparing(Candidat::nom));
        return candidats.size() > limite ? candidats.subList(0, limite) : candidats;
    }

    private static Candidat noter(DonneesDePresence donnees, InfirmierDeLaPresence infirmier, UUID salleId, UUID creneauId,
                                  boolean salleIso, boolean doubleVacation, int seancesSemaine) {
        int score = SCORE_BASE;
        List<RaisonDeRemplacement> raisons = new ArrayList<>();
        List<AffectationPrevue> siennes = donnees.personnel().affectations().stream()
                .filter(a -> a.infirmier().equals(infirmier.id())).toList();
        if (siennes.stream().anyMatch(a -> a.salle().equals(salleId))) {
            score += BONUS_SALLE_CONNUE;
            raisons.add(RaisonDeRemplacement.SALLE_CONNUE);
        }
        if (siennes.stream().anyMatch(a -> a.creneau().equals(creneauId))) {
            score += BONUS_CRENEAU_HABITUEL;
            raisons.add(RaisonDeRemplacement.CRENEAU_HABITUEL);
        }
        score += Math.max(0, BONUS_CHARGE_MAX - MALUS_PAR_SEANCE * seancesSemaine);
        if (seancesSemaine <= SEUIL_CHARGE_FAIBLE) raisons.add(RaisonDeRemplacement.CHARGE_FAIBLE);
        if (doubleVacation) {
            score -= MALUS_DOUBLE_VACATION;
            raisons.add(RaisonDeRemplacement.DOUBLE_VACATION);
        } else {
            score += BONUS_JOUR_LIBRE;
            raisons.add(RaisonDeRemplacement.JOUR_LIBRE);
        }
        if (salleIso) {
            score += BONUS_ISOLEMENT;
            raisons.add(RaisonDeRemplacement.HABILITE_ISOLEMENT);
        }
        return new Candidat(infirmier.id(), infirmier.nomAffiche(), infirmier.qualification(), infirmier.habiliteIsolement(),
                Math.max(0, Math.min(100, score)), raisons, seancesSemaine);
    }
}
