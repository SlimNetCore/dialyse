package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant : les prises en charge (table {@code prise_en_charge}). Une lecture ne rend que celles du centre demandé. */
public interface PrisesEnCharge {

    Optional<PriseEnCharge> trouver(CentreId centre, UUID id);

    /** Une PEC porte-t-elle déjà cet identifiant, dans un centre quelconque ? (identifiant imposé par la fiche) */
    boolean identifiantPris(UUID id);

    /** Crée ou met à jour la PEC. */
    void enregistrer(PriseEnCharge pec);

    void supprimer(UUID id);

    List<PriseEnCharge> duPatient(CentreId centre, PatientId patient);

    List<PriseEnCharge> duCentre(CentreId centre);
}
