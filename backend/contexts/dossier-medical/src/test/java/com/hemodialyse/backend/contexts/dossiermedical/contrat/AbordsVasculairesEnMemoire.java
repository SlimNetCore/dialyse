package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link AbordsVasculaires} : mêmes ordres de lecture que l'adapter JDBC (jugé par le même contrat). */
public class AbordsVasculairesEnMemoire implements AbordsVasculaires {
    private final Map<UUID, AbordVasculaire> fiches = new LinkedHashMap<>();

    @Override
    public void ajouter(AbordVasculaire abord) {
        fiches.put(abord.id(), copie(abord));
    }

    @Override
    public void enregistrerLeRemplacement(AbordVasculaire abord) {
        fiches.put(abord.id(), copie(abord));
    }

    @Override
    public Optional<AbordVasculaire> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .map(AbordsVasculairesEnMemoire::copie);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        if (trouverEnVerrouillant(centre, patient, id).isEmpty()) return false;
        fiches.remove(id);
        return true;
    }

    @Override
    public PageDeFiches<AbordVasculaire> duPatient(CentreId centre, PatientId patient, int page, int taille) {
        Comparator<AbordVasculaire> parCreation = Comparator.comparing(AbordVasculaire::creation, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()));
        List<AbordVasculaire> tous = fiches.values().stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .sorted(parCreation.thenComparing(AbordVasculaire::enregistreLe, Comparator.reverseOrder())
                        .thenComparing(AbordVasculaire::id, Comparator.reverseOrder()))
                .map(AbordsVasculairesEnMemoire::copie).toList();
        int debut = (int) Math.min((long) page * taille, tous.size());
        return new PageDeFiches<>(tous.subList(debut, Math.min(debut + taille, tous.size())), tous.size(), page, taille);
    }

    private static AbordVasculaire copie(AbordVasculaire a) {
        return AbordVasculaire.reconstituer(a.id(), a.patient(), a.centre(), a.type(), a.cote(), a.localisation(), a.creation(), a.fin(),
                a.actif(), a.complications(), a.enregistreLe());
    }
}
