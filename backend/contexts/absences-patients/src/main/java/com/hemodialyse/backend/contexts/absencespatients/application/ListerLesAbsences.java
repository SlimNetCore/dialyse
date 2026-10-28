package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.FiltreDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.absencespatients.domain.Pagination;

import java.time.LocalDate;
import java.util.Map;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

/**
 * Liste paginée et filtrable des absences du centre (RG-ABS-050), avec le nom du patient et l'indicateur « en retard » au jour
 * courant.
 */
public class ListerLesAbsences {

    private final LectureDesAbsences lecture;
    private final NomsDesPatients noms;
    private final HorlogeDesCentres horloge;

    public ListerLesAbsences(LectureDesAbsences lecture, NomsDesPatients noms, HorlogeDesCentres horloge) {
        this.lecture = lecture;
        this.noms = noms;
        this.horloge = horloge;
    }

    public PageDeResultats<LigneDuSuivi> executer(CentreId centre, FiltreDesAbsences filtre, Pagination pagination) {
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        PageDeResultats<AbsenceSuivie> page = lecture.lister(centre, filtre, pagination);
        Map<PatientId, String> nomsDesPatients = noms.parPatient(centre,
                page.elements().stream().map(AbsenceSuivie::patient).toList());
        return new PageDeResultats<>(
                page.elements().stream()
                        .map(a -> new LigneDuSuivi(a, nomsDesPatients.get(a.patient()), a.enRetard(aujourdhui)))
                        .toList(),
                page.total(), page.page(), page.taille());
    }
}
