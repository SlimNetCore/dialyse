package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link Analyses} : mêmes ordres et mêmes filtres que l'adapter JDBC (jugé par le même contrat). */
public class AnalysesEnMemoire implements Analyses {
    private final Map<UUID, Analyse> fiches = new LinkedHashMap<>();

    @Override
    public void ajouter(Analyse analyse) {
        fiches.put(analyse.id(), copie(analyse));
    }

    @Override
    public void enregistrerLeRemplacement(Analyse analyse) {
        fiches.put(analyse.id(), copie(analyse));
    }

    @Override
    public Optional<Analyse> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .map(AnalysesEnMemoire::copie);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        if (trouverEnVerrouillant(centre, patient, id).isEmpty()) return false;
        fiches.remove(id);
        return true;
    }

    @Override
    public PageDeFiches<Analyse> duPatient(CentreId centre, PatientId patient, Periode periode, int page, int taille) {
        List<Analyse> toutes = fiches.values().stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .filter(a -> periode.du() == null || !a.datePrelevement().isBefore(periode.du()))
                .filter(a -> periode.au() == null || !a.datePrelevement().isAfter(periode.au()))
                .sorted(Comparator.comparing(Analyse::datePrelevement).thenComparing(Analyse::creeLe).thenComparing(Analyse::id).reversed())
                .map(AnalysesEnMemoire::copie).toList();
        int debut = (int) Math.min((long) page * taille, toutes.size());
        return new PageDeFiches<>(toutes.subList(debut, Math.min(debut + taille, toutes.size())), toutes.size(), page, taille);
    }

    private static Analyse copie(Analyse a) {
        return Analyse.reconstituer(a.id(), a.patient(), a.centre(), a.datePrelevement(), a.mesures(), a.creeLe(), a.modifieLe());
    }
}
