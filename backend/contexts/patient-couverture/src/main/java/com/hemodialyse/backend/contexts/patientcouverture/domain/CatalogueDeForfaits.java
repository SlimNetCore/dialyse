package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant : le catalogue de forfaits d'un centre. Toute opération est bornée au centre indiqué. */
public interface CatalogueDeForfaits {

    Optional<Forfait> trouver(CentreId centre, UUID id);

    List<Forfait> duCentre(CentreId centre);

    void enregistrer(Forfait forfait);

    /** Remplace le code, le libellé et le prix ; sans effet si le forfait n'est pas du centre indiqué. */
    void remplacer(Forfait forfait);

    void supprimer(CentreId centre, UUID id);
}
