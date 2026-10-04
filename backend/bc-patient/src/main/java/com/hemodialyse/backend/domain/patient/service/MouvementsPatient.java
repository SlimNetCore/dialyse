package com.hemodialyse.backend.domain.patient.service;

import com.hemodialyse.backend.domain.patient.model.MouvementPatient;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.patient.vo.JoursDialyse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fabrique des mouvements de patients (service de domaine pur) : décide quand un enregistrement ou une libération de
 * place constitue un mouvement à tracer, et en fige l'affectation occupée.
 */
public final class MouvementsPatient {

    private MouvementsPatient() {
    }

    /**
     * Mouvement d'admission d'un patient qui vient d'être créé.
     */
    public static MouvementPatient admission(Patient patient, Instant maintenant) {
        return nouveau(patient, TypeMouvementPatient.ADMISSION, patient.getDateAdmission(), null, false, maintenant);
    }

    /**
     * Mouvement produit par un enregistrement de la fiche : vide si ni l'état ni la date d'évènement n'ont changé.
     *
     * @param patient    fiche après enregistrement
     * @param etatAvant  état avant enregistrement
     * @param dateAvant  date d'évènement avant enregistrement
     * @param aujourdhui date de repli lorsque l'évènement n'a pas de date (reprise à l'état permanent)
     */
    public static Optional<MouvementPatient> changementEtat(Patient patient, String etatAvant, LocalDate dateAvant,
                                                            LocalDate aujourdhui, Instant maintenant) {
        if (Objects.equals(etatAvant, patient.getEtatPatient())
                && Objects.equals(dateAvant, patient.getDateEvenementEtat())) {
            return Optional.empty();
        }
        LocalDate dateEffet = patient.getDateEvenementEtat() != null ? patient.getDateEvenementEtat() : aujourdhui;
        return Optional.of(nouveau(patient, TypeMouvementPatient.pourEtat(patient.getEtatPatient()), dateEffet,
                etatAvant, false, maintenant));
    }

    /**
     * Mouvement de libération de la place, à produire <b>avant</b> que la fiche ne perde son affectation.
     */
    public static MouvementPatient placeLiberee(Patient patient, LocalDate aujourdhui, Instant maintenant) {
        return nouveau(patient, TypeMouvementPatient.PLACE_LIBEREE, aujourdhui, patient.getEtatPatient(), true,
                maintenant);
    }

    /**
     * La fiche porte-t-elle encore une affectation (salle, créneau) alors que sa place est libérée ?
     */
    public static boolean placeAReprendre(Patient patient, LocalDate aujourdhui) {
        boolean affecte = patient.getSalleId() != null || patient.getPositionId() != null
                || patient.getGenerateurId() != null;
        return affecte && FinOccupation.placeLiberee(patient.getEtatPatient(), patient.getDateEvenementEtat(), aujourdhui);
    }

    static String jours(JoursDialyse j) {
        if (j == null) return "";
        List<String> codes = new ArrayList<>();
        if (j.dimanche()) codes.add("DIMANCHE");
        if (j.lundi()) codes.add("LUNDI");
        if (j.mardi()) codes.add("MARDI");
        if (j.mercredi()) codes.add("MERCREDI");
        if (j.jeudi()) codes.add("JEUDI");
        if (j.vendredi()) codes.add("VENDREDI");
        if (j.samedi()) codes.add("SAMEDI");
        return String.join(",", codes);
    }

    private static MouvementPatient nouveau(Patient p, TypeMouvementPatient type, LocalDate dateEffet,
                                            String etatPrecedent, boolean automatique, Instant maintenant) {
        return new MouvementPatient(UUID.randomUUID(), p.getCenterId().value(), p.getId().value(), type, dateEffet,
                etatPrecedent, p.getEtatPatient(), p.getSalleId(), p.getPositionId(), p.getGenerateurId(),
                jours(p.getJoursDialyse()), automatique, maintenant);
    }
}
