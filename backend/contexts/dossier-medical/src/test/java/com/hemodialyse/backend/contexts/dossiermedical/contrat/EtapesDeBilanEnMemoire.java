package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Double en mémoire du port {@link EtapesDeBilan}. */
public class EtapesDeBilanEnMemoire implements EtapesDeBilan {
    private final List<EtapeDeBilan> lignes = new ArrayList<>();

    private static EtapeDeBilan copie(EtapeDeBilan e) {
        return EtapeDeBilan.reconstituer(e.id(), e.patient(), e.centre(), e.categorie(), e.libelle(), e.statut(), e.dateRealisation(),
                e.resultat(), e.dateExpiration(), e.demandeExamenId(), e.serologieId(), e.creeLe(), e.modifieLe());
    }

    @Override
    public List<EtapeDeBilan> duPatient(CentreId centre, PatientId patient) {
        return lignes.stream().filter(e -> e.centre().equals(centre) && e.patient().equals(patient))
                .sorted(Comparator.comparing((EtapeDeBilan e) -> e.categorie().name()).thenComparing(EtapeDeBilan::libelle).thenComparing(EtapeDeBilan::creeLe)
                        .thenComparing(EtapeDeBilan::id))
                .map(EtapesDeBilanEnMemoire::copie).toList();
    }

    @Override
    public Set<String> clesDuPatient(CentreId centre, PatientId patient) {
        return lignes.stream().filter(e -> e.centre().equals(centre) && e.patient().equals(patient))
                .map(e -> e.categorie().name() + "|" + e.libelle()).collect(Collectors.toSet());
    }

    @Override
    public void verrouillerLaGeneration(CentreId centre, PatientId patient) {
        // un seul fil dans le double
    }

    @Override
    public void ajouter(EtapeDeBilan etape) {
        lignes.add(copie(etape));
    }

    @Override
    public void enregistrer(EtapeDeBilan etape) {
        lignes.removeIf(e -> e.id().equals(etape.id()));
        lignes.add(copie(etape));
    }

    @Override
    public Optional<EtapeDeBilan> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return lignes.stream().filter(e -> e.id().equals(id) && e.centre().equals(centre) && e.patient().equals(patient)).findFirst()
                .map(EtapesDeBilanEnMemoire::copie);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return lignes.removeIf(e -> e.id().equals(id) && e.centre().equals(centre) && e.patient().equals(patient));
    }
}
