package com.hemodialyse.backend.domain.infirmier.service;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CasePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Candidat;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Present;
import com.hemodialyse.backend.domain.infirmier.model.Presence.RaisonRemplacement;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;

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
 * Propose des remplaçants pour une case (salle, créneau, date). Sont exclus : les infirmiers absents ce jour-là, ceux
 * déjà prévus sur ce créneau (dans n'importe quelle salle) et, en salle d'isolement, les infirmiers non habilités.
 * Les autres sont classés : connaissance de la salle, créneau habituel, charge de la semaine, jour libre (pas de double
 * vacation) et habilitation à l'isolement.
 */
public final class RemplacantService {

    private static final int SCORE_BASE = 40;
    private static final int BONUS_SALLE_CONNUE = 15;
    private static final int BONUS_CRENEAU_HABITUEL = 10;
    private static final int BONUS_CHARGE_MAX = 20;
    private static final int MALUS_PAR_SEANCE = 4;
    private static final int BONUS_JOUR_LIBRE = 10;
    private static final int MALUS_DOUBLE_VACATION = 25;
    private static final int BONUS_ISOLEMENT = 10;
    private static final int SEUIL_CHARGE_FAIBLE = 2;

    private RemplacantService() {
    }

    public static List<Candidat> proposer(DonneesPresence donnees, LocalDate date, UUID salleId, UUID creneauId,
                                          int limite) {
        SemainePresence semaine = PresenceInfirmierService.construire(donnees,
                PlanningSemaineService.debutSemaine(date));
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
        for (InfirmierRef i : donnees.infirmiers()) {
            if (memeCreneau.contains(i.id()) || (salleIso && !i.habiliteIsolement())
                    || PresenceInfirmierService.absenceLe(donnees, i.id(), date).isPresent()) {
                continue;
            }
            candidats.add(noter(donnees, i, salleId, creneauId, salleIso, autreCreneau.contains(i.id()),
                    seances.getOrDefault(i.id(), 0)));
        }
        candidats.sort(Comparator.comparingInt(Candidat::score).reversed().thenComparing(Candidat::nom));
        return candidats.size() > limite ? candidats.subList(0, limite) : candidats;
    }

    private static Candidat noter(DonneesPresence donnees, InfirmierRef infirmier, UUID salleId, UUID creneauId,
                                  boolean salleIso, boolean doubleVacation, int seancesSemaine) {
        int score = SCORE_BASE;
        List<RaisonRemplacement> raisons = new ArrayList<>();
        List<AffectationInfirmier> siennes = donnees.affectations().stream()
                .filter(a -> a.infirmierId().equals(infirmier.id())).toList();
        if (siennes.stream().anyMatch(a -> a.salleId().equals(salleId))) {
            score += BONUS_SALLE_CONNUE;
            raisons.add(RaisonRemplacement.SALLE_CONNUE);
        }
        if (siennes.stream().anyMatch(a -> a.creneauId().equals(creneauId))) {
            score += BONUS_CRENEAU_HABITUEL;
            raisons.add(RaisonRemplacement.CRENEAU_HABITUEL);
        }
        score += Math.max(0, BONUS_CHARGE_MAX - MALUS_PAR_SEANCE * seancesSemaine);
        if (seancesSemaine <= SEUIL_CHARGE_FAIBLE) raisons.add(RaisonRemplacement.CHARGE_FAIBLE);
        if (doubleVacation) {
            score -= MALUS_DOUBLE_VACATION;
            raisons.add(RaisonRemplacement.DOUBLE_VACATION);
        } else {
            score += BONUS_JOUR_LIBRE;
            raisons.add(RaisonRemplacement.JOUR_LIBRE);
        }
        if (salleIso) {
            score += BONUS_ISOLEMENT;
            raisons.add(RaisonRemplacement.HABILITE_ISOLEMENT);
        }
        return new Candidat(infirmier.id(), infirmier.nom(), infirmier.qualification(), infirmier.habiliteIsolement(),
                Math.max(0, Math.min(100, score)), raisons, seancesSemaine);
    }
}
