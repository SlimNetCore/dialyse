package com.hemodialyse.backend.application.absence;

import com.hemodialyse.backend.domain.absence.model.AbsenceFiltre;
import com.hemodialyse.backend.domain.absence.model.AbsenceLigne;
import com.hemodialyse.backend.domain.absence.model.AbsencePatient;
import com.hemodialyse.backend.domain.absence.model.MotifAbsence;
import com.hemodialyse.backend.domain.absence.model.StatutAbsence;
import com.hemodialyse.backend.domain.absence.port.AbsenceDonneesPort;
import com.hemodialyse.backend.domain.absence.port.AbsencePatientRepositoryPort;
import com.hemodialyse.backend.domain.absence.service.ValorisationAbsenceService;
import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;
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
 * Suivi des absences de patients : déclaration, qualification, rattrapage, annulation et détection automatique des
 * séances prévues non réalisées. Toujours borné au centre ; les corrections d'une absence déjà qualifiée sont réservées
 * aux profils autorisés ({@code peutCorriger}).
 */
@Service
public class AbsencePatientService {

    static final int PAGE_RECONCILIATION = 200;

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

    private static void exigerDroitDeCorrection(AbsencePatient a, boolean peutCorriger) {
        if (a.statut() != StatutAbsence.A_QUALIFIER && !peutCorriger) {
            throw new BusinessException("ABSENCE_CORRECTION_INTERDITE",
                    "Seuls un administrateur ou un médecin peuvent corriger une absence déjà qualifiée");
        }
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }

    /**
     * Absences non annulées de la semaine (dimanche → samedi) contenant {@code date}, pour colorer le planning.
     */
    public List<AbsenceSemaine> semaine(UUID centerId, LocalDate date) {
        LocalDate debut = PlanningSemaineService.debutSemaine(date);
        return absences.findBetween(centerId, debut, debut.plusDays(6)).stream()
                .map(a -> new AbsenceSemaine(a.id(), a.patientId(), a.dateSeance(), a.statut(), a.motif()))
                .toList();
    }

    public record AbsenceSemaine(UUID absenceId, UUID patientId, LocalDate dateSeance, StatutAbsence statut,
                                 MotifAbsence motif) {
    }

    public PagedResult<AbsenceLigne> lister(UUID centerId, AbsenceFiltre filtre, int page, int size) {
        return absences.findPaged(centerId, filtre, page, size);
    }

    public Synthese synthese(UUID centerId) {
        return new Synthese(absences.countAQualifier(centerId),
                absences.countEnRetard(centerId, aujourdhui().minusDays(AbsencePatient.DELAI_QUALIFICATION_JOURS)));
    }

    public AbsencePatient declarer(UUID centerId, UUID patientId, LocalDate dateSeance, MotifAbsence motif,
                                   String commentaire, UUID utilisateur) {
        donnees.nomPatient(centerId, patientId)
                .orElseThrow(() -> new BusinessException("PATIENT_INTROUVABLE", "Patient introuvable"));
        absences.findByPatientAndDate(centerId, patientId, dateSeance).ifPresent(existante -> {
            throw new BusinessException("ABSENCE_EXISTANTE", "Une absence existe déjà pour ce patient à cette date");
        });
        exigerPeriodeOuverte(centerId, patientId, dateSeance);
        if (donnees.seanceRealisee(centerId, patientId, dateSeance)) {
            throw new BusinessException("ABSENCE_SEANCE_REALISEE",
                    "Le patient a une séance réalisée à cette date : il n'est pas absent");
        }
        AbsencePatient a = AbsencePatient.declaree(centerId, patientId, dateSeance, motif, commentaire,
                valorisation.valoriser(centerId, patientId, dateSeance), utilisateur, aujourdhui(), Instant.now(clock));
        return absences.save(a);
    }

    public AbsencePatient qualifier(UUID centerId, UUID id, MotifAbsence motif, String commentaire, UUID utilisateur,
                                    boolean peutCorriger) {
        AbsencePatient a = charger(centerId, id);
        exigerDroitDeCorrection(a, peutCorriger);
        a.qualifier(motif, commentaire, utilisateur, Instant.now(clock));
        return absences.save(a);
    }

    public AbsencePatient rattraper(UUID centerId, UUID id, LocalDate dateRattrapage, UUID utilisateur) {
        AbsencePatient a = charger(centerId, id);
        if (!donnees.seanceRealisee(centerId, a.patientId(), dateRattrapage)) {
            throw new BusinessException("ABSENCE_RATTRAPAGE_SANS_SEANCE",
                    "Aucune séance réalisée pour ce patient à la date de rattrapage");
        }
        a.rattraper(dateRattrapage, utilisateur, aujourdhui(), Instant.now(clock));
        return absences.save(a);
    }

    public AbsencePatient annuler(UUID centerId, UUID id, String motifAnnulation, UUID utilisateur,
                                  boolean peutCorriger) {
        AbsencePatient a = charger(centerId, id);
        exigerDroitDeCorrection(a, peutCorriger);
        a.annuler(motifAnnulation, utilisateur, Instant.now(clock));
        return absences.save(a);
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
                    absences.save(a);
                    annulees++;
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

    public List<UUID> centres() {
        return donnees.centresActifs();
    }

    /**
     * Charge l'absence à modifier ; refusée si la facturation du patient est validée pour sa période (clôture).
     */
    private AbsencePatient charger(UUID centerId, UUID id) {
        AbsencePatient a = absences.findById(centerId, id)
                .orElseThrow(() -> new BusinessException("ABSENCE_INTROUVABLE", "Absence introuvable"));
        exigerPeriodeOuverte(centerId, a.patientId(), a.dateSeance());
        return a;
    }

    private void exigerPeriodeOuverte(UUID centerId, UUID patientId, LocalDate date) {
        if (donnees.periodeFacturee(centerId, patientId, date)) {
            throw new BusinessException("ABSENCE_PERIODE_FACTUREE",
                    "La facturation de cette période est validée : l'absence est clôturée");
        }
    }

    public record Synthese(long aQualifier, long enRetard) {
    }
}
