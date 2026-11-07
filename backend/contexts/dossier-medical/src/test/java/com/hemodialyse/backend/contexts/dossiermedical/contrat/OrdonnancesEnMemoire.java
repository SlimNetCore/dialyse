package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDOrdonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link Ordonnances} : mêmes ordres de lecture que l'adapter JDBC (jugé par le même contrat). */
public class OrdonnancesEnMemoire implements Ordonnances {

    private final Map<UUID, Ordonnance> fiches = new LinkedHashMap<>();

    @Override
    public void ajouter(Ordonnance ordonnance) {
        fiches.put(ordonnance.id(), copie(ordonnance, ordonnance.lignes()));
    }

    @Override
    public void enregistrerLaTransition(Ordonnance o) {
        fiches.put(o.id(), copie(o, fiches.get(o.id()).lignes()));
    }

    @Override
    public Optional<Ordonnance> trouver(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(o -> o.centre().equals(centre) && o.patient().equals(patient))
                .map(o -> copie(o, triees(o.lignes())));
    }

    @Override
    public Optional<Ordonnance> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return trouver(centre, patient, id);
    }

    @Override
    public PageDOrdonnances duPatient(CentreId centre, PatientId patient, int page, int taille) {
        List<Ordonnance> toutes = fiches.values().stream().filter(o -> o.centre().equals(centre) && o.patient().equals(patient))
                .sorted(Comparator.comparing(Ordonnance::datePrescription).thenComparing(Ordonnance::creeeLe).thenComparing(Ordonnance::id)
                        .reversed()).toList();
        int debut = (int) Math.min((long) page * taille, toutes.size());
        List<Ordonnance> items = toutes.subList(debut, Math.min(debut + taille, toutes.size())).stream()
                .map(o -> copie(o, triees(o.lignes()))).toList();
        return new PageDOrdonnances(items, toutes.size(), page, taille);
    }

    private static List<LigneDOrdonnance> triees(List<LigneDOrdonnance> lignes) {
        List<LigneDOrdonnance> triees = new ArrayList<>(lignes);
        triees.sort(Comparator.comparing(LigneDOrdonnance::libelle, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LigneDOrdonnance::posologie).thenComparing(LigneDOrdonnance::id));
        return triees;
    }

    private static Ordonnance copie(Ordonnance o, List<LigneDOrdonnance> lignes) {
        return Ordonnance.reconstituer(o.id(), o.patient(), o.centre(), o.medecin(), o.datePrescription(), o.statut(), o.numero(), lignes,
                o.creeeLe(), o.modifieeLe(), o.signeeLe());
    }
}
