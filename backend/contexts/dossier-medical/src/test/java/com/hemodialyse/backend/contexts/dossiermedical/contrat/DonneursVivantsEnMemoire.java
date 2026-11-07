package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire du port {@link DonneursVivants}. */
public class DonneursVivantsEnMemoire implements DonneursVivants {
    private final List<DonneurVivant> lignes = new ArrayList<>();

    private static DonneurVivant copie(DonneurVivant d) {
        return DonneurVivant.reconstituer(d.id(), d.patient(), d.centre(), d.nom(), d.prenom(), d.dateNaissance(), d.lienDeParente(),
                d.telephone(), d.groupeSanguin(), d.typageHla(), d.statutBilan(), d.crossmatch(), d.dateCrossmatch(), d.bilanRealise(),
                d.contreIndications(), d.decisionFinale(), d.dateDecision(), d.creeLe(), d.modifieLe());
    }

    @Override
    public List<DonneurVivant> duPatient(CentreId centre, PatientId patient) {
        return lignes.stream().filter(d -> d.centre().equals(centre) && d.patient().equals(patient))
                .sorted(Comparator.comparing(DonneurVivant::creeLe).reversed().thenComparing(DonneurVivant::id))
                .map(DonneursVivantsEnMemoire::copie).toList();
    }

    @Override
    public void ajouter(DonneurVivant donneur) {
        lignes.add(copie(donneur));
    }

    @Override
    public void enregistrer(DonneurVivant donneur) {
        lignes.removeIf(d -> d.id().equals(donneur.id()));
        lignes.add(copie(donneur));
    }

    @Override
    public Optional<DonneurVivant> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return lignes.stream().filter(d -> d.id().equals(id) && d.centre().equals(centre) && d.patient().equals(patient)).findFirst()
                .map(DonneursVivantsEnMemoire::copie);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return lignes.removeIf(d -> d.id().equals(id) && d.centre().equals(centre) && d.patient().equals(patient));
    }
}
