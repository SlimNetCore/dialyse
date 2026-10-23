package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.application.BilanDuControle.Echec;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.DateOuverte;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesOuverts;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodeDeDetection;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;
import com.hemodialyse.backend.contexts.absencespatients.domain.Valorisation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Politique de détection (RG-ABS-040, 041, 042) : pour chaque jour de la période et chaque patient attendu sans séance
 * réalisée, une absence « à qualifier » est créée si aucune n'existe déjà (même annulée), si la période du patient n'est pas
 * facturée et si le mois d'absences est ouvert. Idempotente. Une absence = une transaction : un incident (valorisation refusée...)
 * n'arrête que cette absence, le reste du centre continue (KB-ABS-27 corrigé). Une requête « patients attendus » par jour.
 */
public class DetectionAbsences {

    private final UniteDeTravail uniteDeTravail;
    private final PatientsAttendus attendus;
    private final AbsencesRepository absences;
    private final PeriodesFacturees periodes;
    private final MoisDAbsencesOuverts mois;
    private final ForfaitsDesPatients forfaits;
    private final TauxDeTva taux;
    private final GenerateurIdentifiants identifiants;

    @SuppressWarnings("java:S107")
    public DetectionAbsences(UniteDeTravail uniteDeTravail, PatientsAttendus attendus, AbsencesRepository absences,
                             PeriodesFacturees periodes, MoisDAbsencesOuverts mois, ForfaitsDesPatients forfaits,
                             TauxDeTva taux, GenerateurIdentifiants identifiants) {
        this.uniteDeTravail = uniteDeTravail;
        this.attendus = attendus;
        this.absences = absences;
        this.periodes = periodes;
        this.mois = mois;
        this.forfaits = forfaits;
        this.taux = taux;
        this.identifiants = identifiants;
    }

    public BilanDuControle executer(CentreId centre, PeriodeDeDetection periode, Instant maintenant) {
        int creees = 0;
        List<Echec> echecs = new ArrayList<>();
        List<LocalDate> ignores = new ArrayList<>();
        Map<YearMonth, Boolean> ouverts = new HashMap<>();
        for (LocalDate jour : periode.jours()) {
            if (!ouverts.computeIfAbsent(YearMonth.from(jour), m -> mois.estOuvert(centre, m))) {
                ignores.add(jour);
                continue;
            }
            for (PatientId patient : attendus.sansSeanceRealisee(centre, jour)) {
                try {
                    if (detecter(centre, patient, jour, maintenant)) creees++;
                } catch (RuntimeException e) {
                    echecs.add(new Echec(patient, jour, "détection", ReconciliationAbsences.cause(e)));
                }
            }
        }
        return new BilanDuControle(creees, 0, echecs, ignores);
    }

    private boolean detecter(CentreId centre, PatientId patient, LocalDate jour, Instant maintenant) {
        try {
            return uniteDeTravail.executer(() -> {
                if (absences.existe(centre, patient, jour)) {
                    return false;
                }
                Optional<DateOuverte> ouverte = DateOuverte.siOuverte(periodes, mois, centre, patient, jour);
                if (ouverte.isEmpty()) {
                    return false;
                }
                Valorisation valeur = Valorisation.depuis(forfaits.applicable(centre, patient, jour).orElse(null),
                        taux.pour(centre, jour));
                absences.creer(AbsencePatient.detecter(identifiants.nouvelleAbsence(), ouverte.get(), valeur, maintenant));
                return true;
            });
        } catch (EcritureConcurrente dejaPrise) {
            // une déclaration vient de prendre ce jour : l'absence existe, la détection a atteint son but ; sinon c'est un verrou
            // (mois en cours de clôture, délai dépassé) : l'absence n'existe pas, l'incident est compté dans le bilan et journalisé
            if (absences.existe(centre, patient, jour)) {
                return false;
            }
            throw dejaPrise;
        }
    }
}
