package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link Antecedents} : mêmes ordres de lecture que l'adapter JDBC (jugé par le même contrat). */
public class AntecedentsEnMemoire implements Antecedents {
    private final Map<UUID, Antecedent> fiches = new LinkedHashMap<>();

    @Override
    public boolean unDiagnosticActifExisteEnVerrouillant(CentreId centre, PatientId patient, CodeClinique diagnostic, UUID sauf) {
        return fiches.values().stream().anyMatch(a -> a.centre().equals(centre) && a.patient().equals(patient)
                && a.statut() == StatutClinique.ACTIF && !a.id().equals(sauf) && diagnostic.memeCodeQue(a.diagnostic()));
    }

    @Override
    public void ajouter(Antecedent antecedent) {
        fiches.put(antecedent.id(), copie(antecedent));
    }

    @Override
    public void enregistrerLaCorrection(Antecedent antecedent) {
        fiches.put(antecedent.id(), copie(antecedent));
    }

    @Override
    public Optional<Antecedent> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .map(AntecedentsEnMemoire::copie);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        if (trouverEnVerrouillant(centre, patient, id).isEmpty()) return false;
        fiches.remove(id);
        return true;
    }

    @Override
    public List<Antecedent> tousDuPatient(CentreId centre, PatientId patient) {
        return fiches.values().stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .sorted(Comparator.comparing(Antecedent::debut).thenComparing(Antecedent::creeLe).thenComparing(Antecedent::id).reversed())
                .map(AntecedentsEnMemoire::copie).toList();
    }

    @Override
    public PageDeFiches<Antecedent> duPatient(CentreId centre, PatientId patient, int page, int taille) {
        List<Antecedent> toutes = tousDuPatient(centre, patient);
        int debut = (int) Math.min((long) page * taille, toutes.size());
        return new PageDeFiches<>(toutes.subList(debut, Math.min(debut + taille, toutes.size())), toutes.size(), page, taille);
    }

    private static Antecedent copie(Antecedent a) {
        return Antecedent.reconstituer(a.id(), a.patient(), a.centre(), a.type(), a.diagnostic(), a.libelleLibre(), a.debut(), a.fin(),
                a.statut(), a.severite(), a.note(), a.creeLe(), a.modifieLe());
    }
}
