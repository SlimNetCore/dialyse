package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant : le carnet des transporteurs d'un centre. Toute opération est bornée au centre indiqué. */
public interface CarnetDeTransporteurs {

    Optional<Transporteur> trouver(CentreId centre, UUID id);

    List<Transporteur> duCentre(CentreId centre);

    void enregistrer(Transporteur transporteur);

    /** Remplace le nom et le téléphone ; sans effet si le transporteur n'est pas du centre indiqué. */
    void remplacer(Transporteur transporteur);

    void supprimer(CentreId centre, UUID id);

    /**
     * Patients DU CENTRE du transporteur qui l'utilisent à l'aller ou au retour : l'aller et le retour comptent chacun (KB-RF1-03
     * corrigé, ADR 0123 : les patients des autres centres ne comptent plus).
     */
    long nombreDeReferences(CentreId centre, UUID id);
}
