package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.time.Instant;

/** Événement de domaine d'un équipement (nom au passé, immuable). */
public interface EvenementDEquipement {

    EquipementId equipementId();

    CentreId centreId();

    Instant survenuLe();
}
