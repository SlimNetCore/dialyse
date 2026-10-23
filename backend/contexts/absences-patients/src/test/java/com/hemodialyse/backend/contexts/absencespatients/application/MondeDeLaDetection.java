package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.doubles.AbsencesEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.doubles.SuiviEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.doubles.UniteDeTravailEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.Valorisation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
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
 * Le monde de la détection de nuit en mémoire : les ports sortants sont des doubles, les politiques et cas d'usage les vrais.
 * Horloge fixée au 22 octobre 2026 à 10:00 UTC ; tous les centres en UTC sauf demande contraire.
 */
final class MondeDeLaDetection {

    static final CentreId CENTRE = new CentreId(new UUID(10, 1));
    static final CentreId AUTRE_CENTRE = new CentreId(new UUID(10, 2));
    static final PatientId AMRANI = new PatientId(new UUID(11, 1));
    static final PatientId BELKACEM = new PatientId(new UUID(11, 2));
    static final PatientId CHERIF = new PatientId(new UUID(11, 3));
    static final ForfaitId FORFAIT = new ForfaitId(new UUID(12, 1));
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 22);
    static final LocalDate HIER = AUJOURDHUI.minusDays(1);
    static final Instant MIDI = Instant.parse("2026-10-22T10:00:00Z");

    final AbsencesEnMemoire absences = new AbsencesEnMemoire() {
        @Override
        public void enregistrer(AbsencePatient absence) {
            if (enregistrementsEnEchec.contains(absence.id())) throw new IllegalStateException("base indisponible");
            super.enregistrer(absence);
        }
    };
    final SuiviEnMemoire seances = new SuiviEnMemoire();
    final LectureDesAbsences lecture = new LectureDeLaBase();
    final UniteDeTravailEnMemoire unite = new UniteDeTravailEnMemoire(absences);
    final Map<String, List<PatientId>> attendus = new HashMap<>();
    final Set<CentreId> centresEnIncident = new HashSet<>();
    final Set<AbsenceId> enregistrementsEnEchec = new HashSet<>();
    final Set<String> joursFactures = new HashSet<>();
    final Set<YearMonth> moisClos = new HashSet<>();
    final Map<PatientId, ForfaitApplicable> forfaits = new HashMap<>();
    final List<CentreId> centres = new ArrayList<>(List.of(CENTRE));
    final Map<CentreId, ZoneId> fuseaux = new HashMap<>();
    final List<String> notifications = new ArrayList<>();
    final Set<String> notificationsDuJour = new HashSet<>();
    Instant instant = MIDI;
    private final AtomicInteger identifiant = new AtomicInteger();

    final ReconciliationAbsences reconciliation;
    final DetectionAbsences detection;
    final NotificationDesAbsencesAQualifier notification;
    final ControlerLesAbsences controler;
    final DetecterUnePeriodePassee detecterUnePeriode;

    /** Lecture du suivi dont le compte « à qualifier » lit les absences réellement écrites par la détection. */
    private final class LectureDeLaBase extends SuiviEnMemoire {
        @Override
        public long nombreAQualifier(CentreId centre) {
            return nombreAQualifierEntre(centre, LocalDate.MIN, LocalDate.MAX);
        }

        @Override
        public long nombreAQualifierAvant(CentreId centre, LocalDate limite) {
            return nombreAQualifierEntre(centre, LocalDate.MIN, limite.minusDays(1));
        }

        @Override
        public long nombreAQualifierEntre(CentreId centre, LocalDate du, LocalDate au) {
            return absences.toutes().stream().filter(a -> a.centre().equals(centre) && a.statut() == StatutAbsence.A_QUALIFIER
                    && !a.dateDeSeance().isBefore(du) && !a.dateDeSeance().isAfter(au)).count();
        }
    }

    MondeDeLaDetection() {
        forfaits.put(AMRANI, new ForfaitApplicable(FORFAIT, "Forfait HD", new BigDecimal("10000.00")));
        PatientsAttendus patientsAttendus = (centre, jour) -> {
            if (centresEnIncident.contains(centre)) throw new IllegalStateException("base indisponible");
            return attendus.getOrDefault(cle(centre, jour), List.of());
        };
        var periodes = (com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees)
                (centre, patient, jour) -> joursFactures.contains(cle(centre, patient, jour));
        var mois = new com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesOuverts() {
            @Override
            public boolean estOuvert(CentreId centre, YearMonth m) {
                return !moisClos.contains(m);
            }

            @Override
            public List<YearMonth> moisClos(CentreId centre) {
                return List.copyOf(moisClos);
            }
        };
        ForfaitsDesPatients forfaitsDuMonde = (centre, patient, jour) -> Optional.ofNullable(forfaits.get(patient));
        TauxDeTva taux = (centre, jour) -> new BigDecimal("19");
        GenerateurIdentifiants ids = () -> new AbsenceId(new UUID(20, identifiant.incrementAndGet()));
        reconciliation = new ReconciliationAbsences(unite, absences, this.seances, periodes, mois);
        detection = new DetectionAbsences(unite, patientsAttendus, absences, periodes, mois, forfaitsDuMonde, taux, ids);
        notification = new NotificationDesAbsencesAQualifier(lecture, mois, new com.hemodialyse.backend.contexts.absencespatients
                .publishedlanguage.NotificationsDuCentre() {
            @Override
            public boolean signalerLesAbsencesAQualifier(UUID centre, LocalDate jour, long aQualifier, long enRetard) {
                if (!notificationsDuJour.add(centre + "|" + jour)) {
                    return false;
                }
                notifications.add(centre + "/" + aQualifier + "/" + enRetard);
                return true;
            }
        });
        HorlogeDesCentres horloge = new HorlogeDesCentres(horlogeMutable(ZoneOffset.UTC),
                centre -> fuseaux.getOrDefault(centre, ZoneOffset.UTC));
        controler = new ControlerLesAbsences(() -> List.copyOf(centres), horloge, reconciliation, detection, notification);
        detecterUnePeriode = new DetecterUnePeriodePassee(horloge, reconciliation, detection);
    }

    private Clock horlogeMutable(ZoneId zone) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId nouvelle) {
                return horlogeMutable(nouvelle);
            }

            @Override
            public Instant instant() {
                return instant;
            }
        };
    }

    static String cle(CentreId centre, LocalDate jour) {
        return centre.valeur() + "/" + jour;
    }

    static String cle(CentreId centre, PatientId patient, LocalDate jour) {
        return centre.valeur() + "/" + patient.valeur() + "/" + jour;
    }

    void attendre(CentreId centre, LocalDate jour, PatientId... patients) {
        attendus.computeIfAbsent(cle(centre, jour), k -> new ArrayList<>()).addAll(List.of(patients));
    }

    void facturer(PatientId patient, LocalDate jour) {
        joursFactures.add(cle(CENTRE, patient, jour));
    }

    /** Pose une absence enregistrée dans l'état, la source et le motif donnés. */
    AbsencePatient poser(int rang, CentreId centre, PatientId patient, LocalDate jour, SourceAbsence source, StatutAbsence statut) {
        Valorisation valeur = Valorisation.depuis(null, new BigDecimal("19"));
        AbsencePatient absence = AbsencePatient.reconstituer(new AbsenceId(new UUID(30, rang)), centre, patient, jour, source,
                valeur, null, MIDI, statut, statut == StatutAbsence.A_QUALIFIER || statut == StatutAbsence.ANNULEE ? null
                        : MotifAbsence.MALADIE, statut == StatutAbsence.ANNULEE ? "annulée" : null, null, null, null, null, null);
        absences.creer(absence);
        return absence;
    }

    List<AbsencePatient> absencesDe(CentreId centre) {
        return absences.toutes().stream().filter(a -> a.centre().equals(centre)).toList();
    }
}
