package com.hemodialyse.backend.application.patient;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.MouvementPatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.service.MouvementsPatient;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Libère les places des patients sortis (transfert, décès, greffe, guérison) ou dont le séjour est terminé : la fiche
 * perd sa salle, son créneau, son générateur et ses jours de dialyse, et un mouvement {@code PLACE_LIBEREE} fige
 * l'affectation quittée. Le planning, la charge des infirmiers et les propositions de placement retrouvent ainsi la
 * place disponible.
 */
@Service
public class LiberationPlacesService {

    private final PatientRepositoryPort patients;
    private final MouvementPatientRepositoryPort mouvements;
    private final Clock clock;

    @Autowired
    public LiberationPlacesService(PatientRepositoryPort patients, MouvementPatientRepositoryPort mouvements) {
        this(patients, mouvements, Clock.systemUTC());
    }

    LiberationPlacesService(PatientRepositoryPort patients, MouvementPatientRepositoryPort mouvements, Clock clock) {
        this.patients = patients;
        this.mouvements = mouvements;
        this.clock = clock;
    }

    /**
     * Centres qui comptent au moins un patient affecté.
     */
    public List<CenterId> centres() {
        return patients.findCentersWithAssignments();
    }

    /**
     * Libère les places échues d'un centre (dernier jour d'occupation dépassé à la date du jour).
     *
     * @return nombre de places libérées
     */
    @Transactional
    @CacheEvict(cacheNames = {"patient.list.summary", "patient.list.summary.details"}, allEntries = true)
    public int libererCentre(CenterId centerId) {
        LocalDate aujourdhui = LocalDate.now(clock);
        int liberees = 0;
        for (Patient patient : patients.findWithAssignment(centerId)) {
            if (libererSiEchue(patient, aujourdhui)) liberees++;
        }
        return liberees;
    }

    /**
     * Libère la place d'une fiche si son échéance est dépassée à {@code aujourdhui}.
     *
     * @return {@code true} si la place a été libérée
     */
    @Transactional
    boolean libererSiEchue(Patient patient, LocalDate aujourdhui) {
        if (!MouvementsPatient.placeAReprendre(patient, aujourdhui)) return false;
        mouvements.save(MouvementsPatient.placeLiberee(patient, aujourdhui, Instant.now(clock)));
        patient.libererPlacement();
        patients.save(patient);
        return true;
    }

    /**
     * Libération à l'enregistrement d'une fiche dont l'échéance est déjà passée de plus d'un jour : la veille reste
     * conservée pour que le contrôle des absences de cette veille voie encore les jours de dialyse.
     */
    @Transactional
    public boolean libererALEnregistrement(Patient patient) {
        return libererSiEchue(patient, LocalDate.now(clock).minusDays(1));
    }
}
