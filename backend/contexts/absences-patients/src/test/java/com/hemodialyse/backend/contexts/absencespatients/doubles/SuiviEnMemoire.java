package com.hemodialyse.backend.contexts.absencespatients.doubles;

import com.hemodialyse.backend.contexts.absencespatients.application.LectureDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.FiltreDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.NomDuPatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.absencespatients.domain.Pagination;
import com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Double en mémoire des lectures du suivi : passe le même contrat que les adaptateurs JDBC (H2 et PostgreSQL 16).
 */
public class SuiviEnMemoire implements LectureDesAbsences, SeancesRealisees, NomsDesPatients {

    private static final Set<String> STATUTS_REALISES = Set.of("VALIDEE", "SIGNEE", "FACTUREE");

    private record Ligne(CentreId centre, AbsenceSuivie absence) {
    }

    private record Patient(CentreId centre, PatientId id, String prenom, String nom) {
    }

    private record Seance(CentreId centre, PatientId patient, LocalDate date, String statut) {
    }

    private final List<Ligne> absences = new ArrayList<>();
    private final List<Patient> patients = new ArrayList<>();
    private final List<Seance> seances = new ArrayList<>();

    public void poserLePatient(CentreId centre, PatientId id, String prenom, String nom) {
        patients.add(new Patient(centre, id, prenom, nom));
    }

    public void poserLAbsence(CentreId centre, AbsenceSuivie absence) {
        absences.add(new Ligne(centre, absence));
    }

    public void poserLaSeance(CentreId centre, PatientId patient, LocalDate date, String statut) {
        seances.add(new Seance(centre, patient, date, statut));
    }

    @Override
    public java.util.Map<PatientId, String> parPatient(CentreId centre, java.util.Collection<PatientId> demandes) {
        java.util.Map<PatientId, String> noms = new java.util.HashMap<>();
        patients.stream().filter(p -> p.centre().equals(centre) && demandes.contains(p.id()))
                .forEach(p -> noms.put(p.id(), NomDuPatient.complet(p.prenom(), p.nom())));
        return noms;
    }

    private java.util.stream.Stream<AbsenceSuivie> duCentreStream(CentreId centre) {
        return absences.stream().filter(l -> l.centre().equals(centre)).map(Ligne::absence);
    }

    @Override
    public PageDeResultats<AbsenceSuivie> lister(CentreId centre, FiltreDesAbsences f, Pagination pagination) {
        List<AbsenceSuivie> retenues = duCentreStream(centre)
                .filter(a -> f.statut() == null || a.statut() == f.statut())
                .filter(a -> f.motif() == null || a.motif() == f.motif())
                .filter(a -> f.patient() == null || a.patient().equals(f.patient()))
                .filter(a -> f.du() == null || !a.dateDeSeance().isBefore(f.du()))
                .filter(a -> f.au() == null || !a.dateDeSeance().isAfter(f.au()))
                .sorted(Comparator.comparing(AbsenceSuivie::dateDeSeance).reversed()
                        .thenComparing(a -> a.id().valeur()))
                .toList();
        List<AbsenceSuivie> page = retenues.stream().skip(pagination.decalage()).limit(pagination.taille())
                .toList();
        return new PageDeResultats<>(page, retenues.size(), pagination.page(), pagination.taille());
    }

    @Override
    public List<AbsenceSuivie> absencesNonAnnuleesEntre(CentreId centre, LocalDate du, LocalDate au) {
        return duCentreStream(centre)
                .filter(a -> a.statut() != StatutAbsence.ANNULEE)
                .filter(a -> !a.dateDeSeance().isBefore(du) && !a.dateDeSeance().isAfter(au))
                .sorted(Comparator.comparing(AbsenceSuivie::dateDeSeance).thenComparing(a -> a.id().valeur()))
                .toList();
    }

    @Override
    public long nombreAQualifier(CentreId centre) {
        return duCentreStream(centre).filter(a -> a.statut() == StatutAbsence.A_QUALIFIER).count();
    }

    @Override
    public long nombreAQualifierAvant(CentreId centre, LocalDate limite) {
        return duCentreStream(centre)
                .filter(a -> a.statut() == StatutAbsence.A_QUALIFIER && a.dateDeSeance().isBefore(limite)).count();
    }

    @Override
    public long nombreAQualifierEntre(CentreId centre, LocalDate du, LocalDate au) {
        return duCentreStream(centre).filter(a -> a.statut() == StatutAbsence.A_QUALIFIER
                && !a.dateDeSeance().isBefore(du) && !a.dateDeSeance().isAfter(au)).count();
    }

    @Override
    public Optional<AbsenceSuivie> absence(CentreId centre, AbsenceId id) {
        return duCentreStream(centre).filter(a -> a.id().equals(id)).findFirst();
    }

    @Override
    public List<LocalDate> datesDeRattrapageUtilisees(CentreId centre, PatientId patient) {
        return duCentreStream(centre)
                .filter(a -> a.patient().equals(patient) && a.statut() == StatutAbsence.RATTRAPEE
                        && a.dateDeRattrapage() != null)
                .map(AbsenceSuivie::dateDeRattrapage).toList();
    }

    @Override
    public List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au) {
        return seances.stream()
                .filter(s -> s.centre().equals(centre) && STATUTS_REALISES.contains(s.statut()))
                .filter(s -> !s.date().isBefore(du) && !s.date().isAfter(au))
                .map(s -> new SeanceRealisee(s.patient(), s.date()))
                .distinct()
                .sorted(Comparator.comparing(SeanceRealisee::date))
                .toList();
    }

    @Override
    public List<LocalDate> joursDuPatient(CentreId centre, PatientId patient, LocalDate du, LocalDate au) {
        Set<LocalDate> jours = new TreeSet<>();
        seances.stream()
                .filter(s -> s.centre().equals(centre) && s.patient().equals(patient)
                        && STATUTS_REALISES.contains(s.statut()))
                .filter(s -> !s.date().isBefore(du) && !s.date().isAfter(au))
                .forEach(s -> jours.add(s.date()));
        return List.copyOf(jours);
    }

    @Override
    public boolean realisee(CentreId centre, PatientId patient, LocalDate jour) {
        return seances.stream().anyMatch(s -> s.centre().equals(centre) && s.patient().equals(patient)
                && s.date().equals(jour) && STATUTS_REALISES.contains(s.statut()));
    }
}
