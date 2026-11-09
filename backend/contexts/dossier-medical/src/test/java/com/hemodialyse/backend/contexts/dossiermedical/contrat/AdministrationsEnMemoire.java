package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AdministrationDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Double en mémoire du port {@link Administrations} (mêmes filtres et ordres que l'adapter JDBC, jugé par le même contrat). */
public class AdministrationsEnMemoire implements Administrations {

    private final List<AdministrationDeTraitement> fiches = new ArrayList<>();

    @Override
    public void ajouter(AdministrationDeTraitement administration) {
        fiches.add(administration);
    }

    @Override
    public PageDAdministrations duPatient(CentreId centre, PatientId patient, int page, int taille) {
        List<AdministrationDeTraitement> toutes = lesPlusRecentes(centre, patient, Integer.MAX_VALUE);
        int debut = (int) Math.min((long) page * taille, toutes.size());
        return new PageDAdministrations(toutes.subList(debut, Math.min(debut + taille, toutes.size())), toutes.size(), page, taille);
    }

    @Override
    public List<AdministrationDeTraitement> lesPlusRecentes(CentreId centre, PatientId patient, int nombre) {
        return fiches.stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .sorted(Comparator.comparing(AdministrationDeTraitement::jour).thenComparing(AdministrationDeTraitement::creeeLe)
                        .thenComparing(AdministrationDeTraitement::id).reversed()).limit(nombre).toList();
    }

    @Override
    public int effacerCellesDeLaSeance(CentreId centre, java.util.UUID seance) {
        int avant = fiches.size();
        fiches.removeIf(a -> a.centre().equals(centre) && seance.equals(a.seance()));
        return avant - fiches.size();
    }

    @Override
    public Administre administreSur(CentreId centre, PatientId patient, TypeDeTraitement traitement, LocalDate debut, LocalDate fin) {
        var retenues = fiches.stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient) && a.traitement() == traitement
                && a.administree() && !a.jour().isBefore(debut) && !a.jour().isAfter(fin)).toList();
        long dose = retenues.stream().filter(a -> a.dose() != null && traitement.unite().equalsIgnoreCase(a.dose().unite()))
                .mapToLong(a -> a.dose().valeur().longValue()).sum();
        return new Administre(retenues.size(), dose);
    }
}
