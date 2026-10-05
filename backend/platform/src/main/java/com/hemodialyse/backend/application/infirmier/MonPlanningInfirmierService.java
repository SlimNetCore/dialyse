package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.application.infirmier.InfirmierService.InfirmierDetail;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CreneauPersonnel;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.infirmier.port.AbsenceInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.InfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.JourPlanning;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * « Mon planning » : l'infirmier connecté (compte relié à une fiche) consulte ses propres créneaux, déclare ses absences
 * à venir et peut retirer celles qui n'ont pas commencé. Toute opération passe par la fiche reliée au compte dans le
 * centre courant : un infirmier ne voit ni ne modifie jamais les données d'un autre.
 */
@Service
public class MonPlanningInfirmierService {

    private final InfirmierRepositoryPort infirmiers;
    private final AbsenceInfirmierRepositoryPort absences;
    private final PresenceInfirmierQueryService presence;
    private final InfirmierService referentiel;
    private final NotificationService notifications;

    public MonPlanningInfirmierService(InfirmierRepositoryPort infirmiers, AbsenceInfirmierRepositoryPort absences,
                                       PresenceInfirmierQueryService presence, InfirmierService referentiel,
                                       NotificationService notifications) {
        this.infirmiers = infirmiers;
        this.absences = absences;
        this.presence = presence;
        this.referentiel = referentiel;
        this.notifications = notifications;
    }

    /**
     * Résumé de chaque case où l'infirmier est prévu, remplaçant ou absent : nombre de patients, effectif requis et
     * nombre de collègues. Aucune donnée nominative d'un autre infirmier n'est exposée.
     */
    private static List<CaseMonPlanning> mesCases(SemainePresence semaine, List<CreneauPersonnel> mesCreneaux,
                                                  UUID infirmierId) {
        Set<String> miennes = mesCreneaux.stream()
                .map(c -> cle(c.date(), c.salleId(), c.creneauId())).collect(Collectors.toSet());
        return semaine.cases().stream()
                .filter(c -> miennes.contains(cle(c.date(), c.salleId(), c.creneauId())))
                .map(c -> new CaseMonPlanning(c.date(), c.jour(), c.salleId(), c.creneauId(), c.patients(),
                        c.requis(), c.salleIsolement(),
                        (int) c.presents().stream().filter(p -> !p.infirmierId().equals(infirmierId)).count()))
                .toList();
    }

    private static String cle(LocalDate date, UUID salleId, UUID creneauId) {
        return date + "|" + salleId + "|" + creneauId;
    }

    /**
     * @param date un jour de la semaine voulue (semaine du dimanche au samedi)
     */
    public MonPlanning planning(UUID centerId, UUID userId, LocalDate date) {
        Infirmier fiche = fiche(centerId, userId);
        SemainePresence semaine = presence.semaine(centerId, date);
        List<CreneauPersonnel> mesCreneaux = PresenceInfirmierService.creneauxDe(semaine, fiche.id());
        return new MonPlanning(referentiel.detail(centerId, fiche), semaine.debut(), semaine.fin(), semaine.salles(),
                semaine.creneaux(), mesCreneaux, semaine.jours(), mesCases(semaine, mesCreneaux, fiche.id()));
    }

    public PagedResult<AbsenceInfirmier> mesAbsences(UUID centerId, UUID userId, int page, int size) {
        return absences.findPagedByInfirmier(centerId, fiche(centerId, userId).id(), page, size);
    }

    /**
     * Déclare une absence à venir ou en cours (fin non antérieure à aujourd'hui) et prévient l'administration.
     */
    public AbsenceInfirmier declarer(UUID centerId, UUID userId, LocalDate aujourdhui, LocalDate debut, LocalDate fin,
                                     TypeAbsence type, String motif) {
        Infirmier fiche = fiche(centerId, userId);
        AbsenceInfirmier absence = AbsenceInfirmier.creer(centerId, fiche.id(), debut, fin, type, motif);
        if (fin.isBefore(aujourdhui)) {
            throw new BusinessException("ABSENCE_PASSEE", "Une absence passée ne peut être saisie que par l'administration");
        }
        AbsenceInfirmier enregistree = absences.save(absence);
        notifications.notifyAbsenceInfirmierDeclaree(centerId, fiche.nomComplet(), debut, fin);
        return enregistree;
    }

    public void annuler(UUID centerId, UUID userId, LocalDate aujourdhui, UUID absenceId) {
        Infirmier fiche = fiche(centerId, userId);
        AbsenceInfirmier absence = absences.findById(centerId, absenceId)
                .filter(a -> a.infirmierId().equals(fiche.id()))
                .orElseThrow(() -> new BusinessException("ABSENCE_INTROUVABLE", "Absence introuvable"));
        if (!absence.annulableParInfirmier(aujourdhui)) {
            throw new BusinessException("ABSENCE_NON_ANNULABLE",
                    "Une absence commencée ne peut être retirée que par l'administration");
        }
        absences.delete(centerId, absenceId);
    }

    private Infirmier fiche(UUID centerId, UUID userId) {
        return infirmiers.findByUserId(centerId, userId).orElseThrow(() -> new BusinessException(
                "INFIRMIER_NON_LIE", "Votre compte n'est relié à aucune fiche infirmier de ce centre"));
    }

    /**
     * Planning personnel d'une semaine.
     */
    public record MonPlanning(InfirmierDetail infirmier, LocalDate debut, LocalDate fin, List<SalleRef> salles,
                              List<CreneauRef> creneaux, List<CreneauPersonnel> mesCreneaux, List<JourPlanning> jours,
                              List<CaseMonPlanning> mesCases) {
    }

    /**
     * Case de la grille de l'infirmier : charge de la salle et du créneau (patients, requis) et nombre de collègues
     * prévus avec lui.
     */
    public record CaseMonPlanning(LocalDate date, JourSemaine jour, UUID salleId, UUID creneauId, int patients,
                                  int requis, boolean salleIsolement, int collegues) {
    }
}
