package com.hemodialyse.backend.application.absence;

import com.hemodialyse.backend.domain.absence.model.AbsenceFiltre;
import com.hemodialyse.backend.domain.absence.model.AbsenceLigne;
import com.hemodialyse.backend.domain.absence.model.AbsencePatient;
import com.hemodialyse.backend.domain.absence.model.StatutAbsence;
import com.hemodialyse.backend.domain.absence.port.AbsenceDonneesPort;
import com.hemodialyse.backend.domain.absence.port.AbsencePatientRepositoryPort;
import com.hemodialyse.backend.domain.absence.service.ValorisationAbsenceService;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Détection automatique des absences de patients (séances prévues non réalisées) : contrôle de nuit, réconciliation et
 * rattrapage d'une période passée. Reste au legacy jusqu'à la slice A3 ; la consultation (A1) et le cycle de vie (A2 :
 * déclaration, qualification, rattrapage, annulation) sont dans le contexte absences-patients. Toujours borné au centre.
 */
@Service
public class AbsencePatientService {

    static final int PAGE_RECONCILIATION = 200;
    /**
     * Durée maximale d'un rattrapage manuel (un an).
     */
    static final int RATTRAPAGE_JOURS_MAX = 366;

    private final AbsencePatientRepositoryPort absences;
    private final AbsenceDonneesPort donnees;
    private final ValorisationAbsenceService valorisation;
    private final Clock clock;

    @Autowired
    public AbsencePatientService(AbsencePatientRepositoryPort absences, AbsenceDonneesPort donnees,
                                 ValorisationAbsenceService valorisation) {
        this(absences, donnees, valorisation, Clock.systemUTC());
    }

    AbsencePatientService(AbsencePatientRepositoryPort absences, AbsenceDonneesPort donnees,
                          ValorisationAbsenceService valorisation, Clock clock) {
        this.absences = absences;
        this.donnees = donnees;
        this.valorisation = valorisation;
        this.clock = clock;
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }

    // La consultation (liste, semaine, séances de rattrapage) est passée au contexte absences-patients (slice A1). La
    // synthèse reste ici pour le contrôle de nuit (controlerCentre, notification) jusqu'à la slice A3.
    public Synthese synthese(UUID centerId) {
        return new Synthese(absences.countAQualifier(centerId),
                absences.countEnRetard(centerId, aujourdhui().minusDays(AbsencePatient.DELAI_QUALIFICATION_JOURS)));
    }

    /**
     * Détecte, pour un centre et un jour, les patients attendus sans séance réalisée et crée leur absence « à
     * qualifier » (idempotent : une absence déjà présente, même annulée, n'est jamais recréée).
     *
     * @return nombre d'absences créées
     */
    public int detecter(UUID centerId, LocalDate date) {
        int crees = 0;
        for (UUID patientId : donnees.patientsAttendusSansSeance(centerId, date)) {
            if (absences.findByPatientAndDate(centerId, patientId, date).isPresent()
                    || donnees.periodeFacturee(centerId, patientId, date)) continue;
            absences.save(AbsencePatient.detectee(centerId, patientId, date,
                    valorisation.valoriser(centerId, patientId, date), Instant.now(clock)));
            crees++;
        }
        return crees;
    }

    /**
     * Annule les absences détectées « à qualifier » dont le patient a finalement une séance réalisée ce jour-là
     * (saisie tardive de la séance).
     *
     * @return nombre d'absences annulées
     */
    public int reconcilier(UUID centerId, LocalDate from, LocalDate to) {
        int annulees = 0;
        int page = 0;
        PagedResult<AbsenceLigne> lot;
        do {
            lot = absences.findPaged(centerId,
                    new AbsenceFiltre(StatutAbsence.A_QUALIFIER, null, null, from, to), page, PAGE_RECONCILIATION);
            for (AbsenceLigne ligne : lot.items()) {
                AbsencePatient a = ligne.absence();
                if (donnees.seanceRealisee(centerId, a.patientId(), a.dateSeance())) {
                    a.annuler("Séance réalisée à cette date", null, Instant.now(clock));
                    // seam A2 : une absence qualifiée ou rattrapée entre la lecture et l'écriture n'est pas ré-annulée
                    if (absences.saveIfAQualifier(a)) annulees++;
                }
            }
            page++;
        } while ((long) page * PAGE_RECONCILIATION < lot.total() && !lot.items().isEmpty());
        return annulees;
    }

    /**
     * Contrôle quotidien d'un centre : rattrape les {@code joursARetrocontroler} derniers jours (hors aujourd'hui).
     */
    public Synthese controlerCentre(UUID centerId, int joursARetrocontroler) {
        LocalDate hier = aujourdhui().minusDays(1);
        LocalDate debut = hier.minusDays(joursARetrocontroler - 1L);
        reconcilier(centerId, debut, hier);
        for (LocalDate d = debut; !d.isAfter(hier); d = d.plusDays(1)) {
            detecter(centerId, d);
        }
        return synthese(centerId);
    }

    /**
     * Rattrapage d'une période passée : détecte les absences jamais détectées (scheduler arrêté, base reprise, mois
     * antérieurs au déploiement) et annule celles dont la séance a été saisie après coup. Idempotent. La fin est
     * ramenée à hier (le jour courant n'est pas terminé).
     *
     * @throws BusinessException {@code ABSENCE_PERIODE_INVALIDE} (début après la fin) ou
     *                           {@code ABSENCE_PERIODE_TROP_LONGUE} (plus de {@value #RATTRAPAGE_JOURS_MAX} jours)
     */
    public Rattrapage rattraperPeriode(UUID centerId, LocalDate from, LocalDate to) {
        LocalDate fin = to.isAfter(aujourdhui().minusDays(1)) ? aujourdhui().minusDays(1) : to;
        if (from.isAfter(fin)) {
            throw new BusinessException("ABSENCE_PERIODE_INVALIDE", "Période invalide : le début doit précéder la fin (hier au plus tard)");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, fin) >= RATTRAPAGE_JOURS_MAX) {
            throw new BusinessException("ABSENCE_PERIODE_TROP_LONGUE",
                    "Période trop longue : " + RATTRAPAGE_JOURS_MAX + " jours au maximum");
        }
        int annulees = reconcilier(centerId, from, fin);
        int creees = 0;
        for (LocalDate d = from; !d.isAfter(fin); d = d.plusDays(1)) {
            creees += detecter(centerId, d);
        }
        return new Rattrapage(creees, annulees);
    }

    /**
     * Résultat d'un rattrapage : absences créées et absences annulées (séance saisie après coup).
     */
    public record Rattrapage(int creees, int annulees) {
    }

    public List<UUID> centres() {
        return donnees.centresActifs();
    }

    public record Synthese(long aQualifier, long enRetard) {
    }
}
