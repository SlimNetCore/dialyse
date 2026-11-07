package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link Allergies} : mêmes ordres de lecture que l'adapter JDBC (jugé par le même contrat). */
public class AllergiesEnMemoire implements Allergies {
    private final Map<UUID, Allergie> fiches = new LinkedHashMap<>();

    @Override
    public void ajouter(Allergie allergie) {
        fiches.put(allergie.id(), copie(allergie));
    }

    @Override
    public void enregistrerLaCorrection(Allergie allergie) {
        fiches.put(allergie.id(), copie(allergie));
    }

    @Override
    public Optional<Allergie> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .map(AllergiesEnMemoire::copie);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        if (trouverEnVerrouillant(centre, patient, id).isEmpty()) return false;
        fiches.remove(id);
        return true;
    }

    @Override
    public PageDeFiches<Allergie> duPatient(CentreId centre, PatientId patient, int page, int taille) {
        List<Allergie> toutes = triees(centre, patient);
        int debut = (int) Math.min((long) page * taille, toutes.size());
        return new PageDeFiches<>(toutes.subList(debut, Math.min(debut + taille, toutes.size())), toutes.size(), page, taille);
    }

    @Override
    public List<Allergie> critiquesDuPatient(CentreId centre, PatientId patient) {
        return triees(centre, patient).stream().filter(Allergie::estCritique).toList();
    }

    private List<Allergie> triees(CentreId centre, PatientId patient) {
        return fiches.values().stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .sorted(Comparator.comparing(Allergie::dateDeConstatation).thenComparing(Allergie::creeLe).thenComparing(Allergie::id).reversed())
                .map(AllergiesEnMemoire::copie).toList();
    }

    private static Allergie copie(Allergie a) {
        return Allergie.reconstituer(a.id(), a.patient(), a.centre(), a.substance(), a.categorie(), a.criticite(), a.typeDeReaction(),
                a.manifestations(), a.dateDeConstatation(), a.statut(), a.creeLe(), a.modifieLe());
    }
}
