package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceIntrouvable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SignalementDesSaisies;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Qualifie une absence par un motif, ou la requalifie (RG-ABS-004, 005, 020). Une transaction, sur l'absence verrouillée : une
 * écriture concurrente sur la même absence attend sa fin. La valeur de l'absence n'est jamais recalculée.
 */
public class QualifierUneAbsence {

    private final UniteDeTravail uniteDeTravail;
    private final AbsencesRepository absences;
    private final PeriodesFacturees periodes;
    private final SignalementDesSaisies signalement;
    private final Clock horloge;

    public QualifierUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences, PeriodesFacturees periodes,
                               SignalementDesSaisies signalement, Clock horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.absences = absences;
        this.periodes = periodes;
        this.signalement = signalement;
        this.horloge = horloge;
    }

    /**
     * @throws AbsenceIntrouvable absence inconnue de ce centre
     */
    public LigneDuSuivi executer(CentreId centre, AbsenceId id, MotifAbsence motif, String commentaire, Intervenant intervenant) {
        LocalDate aujourdhui = LocalDate.now(horloge);
        AbsencePatient absence = uniteDeTravail.executer(() -> {
            AbsencePatient a = absences.verrouiller(centre, id).orElseThrow(() -> new AbsenceIntrouvable(id));
            a.qualifier(motif, commentaire, intervenant.utilisateur(), intervenant.droit(), periodes, Instant.now(horloge));
            absences.enregistrer(a);
            signalement.absenceSaisie(centre.valeur(), a.patient().valeur(), a.dateDeSeance());
            return a;
        });
        return new LigneDuSuivi(absence.vue(), null, absence.vue().enRetard(aujourdhui));
    }
}
