package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceIntrouvable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SignalementDesSaisies;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Annule une absence (patient finalement présent, déclaration erronée) avec un commentaire (RG-ABS-003, 022). Une transaction,
 * sur l'absence verrouillée. Une absence rattrapée ne s'annule pas, ni celle que l'exclusion d'une séance a déclarée (réincluez la séance).
 */
public class AnnulerUneAbsence {

    private final UniteDeTravail uniteDeTravail;
    private final AbsencesRepository absences;
    private final PeriodesFacturees periodes;
    private final ExclusionsDeSeances exclusions;
    private final SignalementDesSaisies signalement;
    private final Clock horloge;

    public AnnulerUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences, PeriodesFacturees periodes,
                             ExclusionsDeSeances exclusions, SignalementDesSaisies signalement, Clock horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.absences = absences;
        this.periodes = periodes;
        this.exclusions = exclusions;
        this.signalement = signalement;
        this.horloge = horloge;
    }

    /**
     * @throws AbsenceIntrouvable absence inconnue de ce centre
     */
    public LigneDuSuivi executer(CentreId centre, AbsenceId id, String commentaire, Intervenant intervenant) {
        LocalDate aujourdhui = LocalDate.now(horloge);
        AbsencePatient absence = uniteDeTravail.executer(() -> {
            AbsencePatient a = absences.verrouiller(centre, id).orElseThrow(() -> new AbsenceIntrouvable(id));
            a.annuler(commentaire, intervenant.utilisateur(), intervenant.droit(),
                    exclusions.absenceIssueDUneExclusion(centre, id), periodes, Instant.now(horloge));
            absences.enregistrer(a);
            signalement.absenceSaisie(centre.valeur(), a.patient().valeur(), a.dateDeSeance());
            return a;
        });
        return new LigneDuSuivi(absence.vue(), null, absence.vue().enRetard(aujourdhui));
    }
}
