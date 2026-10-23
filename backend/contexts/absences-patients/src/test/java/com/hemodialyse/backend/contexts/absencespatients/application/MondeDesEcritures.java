package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.doubles.AbsencesEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.doubles.SuiviEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.doubles.UniteDeTravailEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection;
import com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Le monde des écritures d'absences en mémoire : les ports sortants sont des doubles, les cas d'usage les vrais. Horloge fixée
 * au 22 octobre 2026 à 08:00 UTC.
 */
final class MondeDesEcritures {

    static final CentreId CENTRE = new CentreId(new UUID(10, 1));
    static final CentreId AUTRE_CENTRE = new CentreId(new UUID(10, 2));
    static final PatientId AMRANI = new PatientId(new UUID(11, 1));
    static final PatientId BELKACEM = new PatientId(new UUID(11, 2));
    static final ForfaitId FORFAIT = new ForfaitId(new UUID(12, 1));
    static final UtilisateurId SECRETAIRE = new UtilisateurId(new UUID(13, 1));
    static final UtilisateurId MEDECIN = new UtilisateurId(new UUID(13, 2));
    static final Intervenant SANS_DROIT = new Intervenant(SECRETAIRE, DroitDeCorrection.REFUSE);
    static final Intervenant AVEC_DROIT = new Intervenant(MEDECIN, DroitDeCorrection.ACCORDE);
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 22);
    static final LocalDate SEANCE = AUJOURDHUI.minusDays(5);
    static final Instant MAINTENANT = Instant.parse("2026-10-22T08:00:00Z");

    final AbsencesEnMemoire absences = new AbsencesEnMemoire();
    final SuiviEnMemoire suivi = new SuiviEnMemoire();
    final UniteDeTravailEnMemoire unite = new UniteDeTravailEnMemoire(absences);
    final com.hemodialyse.backend.contexts.absencespatients.doubles.MoisAbsencesEnMemoire mois =
            new com.hemodialyse.backend.contexts.absencespatients.doubles.MoisAbsencesEnMemoire();
    final GardeDesMoisDAbsences garde = new GardeDesMoisDAbsences(mois);
    /**
     * Lecture dont les comptes « à qualifier » portent sur les absences réellement écrites.
     */
    final SuiviEnMemoire lectureDesAbsences = new SuiviEnMemoire() {
        @Override
        public long nombreAQualifierEntre(CentreId centre, LocalDate du, LocalDate au) {
            return absences.toutes().stream().filter(a -> a.centre().equals(centre)
                    && a.statut() == com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence.A_QUALIFIER
                    && !a.dateDeSeance().isBefore(du) && !a.dateDeSeance().isAfter(au)).count();
        }
    };
    final List<Object> evenementsDeMois = new ArrayList<>();
    final Set<String> patients = new HashSet<>();
    final Set<String> joursFactures = new HashSet<>();
    final Map<PatientId, ForfaitApplicable> forfaits = new HashMap<>();
    final List<String> signalements = new ArrayList<>();
    final Set<AbsenceId> absencesDExclusion = new HashSet<>();
    BigDecimal taux = new BigDecimal("19");
    private final AtomicInteger identifiant = new AtomicInteger();
    Instant instant = MAINTENANT;
    final Clock horloge = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    };

    final PeriodesFacturees periodes = (centre, patient, jour) -> joursFactures.contains(cle(centre, patient, jour));
    final DeclarerUneAbsence declarer;
    final QualifierUneAbsence qualifier;
    final RattraperUneAbsence rattraper;
    final AnnulerUneAbsence annuler;
    final RetirerLAbsenceDeLExclusion retirer;
    final CloturerLeMoisDAbsences cloturer;
    final RouvrirLeMoisDAbsences rouvrir;

    MondeDesEcritures() {
        patients.add(AMRANI.valeur() + "@" + CENTRE.valeur());
        patients.add(BELKACEM.valeur() + "@" + CENTRE.valeur());
        forfaits.put(AMRANI, new ForfaitApplicable(FORFAIT, "Forfait HD", new BigDecimal("10000.00")));
        PatientsDuCentre existants = (centre, patient) -> patients.contains(patient.valeur() + "@" + centre.valeur());
        ForfaitsDesPatients forfaitsDuMonde = (centre, patient, jour) -> Optional.ofNullable(forfaits.get(patient));
        TauxDeTva tauxDuMonde = (centre, jour) -> taux;
        GenerateurIdentifiants ids = () -> new AbsenceId(new UUID(20, identifiant.incrementAndGet()));
        declarer = new DeclarerUneAbsence(unite, existants, absences, periodes, suivi, forfaitsDuMonde, tauxDuMonde, ids,
                (centre, patient, jour) -> signalements.add(centre + "/" + patient + "/" + jour), HorlogeDesCentres.enUtc(horloge), garde);
        qualifier = new QualifierUneAbsence(unite, absences, periodes,
                (centre, patient, jour) -> signalements.add(centre + "/" + patient + "/" + jour), HorlogeDesCentres.enUtc(horloge), garde);
        rattraper = new RattraperUneAbsence(unite, absences, periodes, suivi,
                (centre, patient, jour) -> signalements.add(centre + "/" + patient + "/" + jour), HorlogeDesCentres.enUtc(horloge), garde);
        annuler = new AnnulerUneAbsence(unite, absences, periodes, (centre, absence) -> absencesDExclusion.contains(absence),
                (centre, patient, jour) -> signalements.add(centre + "/" + patient + "/" + jour), HorlogeDesCentres.enUtc(horloge), garde);
        retirer = new RetirerLAbsenceDeLExclusion(unite, absences, garde);
        PublicationDesMoisDAbsences publication = new PublicationDesMoisDAbsences() {
            @Override
            public void cloture(com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesCloture e) {
                evenementsDeMois.add(e);
            }

            @Override
            public void rouvert(com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesRouvert e) {
                evenementsDeMois.add(e);
            }
        };
        cloturer = new CloturerLeMoisDAbsences(mois, lectureDesAbsences, publication, unite, HorlogeDesCentres.enUtc(horloge));
        rouvrir = new RouvrirLeMoisDAbsences(mois, publication, unite, HorlogeDesCentres.enUtc(horloge));
    }

    private static String cle(CentreId centre, PatientId patient, LocalDate jour) {
        return centre.valeur() + "/" + patient.valeur() + "/" + jour;
    }

    /** Avance l'horloge du monde. */
    void plusTard() {
        instant = instant.plusSeconds(3600);
    }

    void facturer(PatientId patient, LocalDate jour) {
        joursFactures.add(cle(CENTRE, patient, jour));
    }

    DeclarerUneAbsence.Commande commande(PatientId patient, LocalDate jour, MotifAbsence motif, String commentaire) {
        return new DeclarerUneAbsence.Commande(patient, jour, motif, commentaire);
    }

    /** Déclare l'absence de la secrétaire et rend sa lecture dans la base. */
    AbsencePatient declaree(PatientId patient, LocalDate jour, MotifAbsence motif) {
        LigneDuSuivi ligne = declarer.executer(CENTRE, commande(patient, jour, motif, null), SANS_DROIT);
        return absences.lire(ligne.absence().id()).orElseThrow();
    }

}
