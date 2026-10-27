package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.PatientDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PresenceDesPatients;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Lecture publiée de la présence des patients : une seule règle ({@code SejourDuPatient}) pour Absences et la Direction. */
public class ConsulterLaPresenceDesPatients implements PresenceDesPatients {

    private final SejoursDesPatients patients;

    public ConsulterLaPresenceDesPatients(SejoursDesPatients patients) {
        this.patients = patients;
    }

    @Override
    public List<UUID> attendusLe(UUID centre, LocalDate jour) {
        JourDeLaSemaine ceJour = JourDeLaSemaine.de(jour.getDayOfWeek());
        return patients.duCentreSansRisque(new CentreId(centre)).stream()
                .filter(p -> p.place().jours().contains(ceJour) && p.sejour().estAttenduLe(jour))
                // ordre des identifiants comme la base (octets non signés = ordre lexicographique de l'écriture hexadécimale)
                .map(PatientDeLaSemaine::patient).map(p -> p.valeur()).sorted(Comparator.comparing(UUID::toString)).toList();
    }

    @Override
    public long fileActive(UUID centre, LocalDate du, LocalDate au) {
        return patients.duCentreSansRisque(new CentreId(centre)).stream().filter(p -> p.sejour().estPresentSur(du, au)).count();
    }
}
