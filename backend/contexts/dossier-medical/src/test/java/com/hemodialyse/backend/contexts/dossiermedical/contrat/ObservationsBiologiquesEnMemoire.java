package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
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

/** Double en mémoire du port {@link ObservationsBiologiques} : mêmes ordres et mêmes filtres que l'adapter JDBC (jugé par le même contrat). */
public class ObservationsBiologiquesEnMemoire implements ObservationsBiologiques {
    private final Map<UUID, ObservationBiologique> fiches = new LinkedHashMap<>();

    @Override
    public void ajouter(ObservationBiologique observation) {
        fiches.put(observation.id(), copie(observation));
    }

    @Override
    public void enregistrerLaCorrection(ObservationBiologique observation) {
        fiches.put(observation.id(), copie(observation));
    }

    @Override
    public Optional<ObservationBiologique> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return Optional.ofNullable(fiches.get(id)).filter(o -> o.centre().equals(centre) && o.patient().equals(patient))
                .map(ObservationsBiologiquesEnMemoire::copie);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        if (trouverEnVerrouillant(centre, patient, id).isEmpty()) return false;
        fiches.remove(id);
        return true;
    }

    @Override
    public PageDeFiches<ObservationBiologique> duPatient(CentreId centre, PatientId patient, String code, LocalDate du, LocalDate au, int page,
                                                         int taille) {
        List<ObservationBiologique> toutes = triees(centre, patient).stream()
                .filter(o -> code == null || code.isBlank() || o.analyte().code().equals(code))
                .filter(o -> du == null || !o.datePrelevement().isBefore(du))
                .filter(o -> au == null || !o.datePrelevement().isAfter(au)).toList();
        int debut = (int) Math.min((long) page * taille, toutes.size());
        return new PageDeFiches<>(toutes.subList(debut, Math.min(debut + taille, toutes.size())), toutes.size(), page, taille);
    }

    @Override
    public List<ObservationBiologique> deLaDemande(CentreId centre, PatientId patient, UUID demandeDExamen) {
        return triees(centre, patient).stream().filter(o -> demandeDExamen.equals(o.demandeDExamen())).toList();
    }

    private List<ObservationBiologique> triees(CentreId centre, PatientId patient) {
        return fiches.values().stream().filter(o -> o.centre().equals(centre) && o.patient().equals(patient))
                .sorted(Comparator.comparing(ObservationBiologique::datePrelevement).thenComparing(ObservationBiologique::creeLe)
                        .thenComparing(ObservationBiologique::id).reversed())
                .map(ObservationsBiologiquesEnMemoire::copie).toList();
    }

    private static ObservationBiologique copie(ObservationBiologique o) {
        return ObservationBiologique.reconstituer(o.id(), o.patient(), o.centre(), o.demandeDExamen(), o.analyte(), o.valeurNumerique(),
                o.valeurTexte(), o.datePrelevement(), o.statut(), o.source(), o.creeLe(), o.modifieLe());
    }
}
