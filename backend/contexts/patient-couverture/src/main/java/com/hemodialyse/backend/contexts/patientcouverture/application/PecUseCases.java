package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Pec;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PecUseCases {
    PecId creer(CreerPecCommande commande, Instant maintenant);

    void valider(ValiderPecCommande commande);

    void cloturer(CloturerPecCommande commande);

    Pec consulter(PecId pecId, CentreId centreId);

    PagedResult<Pec> lister(CentreId centreId, int page, int size);

    List<Pec> listerParPatient(PatientId patientId, CentreId centreId);

    boolean autoriseSeance(PecId pecId, CentreId centreId);

    void supprimer(PecId pecId, CentreId centreId);

    record CreerPecCommande(PatientId patientId, CentreId centreId, LocalDate dateDebutDemande,
                            LocalDate dateFinDemande, UUID forfaitDemandeId, UUID auteurId) {
    }

    record ValiderPecCommande(PecId pecId, CentreId centreId, LocalDate dateDebutEffectif,
                              LocalDate dateFinEffectif, UUID forfaitEffectifId) {
    }

    record CloturerPecCommande(PecId pecId, CentreId centreId) {
    }
}