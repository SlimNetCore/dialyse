package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.patientcouverture.application.PlaceDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.LiberationDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * ACL du port {@link PlaceDeLaFiche} vers le langage publié de la Planification ({@code PlacementDesPatients},
 * {@code LiberationDesPlaces}, ADR 0052, P5 et P7) : la fiche ne parle que de ses propres termes (place, jours), la
 * Planification contrôle et écrit la place dans la transaction de l'appelant, sous le verrou du centre. Les refus de la
 * Planification ({@code PlacementDesPatients.Refus}, {@code EcritureConcurrente}) traversent tels quels.
 */
public class PlaceDeLaFicheParLaPlanification implements PlaceDeLaFiche {

    private final PlacementDesPatients placements;
    private final LiberationDesPlaces liberation;

    public PlaceDeLaFicheParLaPlanification(PlacementDesPatients placements, LiberationDesPlaces liberation) {
        this.placements = placements;
        this.liberation = liberation;
    }

    @Override
    public void verifier(CentreId centre, PatientId patient, Affectation a, EtatDuPatient etatAVenir, LocalDate dateAVenir) {
        placements.verifier(centre.valeur(), identifiant(patient), a.salleId(), a.creneauId(), a.generateurId(), jours(a),
                etatAVenir == null ? null : etatAVenir.name(), dateAVenir);
    }

    @Override
    public void placer(CentreId centre, PatientId patient, Affectation a, EtatDuPatient etatAVenir, LocalDate dateAVenir) {
        placements.placer(centre.valeur(), identifiant(patient), a.salleId(), a.creneauId(), a.generateurId(), jours(a),
                etatAVenir == null ? null : etatAVenir.name(), dateAVenir);
    }

    @Override
    public boolean libererSiEchue(CentreId centre, PatientId patient) {
        return liberation.siEchue(centre.valeur(), patient.valeur());
    }

    private static UUID identifiant(PatientId patient) {
        return patient == null ? null : patient.valeur();
    }

    private static Set<DayOfWeek> jours(Affectation a) {
        Affectation.Jours j = a.jours();
        Set<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
        if (j.dimanche()) jours.add(DayOfWeek.SUNDAY);
        if (j.lundi()) jours.add(DayOfWeek.MONDAY);
        if (j.mardi()) jours.add(DayOfWeek.TUESDAY);
        if (j.mercredi()) jours.add(DayOfWeek.WEDNESDAY);
        if (j.jeudi()) jours.add(DayOfWeek.THURSDAY);
        if (j.vendredi()) jours.add(DayOfWeek.FRIDAY);
        if (j.samedi()) jours.add(DayOfWeek.SATURDAY);
        return jours;
    }
}
