package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDeStatistiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Tension;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.StatistiquesMedicalesPubliees;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Les doubles des statistiques du patient (PL4) : session, patients du centre, seances et mesures medicales en memoire, avec trace des appels. */
final class FixtureDesStatistiques {

    static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 3, 15);

    final List<String> appels = new ArrayList<>();
    final Set<UUID> patientsDuCentre = new java.util.HashSet<>(Set.of(PATIENT));
    CentreId centreDeLaSession = CENTRE;

    SeancesDuPatient.MesuresDesSeances seances = new SeancesDuPatient.MesuresDesSeances(0, null, null, null, List.of(), List.of());
    Double poidsSec = null;
    LocalDate poidsSecDemandePour;
    StatistiquesMedicalesPubliees.Moyennes moyennes = new StatistiquesMedicalesPubliees.Moyennes(0.0, 0.0, 0.0);
    List<Map<String, Object>> bilans = List.of();
    List<Map<String, Object>> epo = List.of();
    List<Map<String, Object>> exportBilans = List.of();
    PeriodeDeStatistiques periodeLue;

    final SessionCourante session = demande -> {
        appels.add("session");
        if (centreDeLaSession == null || (demande != null && !demande.equals(centreDeLaSession.valeur()))) throw new AccesRefuse();
        return centreDeLaSession;
    };

    final PatientsDuCentre patients = (centre, patient) -> {
        appels.add("patient");
        return patientsDuCentre.contains(patient.valeur());
    };

    final SeancesDuPatient lectureDesSeances = (centre, patient, periode) -> {
        appels.add("seances");
        periodeLue = periode;
        return seances;
    };

    final StatistiquesMedicalesPubliees medicales = new StatistiquesMedicalesPubliees() {
        @Override
        public Moyennes moyennes(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            appels.add("moyennes");
            return moyennes;
        }

        @Override
        public List<Map<String, Object>> tendanceDesBilans(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            appels.add("bilans");
            return bilans;
        }

        @Override
        public List<Map<String, Object>> tendanceDesPrescriptionsDEpo(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            appels.add("epo");
            return epo;
        }

        @Override
        public List<Map<String, Object>> lignesDExport(UUID centre, UUID patient, LocalDate du, LocalDate au) {
            appels.add("export");
            return exportBilans;
        }

        @Override
        public Optional<Double> poidsSecCibleEnVigueur(UUID centre, UUID patient, LocalDate au) {
            appels.add("poids sec");
            poidsSecDemandePour = au;
            return Optional.ofNullable(poidsSec);
        }
    };

    final HorlogeDuCentre horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);

    static PatientId patient() {
        return new PatientId(PATIENT);
    }

    static Map<String, Object> ligne(Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) m.put((String) paires[i], paires[i + 1]);
        return m;
    }

    static SeancesDuPatient.TensionsDeSeance tensions(LocalDate jour, String avant, String apres) {
        return new SeancesDuPatient.TensionsDeSeance(jour, Tension.lire(avant), Tension.lire(apres));
    }
}
