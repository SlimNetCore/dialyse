package com.hemodialyse.backend.domain.infirmier.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Absence d'un infirmier sur une période (bornes incluses) : elle se superpose à son roulement.
 */
public record AbsenceInfirmier(
        UUID id,
        UUID centerId,
        UUID infirmierId,
        LocalDate debut,
        LocalDate fin,
        TypeAbsence type,
        String motif
) {
    /**
     * Durée maximale d'une absence déclarée (un an) : au-delà, la saisie est probablement erronée.
     */
    public static final int DUREE_MAX_JOURS = 366;

    public AbsenceInfirmier {
        if (centerId == null) throw new IllegalArgumentException("Centre requis");
        if (infirmierId == null) throw new IllegalArgumentException("Infirmier requis");
        if (debut == null || fin == null) throw new IllegalArgumentException("Période requise");
        if (fin.isBefore(debut)) throw new IllegalArgumentException("La fin de l'absence précède son début");
        if (ChronoUnit.DAYS.between(debut, fin) + 1 > DUREE_MAX_JOURS) {
            throw new IllegalArgumentException("Une absence ne peut pas dépasser " + DUREE_MAX_JOURS + " jours");
        }
        if (type == null) throw new IllegalArgumentException("Type d'absence requis");
        motif = motif == null || motif.isBlank() ? null : motif.trim();
    }

    public static AbsenceInfirmier creer(UUID centerId, UUID infirmierId, LocalDate debut, LocalDate fin,
                                         TypeAbsence type, String motif) {
        return new AbsenceInfirmier(UUID.randomUUID(), centerId, infirmierId, debut, fin, type, motif);
    }

    public boolean couvre(LocalDate date) {
        return !date.isBefore(debut) && !date.isAfter(fin);
    }

    /**
     * Un infirmier ne peut retirer lui-même que ses absences à venir (début strictement postérieur à aujourd'hui) :
     * une absence commencée ou passée relève de l'administration.
     */
    public boolean annulableParInfirmier(LocalDate aujourdhui) {
        return debut.isAfter(aujourdhui);
    }
}
