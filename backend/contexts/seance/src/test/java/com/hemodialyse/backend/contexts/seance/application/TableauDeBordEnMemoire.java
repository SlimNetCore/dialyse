package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Demographie;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientDuMois;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Doubles en mémoire des ports de lecture du tableau de bord (SE7b) : séances réalisées, patients, absences, jours non ouvrés. Un
 * patient est rangé dans le centre qui le tient ; une séance l'est dans le centre où elle a lieu (le patient peut être d'un autre centre :
 * KB-SEA-64).
 */
final class TableauDeBordEnMemoire implements SeancesRealisees, PatientsDuTableauDeBord, AbsencesDuCentre {

    final Map<CentreId, List<PatientDuMois>> patientsParCentre = new HashMap<>();
    final Map<CentreId, Map<PatientId, Identite>> identitesParCentre = new HashMap<>();
    final Map<CentreId, List<SeanceRealisee>> seancesParCentre = new HashMap<>();
    final Map<CentreId, List<Demographie>> demographiesParPatient = new HashMap<>();
    final Map<CentreId, List<AbsenceDuCentre>> absencesParCentre = new HashMap<>();
    final Map<CentreId, Set<LocalDate>> bloquesParCentre = new HashMap<>();
    /** Dernier intervalle demandé aux absences : prouve que le futur n'est jamais lu. */
    LocalDate dernierDebutDemande;
    LocalDate derniereFinDemandee;
    int lecturesDesAbsences;

    void patient(CentreId centre, PatientDuMois patient, String nom, String prenom) {
        patientsParCentre.computeIfAbsent(centre, c -> new ArrayList<>()).add(patient);
        identitesParCentre.computeIfAbsent(centre, c -> new HashMap<>()).put(patient.id(), new Identite(nom, prenom));
    }

    void seance(CentreId centre, PatientId patient, LocalDate jour, String statut, Demographie demographie) {
        seancesParCentre.computeIfAbsent(centre, c -> new ArrayList<>()).add(new SeanceRealisee(patient, jour, statut));
        demographiesParPatient.computeIfAbsent(centre, c -> new ArrayList<>()).add(demographie);
    }

    void absence(CentreId centre, PatientId patient, LocalDate jour, String statut) {
        absencesParCentre.computeIfAbsent(centre, c -> new ArrayList<>()).add(new AbsenceDuCentre(patient, jour, statut));
    }

    void bloquer(CentreId centre, LocalDate... jours) {
        bloquesParCentre.computeIfAbsent(centre, c -> new HashSet<>()).addAll(List.of(jours));
    }

    // ---- SeancesRealisees

    @Override
    public long nombre(CentreId centre, MoisDuTableauDeBord mois) {
        return du(centre, mois).size();
    }

    @Override
    public List<SeanceRealisee> du(CentreId centre, MoisDuTableauDeBord mois) {
        return seancesParCentre.getOrDefault(centre, List.of()).stream()
                .filter(s -> List.of("VALIDEE", "SIGNEE", "FACTUREE").contains(s.statut()))
                .filter(s -> !s.jour().isBefore(mois.premierJour()) && !s.jour().isAfter(mois.dernierJour())).toList();
    }

    // ---- PatientsDuTableauDeBord

    @Override
    public List<PatientDuMois> duCentre(CentreId centre) {
        return patientsParCentre.getOrDefault(centre, List.of());
    }

    @Override
    public List<Demographie> presentsDuMois(CentreId centre, MoisDuTableauDeBord mois) {
        List<Demographie> presents = new ArrayList<>();
        List<SeanceRealisee> seances = seancesParCentre.getOrDefault(centre, List.of());
        List<Demographie> demographies = demographiesParPatient.getOrDefault(centre, List.of());
        for (int i = 0; i < seances.size(); i++) {
            var s = seances.get(i);
            boolean duMois = du(centre, mois).contains(s);
            if (duMois && presents.stream().noneMatch(d -> d.patient().equals(s.patient()))) presents.add(demographies.get(i));
        }
        return presents;
    }

    @Override
    public Map<PatientId, Identite> identites(CentreId centre) {
        return identitesParCentre.getOrDefault(centre, Map.of());
    }

    // ---- AbsencesDuCentre

    @Override
    public long nombre(CentreId centre, LocalDate du, LocalDate au) {
        return liste(centre, du, au).size();
    }

    @Override
    public List<AbsenceDuCentre> liste(CentreId centre, LocalDate du, LocalDate au) {
        lecturesDesAbsences++;
        dernierDebutDemande = du;
        derniereFinDemandee = au;
        return absencesParCentre.getOrDefault(centre, List.of()).stream().filter(a -> !a.jour().isBefore(du) && !a.jour().isAfter(au)).toList();
    }

    // ---- JoursBloques

    JoursBloques jours() {
        return (centre, mois) -> bloquesParCentre.getOrDefault(centre, Set.of());
    }
}
