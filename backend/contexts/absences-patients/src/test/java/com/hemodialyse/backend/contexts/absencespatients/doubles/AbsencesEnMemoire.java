package com.hemodialyse.backend.contexts.absencespatients.doubles;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Double en mémoire des absences enregistrées : passe le même contrat que l'adaptateur JDBC (H2 et PostgreSQL 16). Il stocke des
 * copies (comme une base) et tient les deux contraintes de la base : une absence par patient et par jour, une séance de
 * rattrapage par absence rattrapée (index unique partiel de PostgreSQL). Il sait prendre un instantané pour défaire une
 * transaction ({@link UniteDeTravailEnMemoire}).
 */
public class AbsencesEnMemoire implements AbsencesRepository {

    private final Map<AbsenceId, AbsencePatient> lignes = new LinkedHashMap<>();

    private static AbsencePatient copie(AbsencePatient a) {
        return AbsencePatient.reconstituer(a.id(), a.centre(), a.patient(), a.dateDeSeance(), a.source(), a.valorisation(),
                a.declareePar(), a.declareeLe(), a.statut(), a.motif(), a.commentaire(), a.qualifieePar(), a.qualifieeLe(),
                a.dateDeRattrapage(), a.modifieePar(), a.modifieeLe());
    }

    public int nombre() {
        return lignes.size();
    }

    public List<AbsencePatient> toutes() {
        return lignes.values().stream().map(AbsencesEnMemoire::copie).toList();
    }

    public Optional<AbsencePatient> lire(AbsenceId id) {
        return Optional.ofNullable(lignes.get(id)).map(AbsencesEnMemoire::copie);
    }

    Map<AbsenceId, AbsencePatient> instantane() {
        Map<AbsenceId, AbsencePatient> photo = new LinkedHashMap<>();
        lignes.forEach((id, a) -> photo.put(id, copie(a)));
        return photo;
    }

    void restaurer(Map<AbsenceId, AbsencePatient> photo) {
        lignes.clear();
        photo.forEach((id, a) -> lignes.put(id, copie(a)));
    }

    @Override
    public Optional<AbsencePatient> verrouiller(CentreId centre, AbsenceId id) {
        return lire(id).filter(a -> a.centre().equals(centre));
    }

    @Override
    public Optional<AbsenceEtRattrapages> verrouillerPourRattrapage(CentreId centre, AbsenceId id) {
        return verrouiller(centre, id).map(absence -> new AbsenceEtRattrapages(absence, lignes.values().stream()
                .filter(a -> a.centre().equals(centre) && a.patient().equals(absence.patient())
                        && a.statut() == StatutAbsence.RATTRAPEE && a.dateDeRattrapage() != null)
                .map(AbsencePatient::dateDeRattrapage).toList()));
    }

    @Override
    public boolean existe(CentreId centre, PatientId patient, LocalDate jour) {
        return lignes.values().stream().anyMatch(a -> a.centre().equals(centre) && a.patient().equals(patient)
                && a.dateDeSeance().equals(jour));
    }

    @Override
    public List<AbsencePatient> detecteesAQualifierEntre(CentreId centre, LocalDate du, LocalDate au, AbsenceId apres,
                                                         int limite) {
        return lignes.values().stream()
                .filter(a -> a.centre().equals(centre) && a.source() == com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence.AUTOMATIQUE
                        && a.statut() == StatutAbsence.A_QUALIFIER && !a.dateDeSeance().isBefore(du) && !a.dateDeSeance().isAfter(au)
                        && (apres == null || a.id().valeur().compareTo(apres.valeur()) > 0))
                .sorted(java.util.Comparator.comparing(a -> a.id().valeur()))
                .limit(limite).map(AbsencesEnMemoire::copie).toList();
    }

    @Override
    public void creer(AbsencePatient absence) {
        if (existe(absence.centre(), absence.patient(), absence.dateDeSeance()) || lignes.containsKey(absence.id())) {
            throw new EcritureConcurrente();
        }
        lignes.put(absence.id(), copie(absence));
    }

    @Override
    public void enregistrer(AbsencePatient absence) {
        if (!lignes.containsKey(absence.id())) {
            throw new EcritureConcurrente();
        }
        List<AbsencePatient> autres = new ArrayList<>(lignes.values());
        autres.removeIf(a -> a.id().equals(absence.id()));
        boolean dateDejaPrise = absence.statut() == StatutAbsence.RATTRAPEE && absence.dateDeRattrapage() != null
                && autres.stream().anyMatch(a -> a.statut() == StatutAbsence.RATTRAPEE
                && a.centre().equals(absence.centre()) && a.patient().equals(absence.patient())
                && absence.dateDeRattrapage().equals(a.dateDeRattrapage()));
        if (dateDejaPrise) {
            throw new EcritureConcurrente();
        }
        lignes.put(absence.id(), copie(absence));
    }

    @Override
    public void supprimer(CentreId centre, AbsenceId id) {
        AbsencePatient a = lignes.get(id);
        if (a != null && a.centre().equals(centre)) {
            lignes.remove(id);
        }
    }
}
