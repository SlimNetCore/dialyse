package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ce qu'un passage de détection et de réconciliation a fait : absences créées et annulées, échecs isolés (une absence en échec
 * n'arrête pas les autres), jours ignorés car leur mois d'absences est clos.
 */
public record BilanDuControle(int creees, int annulees, List<Echec> echecs, List<LocalDate> joursIgnores) {

    public BilanDuControle {
        echecs = List.copyOf(echecs);
        joursIgnores = List.copyOf(joursIgnores);
    }

    /** Un incident sur une absence : le patient, le jour de la séance manquée, l'opération et la cause (pour le journal). */
    public record Echec(PatientId patient, LocalDate jour, String operation, String cause) {
    }

    public BilanDuControle avec(BilanDuControle autre) {
        List<Echec> tous = new ArrayList<>(echecs);
        tous.addAll(autre.echecs);
        List<LocalDate> ignores = new ArrayList<>(joursIgnores);
        for (LocalDate jour : autre.joursIgnores) {
            if (!ignores.contains(jour)) ignores.add(jour);
        }
        return new BilanDuControle(creees + autre.creees, annulees + autre.annulees, tous, ignores);
    }
}
