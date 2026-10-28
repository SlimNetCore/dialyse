package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.LiberationDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.VerrouDesPlacesDuCentre;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.service.FinOccupation;
import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * ACL de la fiche du patient (legacy) vers le port publié {@link LiberationDesPlaces} de la Planification (P7, seam fiche).
 * <p>
 * <b>Ordre global des verrous (ADR 0056, 0061)</b> : quand la fiche enregistrée tient une place dont l'échéance est dépassée, le
 * verrou du centre est pris AVANT le vidage du contexte de persistance (qui verrouille la ligne du patient) ; le job de nuit prend le
 * même ordre (centre, puis ligne de la fiche) : plus de cycle. Le jugement avant le vidage est fait sur la fiche en mémoire, au jour UTC,
 * qui est toujours au moins égal à la veille du centre : il prend le verrou dans tous les cas où la libération aura lieu (et parfois un
 * verrou de trop, sans conséquence). Le verrou est déjà tenu quand le contrôle de placement de la fiche l'a pris plus tôt.
 * <p>
 * Ensuite la fiche enregistrée est rendue visible en SQL (vidage du contexte de persistance) ; si la place est libérée, la fiche gérée
 * par JPA est relue (elle ne réécrira pas l'ancienne place) et la fiche renvoyée est alignée. Dans la transaction de la fiche.
 */
@Component
public class LiberationALEnregistrementDeLaFiche {

    private final LiberationDesPlaces liberation;
    private final VerrouDesPlacesDuCentre verrou;
    private final EntityManager entites;

    public LiberationALEnregistrementDeLaFiche(LiberationDesPlaces liberation, VerrouDesPlacesDuCentre verrou, EntityManager entites) {
        this.liberation = liberation;
        this.verrou = verrou;
        this.entites = entites;
    }

    /** Le jour du centre (fuseau du centre) : date des mouvements de la fiche, comme {@code PLACE_LIBEREE}. */
    public LocalDate aujourdhuiDuCentre(Patient fiche) {
        return liberation.aujourdhui(fiche.getCenterId().value());
    }

    /** Libère la place de la fiche si son échéance est dépassée de plus d'un jour ; aligne la fiche renvoyée. */
    public boolean libererSiEchue(Patient fiche) {
        if (tientUnePlaceEchue(fiche)) {
            verrou.verrouiller(fiche.getCenterId().value());
        }
        entites.flush();
        boolean liberee = liberation.siEchue(fiche.getCenterId().value(), fiche.getId().value());
        if (liberee) {
            PatientJpaEntity geree = entites.find(PatientJpaEntity.class, fiche.getId().value());
            if (geree != null) entites.refresh(geree);
            fiche.libererPlacement();
        }
        return liberee;
    }

    /** Jugement prudent (jour UTC, jamais en retard sur la veille du centre) sur la fiche en mémoire. */
    static boolean tientUnePlaceEchue(Patient fiche) {
        boolean place = fiche.getSalleId() != null || fiche.getPositionId() != null || fiche.getGenerateurId() != null;
        return place && FinOccupation.placeLiberee(fiche.getEtatPatient(), fiche.getDateEvenementEtat(), LocalDate.now(ZoneOffset.UTC));
    }
}
