package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.DateOuverte;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;
import com.hemodialyse.backend.contexts.absencespatients.domain.RegleDAbsenceViolee;
import com.hemodialyse.backend.contexts.absencespatients.domain.Valorisation;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.SignalementDesSaisies;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Déclare l'absence d'un patient à une séance prévue (RG-ABS-010 à 013, 024, 030). Une transaction : patient du centre, jour
 * libre (une absence par patient et par jour, même annulée), période non facturée, pas de séance réalisée ce jour-là, puis la
 * valeur est figée (forfait applicable, taux du jour) et l'absence créée. Utilisé aussi par l'exclusion d'une séance de la
 * Facturation. Le mois d'absences de la date doit être ouvert (A6, {@code MOIS_D_ABSENCES_CLOS}, ligne du mois verrouillée dans
 * la transaction). Déclarations simultanées du même jour : la perdante reçoit {@code EcritureConcurrente} (409).
 */
public class DeclarerUneAbsence {

    private final UniteDeTravail uniteDeTravail;
    private final PatientsDuCentre patients;
    private final AbsencesRepository absences;
    private final PeriodesFacturees periodes;
    private final SeancesRealisees seances;
    private final ForfaitsDesPatients forfaits;
    private final TauxDeTva taux;
    private final GenerateurIdentifiants identifiants;
    private final SignalementDesSaisies signalement;
    private final HorlogeDesCentres horloge;
    private final GardeDesMoisDAbsences garde;

    @SuppressWarnings("java:S107")
    public DeclarerUneAbsence(UniteDeTravail uniteDeTravail, PatientsDuCentre patients, AbsencesRepository absences,
                              PeriodesFacturees periodes, SeancesRealisees seances, ForfaitsDesPatients forfaits,
                              TauxDeTva taux, GenerateurIdentifiants identifiants, SignalementDesSaisies signalement,
                              HorlogeDesCentres horloge, GardeDesMoisDAbsences garde) {
        this.garde = garde;
        this.uniteDeTravail = uniteDeTravail;
        this.patients = patients;
        this.absences = absences;
        this.periodes = periodes;
        this.seances = seances;
        this.forfaits = forfaits;
        this.taux = taux;
        this.identifiants = identifiants;
        this.signalement = signalement;
        this.horloge = horloge;
    }

    /**
     * @param motif       facultatif : avec motif, l'absence est qualifiée d'emblée
     * @param commentaire facultatif (conservé même sans motif)
     */
    public record Commande(PatientId patient, LocalDate dateDeSeance, MotifAbsence motif, String commentaire) {
    }

    public LigneDuSuivi executer(CentreId centre, Commande commande, Intervenant intervenant) {
        LocalDate aujourdhui = horloge.aujourdhui(centre);
        AbsencePatient absence = uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, commande.patient())) {
                throw new RegleDAbsenceViolee("PATIENT_INTROUVABLE", "Patient introuvable");
            }
            if (absences.existe(centre, commande.patient(), commande.dateDeSeance())) {
                throw new RegleDAbsenceViolee("ABSENCE_EXISTANTE", "Une absence existe déjà pour ce patient à cette date");
            }
            DateOuverte ouverte = DateOuverte.exiger(periodes, centre, commande.patient(), commande.dateDeSeance());
            garde.exigerOuvert(centre, commande.dateDeSeance());
            if (seances.realisee(centre, commande.patient(), commande.dateDeSeance())) {
                throw new RegleDAbsenceViolee("ABSENCE_SEANCE_REALISEE",
                        "Le patient a une séance réalisée à cette date : il n'est pas absent");
            }
            Valorisation valeur = Valorisation.depuis(
                    forfaits.applicable(centre, commande.patient(), commande.dateDeSeance()).orElse(null),
                    taux.pour(centre, commande.dateDeSeance()));
            AbsencePatient creee = AbsencePatient.declarer(identifiants.nouvelleAbsence(), ouverte, commande.motif(),
                    commande.commentaire(), valeur, intervenant.utilisateur(), aujourdhui, horloge.maintenant());
            absences.creer(creee);
            signalement.absenceSaisie(centre.valeur(), creee.patient().valeur(), creee.dateDeSeance());
            return creee;
        });
        return new LigneDuSuivi(absence.vue(), null, absence.vue().enRetard(aujourdhui));
    }
}
