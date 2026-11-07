package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesureDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Traitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * L'observance en cours d'un patient : pour chaque traitement de la prescription la plus récente (seule celle-là est contrôlée,
 * KB-MED-28 e), la période courante et ce qui y a été administré. Un traitement sans article, sans fréquence positive ou sans unité de
 * fréquence n'est pas suivi. Le « jour » est celui de l'horloge injectée (jour de la JVM tant que le jour du centre n'est pas décidé :
 * KB-MED-15, à confirmer). Droits : MEDECIN, ADMIN et INFIRMIER.
 */
public class ConsulterLObservance {

    /** Observance d'un traitement sur sa période courante. */
    public record Observance(LocalDate debut, LocalDate fin, int dosesAttendues, int dosesAdministrees, int dosesRestantes, long joursRestants,
                             String uniteDose, Integer dosePrescrite, Integer doseAttendue, Integer doseAdministree, Integer doseRestante) {
    }

    /** L'observance de l'EPO et du fer (vide quand le traitement n'est pas suivi). */
    public record Observances(Optional<Observance> epo, Optional<Observance> fer) {
    }

    private final SessionCourante session;
    private final Prescriptions prescriptions;
    private final Administrations administrations;
    private final HorlogeDuCentre horloge;

    public ConsulterLObservance(SessionCourante session, Prescriptions prescriptions, Administrations administrations, HorlogeDuCentre horloge) {
        this.session = session;
        this.prescriptions = prescriptions;
        this.administrations = administrations;
        this.horloge = horloge;
    }

    public Observances executer(UUID centreDemande, UUID patientDemande) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        var derniere = prescriptions.laPlusRecente(centre, patient);
        if (derniere.isEmpty()) return new Observances(Optional.empty(), Optional.empty());
        var contenu = derniere.get().contenu();
        return new Observances(calculer(centre, patient, TypeDeTraitement.EPO, contenu.date(), contenu.epo()),
                calculer(centre, patient, TypeDeTraitement.FER_INJECTABLE, contenu.date(), contenu.fer()));
    }

    private Optional<Observance> calculer(CentreId centre, PatientId patient, TypeDeTraitement type, LocalDate datePrescription, Traitement t) {
        if (t.article() == null || t.frequenceValeur() == null || t.frequenceValeur() <= 0 || t.frequence() == null) return Optional.empty();
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        PeriodeDObservance courante = PeriodeDObservance.courante(datePrescription, PeriodeDObservance.fenetreEnJours(t.frequence().name()), aujourdhui);
        var administre = administrations.administreSur(centre, patient, type, courante.debut(), courante.fin());
        MesureDObservance m = MesureDObservance.calculer(t.frequenceValeur(), t.dose(), type.unite(), administre.nombre(), administre.dose());
        return Optional.of(new Observance(courante.debut(), courante.fin(), t.frequenceValeur(), m.administrations(), m.restantes(),
                courante.joursRestants(aujourdhui), m.unite(), m.dosePrescrite(), m.enDose() ? m.attendu() : null,
                m.enDose() ? m.administre() : null, m.enDose() ? m.restant() : null));
    }
}
