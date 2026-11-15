package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link OrdonnancesAImprimer} : mêmes réponses que l'adapter JDBC (contrat commun). */
public class OrdonnancesAImprimerEnMemoire implements OrdonnancesAImprimer {

    private record Cle(CentreId centre, UUID id) {
    }

    private final Map<Cle, OrdonnanceImprimable> ordonnances = new HashMap<>();

    public void poser(CentreId centre, UUID id, UUID patient, String numero, LocalDate date, String medecin, List<LigneImprimable> lignes) {
        ordonnances.put(new Cle(centre, id), new OrdonnanceImprimable(patient, numero, date, medecin, lignes));
    }

    @Override
    public Optional<OrdonnanceImprimable> trouver(CentreId centre, UUID ordonnance) {
        return Optional.ofNullable(ordonnances.get(new Cle(centre, ordonnance)));
    }
}
