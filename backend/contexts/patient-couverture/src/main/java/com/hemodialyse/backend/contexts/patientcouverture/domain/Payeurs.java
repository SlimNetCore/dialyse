package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant d'un niveau de la chaîne des payeurs d'un centre. Toute opération est bornée au centre indiqué. */
public interface Payeurs<P extends Payeur> {

    Optional<P> trouver(CentreId centre, UUID id);

    List<P> duCentre(CentreId centre);

    void enregistrer(P payeur);

    /** Remplace tous les champs ; sans effet si le payeur n'est pas du centre indiqué. */
    void remplacer(P payeur);

    void supprimer(CentreId centre, UUID id);
}
