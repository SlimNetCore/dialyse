package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.LiberationDesPlaces;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * ACL de la fiche du patient (legacy) vers le port publié {@link LiberationDesPlaces} de la Planification (P7, seam fiche) : la
 * fiche enregistrée est rendue visible en SQL (vidage du contexte de persistance) avant l'appel ; si la place est libérée, la fiche
 * gérée par JPA est relue (elle ne réécrira pas l'ancienne place) et la fiche renvoyée est alignée. Dans la transaction de la fiche.
 */
@Component
public class LiberationALEnregistrementDeLaFiche {

    private final LiberationDesPlaces liberation;
    private final EntityManager entites;

    public LiberationALEnregistrementDeLaFiche(LiberationDesPlaces liberation, EntityManager entites) {
        this.liberation = liberation;
        this.entites = entites;
    }

    /**
     * Libère la place de la fiche si son échéance est dépassée de plus d'un jour ; aligne la fiche renvoyée.
     */
    public boolean libererSiEchue(Patient fiche) {
        entites.flush();
        boolean liberee = liberation.siEchue(fiche.getCenterId().value(), fiche.getId().value());
        if (liberee) {
            PatientJpaEntity geree = entites.find(PatientJpaEntity.class, fiche.getId().value());
            if (geree != null) entites.refresh(geree);
            fiche.libererPlacement();
        }
        return liberee;
    }
}
