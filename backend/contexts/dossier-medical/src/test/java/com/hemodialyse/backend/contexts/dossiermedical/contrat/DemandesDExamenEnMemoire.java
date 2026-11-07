package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDeDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeDemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link DemandesDExamen} : mêmes ordres de lecture que l'adapter JDBC (jugé par le même contrat). */
public class DemandesDExamenEnMemoire implements DemandesDExamen {

    private final Map<UUID, DemandeExamen> fiches = new LinkedHashMap<>();

    @Override
    public void ajouter(DemandeExamen demande) {
        fiches.put(demande.id(), copie(demande, demande.lignes()));
    }

    @Override
    public void enregistrerLaTransition(DemandeExamen demande) {
        fiches.put(demande.id(), copie(demande, fiches.get(demande.id()).lignes()));
    }

    @Override
    public Optional<DemandeExamen> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(d -> d.centre().equals(centre) && d.patient().equals(patient))
                .map(d -> copie(d, triees(d.lignes())));
    }

    @Override
    public PageDeDemandesDExamen duPatient(CentreId centre, PatientId patient, int page, int taille) {
        List<DemandeExamen> toutes = fiches.values().stream().filter(d -> d.centre().equals(centre) && d.patient().equals(patient))
                .sorted(Comparator.comparing(DemandeExamen::dateDemande).thenComparing(DemandeExamen::creeeLe)
                        .thenComparing(DemandeExamen::id).reversed()).toList();
        int debut = (int) Math.min((long) page * taille, toutes.size());
        List<DemandeExamen> items = toutes.subList(debut, Math.min(debut + taille, toutes.size())).stream()
                .map(d -> copie(d, triees(d.lignes()))).toList();
        return new PageDeDemandesDExamen(items, toutes.size(), page, taille);
    }

    private static List<LigneDeDemandeDExamen> triees(List<LigneDeDemandeDExamen> lignes) {
        List<LigneDeDemandeDExamen> triees = new ArrayList<>(lignes);
        triees.sort(Comparator.comparing(LigneDeDemandeDExamen::libelle, Comparator.nullsLast(Comparator.<String>naturalOrder()))
                .thenComparing(l -> l.code() == null ? "" : l.code().code())
                .thenComparing(LigneDeDemandeDExamen::commentaire, Comparator.nullsFirst(Comparator.<String>naturalOrder()))
                .thenComparing(LigneDeDemandeDExamen::id));
        return triees;
    }

    private static DemandeExamen copie(DemandeExamen d, List<LigneDeDemandeDExamen> lignes) {
        return DemandeExamen.reconstituer(d.id(), d.patient(), d.centre(), d.prescripteur(), d.dateDemande(), d.categorie(), d.urgent(),
                d.motif(), d.statut(), d.conclusion(), lignes, d.creeeLe(), d.modifieeLe());
    }
}
