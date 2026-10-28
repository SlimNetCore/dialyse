package com.hemodialyse.backend.contexts.absencespatients.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceIntrouvable;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.FaitsDuRattrapage;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SignalementDesSaisies;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Rattrape une absence par une séance réalisée du patient (RG-ABS-021, 022). Une transaction qui verrouille toutes les absences
 * du patient : deux absences ne peuvent pas se rattraper en même temps par la même séance.
 */
public class RattraperUneAbsence {

    private final UniteDeTravail uniteDeTravail;
    private final AbsencesRepository absences;
    private final PeriodesFacturees periodes;
    private final SeancesRealisees seances;
    private final SignalementDesSaisies signalement;
    private final HorlogeDesCentres horloge;
    private final GardeDesMoisDAbsences garde;

    public RattraperUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences, PeriodesFacturees periodes,
                               SeancesRealisees seances, SignalementDesSaisies signalement, HorlogeDesCentres horloge,
                               GardeDesMoisDAbsences garde) {
        this.garde = garde;
        this.uniteDeTravail = uniteDeTravail;
        this.absences = absences;
        this.periodes = periodes;
        this.seances = seances;
        this.signalement = signalement;
        this.horloge = horloge;
    }

    /**
     * @throws AbsenceIntrouvable absence inconnue de ce centre
     */
    public LigneDuSuivi executer(CentreId centre, AbsenceId id, LocalDate dateDeRattrapage, Intervenant intervenant) {
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        AbsencePatient absence = uniteDeTravail.executer(() -> {
            AbsencesRepository.AbsenceEtRattrapages lue = absences.verrouillerPourRattrapage(centre, id)
                    .orElseThrow(() -> new AbsenceIntrouvable(id));
            AbsencePatient a = lue.absence();
            List<LocalDate> utilisees = lue.datesDeRattrapageUtilisees();
            FaitsDuRattrapage faits = new FaitsDuRattrapage() {
                @Override
                public boolean seanceRealisee(LocalDate jour) {
                    return seances.realisee(centre, a.patient(), jour);
                }

                @Override
                public boolean dejaUtilisee(LocalDate jour) {
                    return utilisees.contains(jour);
                }
            };
            // le mois de l'absence puis celui de la date de rattrapage, du plus ancien au plus récent (pas d'interblocage)
            garde.exigerOuvert(centre, a.dateDeSeance());
            if (dateDeRattrapage != null && dateDeRattrapage.isAfter(a.dateDeSeance())) {
                garde.exigerOuvert(centre, dateDeRattrapage);
            }
            a.rattraper(dateDeRattrapage, intervenant.utilisateur(), intervenant.droit(), periodes, faits, aujourdhui,
                    horloge.maintenant());
            absences.enregistrer(a);
            signalement.absenceSaisie(centre.valeur(), a.patient().valeur(), a.dateDeSeance());
            return a;
        });
        return new LigneDuSuivi(absence.vue(), null, absence.vue().enRetard(aujourdhui));
    }
}
