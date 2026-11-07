package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAlerte;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeTraitement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link Alertes} (mêmes filtres et ordres que l'adapter JDBC, jugé par le même contrat). */
public class AlertesEnMemoire implements Alertes {

    private final List<AlerteDObservance> fiches = new ArrayList<>();

    @Override
    public void ajouter(AlerteDObservance alerte) {
        fiches.add(copie(alerte));
    }

    @Override
    public void enregistrerLaResolution(AlerteDObservance alerte) {
        fiches.replaceAll(a -> a.id().equals(alerte.id()) ? copie(alerte) : a);
    }

    @Override
    public List<AlerteDObservance> duPatient(CentreId centre, PatientId patient) {
        return fiches.stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .sorted(Comparator.comparing(AlerteDObservance::creeeLe).thenComparing(AlerteDObservance::id).reversed()).map(AlertesEnMemoire::copie).toList();
    }

    @Override
    public Optional<AlerteDObservance> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return fiches.stream().filter(a -> a.id().equals(id) && a.centre().equals(centre) && a.patient().equals(patient)).findFirst()
                .map(AlertesEnMemoire::copie);
    }

    @Override
    public Optional<AlerteDObservance> ouverte(CentreId centre, PatientId patient, TypeDeTraitement traitement, TypeDAlerte nature) {
        return duPatient(centre, patient).stream().filter(a -> a.traitement() == traitement && a.nature() == nature && a.resolueLe() == null)
                .findFirst();
    }

    private static AlerteDObservance copie(AlerteDObservance a) {
        return AlerteDObservance.reconstituer(a.id(), a.patient(), a.centre(), a.traitement(), a.nature(), a.debut(), a.fin(), a.attendues(),
                a.administrees(), a.message(), a.detail(), a.creeeLe(), a.resolueLe());
    }
}
