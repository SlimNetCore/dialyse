package com.hemodialyse.backend.contexts.absencespatients.domain;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;

import java.time.Instant;
import java.time.YearMonth;

/**
 * Une ligne du journal des clôtures et réouvertures d'un mois d'absences (append-only) : qui, quand, et pour une réouverture,
 * pourquoi ; pour une clôture, l'avertissement chiffré donné à l'administrateur.
 *
 * @param aQualifierRestantes absences « à qualifier » du mois à la clôture (clôture seulement)
 * @param enRetard            parmi elles, celles en retard de qualification (clôture seulement)
 * @param motif               renseigné pour une réouverture seulement
 */
public record EntreeDuJournalDuMois(Type type, CentreId centre, YearMonth mois, Instant le, UtilisateurId par,
                                    String motif,
                                    Long aQualifierRestantes, Long enRetard) {

    public static final int MOTIF_MAX = 500;

    static EntreeDuJournalDuMois cloture(CentreId centre, YearMonth mois, Instant le, UtilisateurId par, long aQualifierRestantes,
                                         long enRetard) {
        return new EntreeDuJournalDuMois(Type.CLOTURE, centre, mois, le, par, null, aQualifierRestantes, enRetard);
    }

    static EntreeDuJournalDuMois reouverture(CentreId centre, YearMonth mois, Instant le, UtilisateurId par, String motif) {
        return new EntreeDuJournalDuMois(Type.REOUVERTURE, centre, mois, le, par, motif, null, null);
    }

    public enum Type {CLOTURE, REOUVERTURE}
}
