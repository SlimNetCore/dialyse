package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ValidationFournie;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link ValidationFournie} : la validation legacy (SE2 : statut, place mémorisée, signature de
 * l'infirmier, sans consommation). Participe à la transaction de l'appelant. Retirée quand SE2 sera reprise.
 */
@Component
public class ValidationFournieLegacy implements ValidationFournie {

    private final SeanceUseCase seances;
    private final EntityManager entites;

    public ValidationFournieLegacy(SeanceUseCase seances, EntityManager entites) {
        this.seances = seances;
        this.entites = entites;
    }

    /**
     * La validation legacy écrit par JPA (écriture différée jusqu'à la fin de la transaction) : le flush rend le nouveau statut visible
     * à la relecture JDBC de la Séance dans la même unité de travail.
     */
    @Override
    public void validerParLInfirmier(UUID centre, UUID seance, String auteur) {
        seances.validate(CenterId.of(centre), seance, auteur, List.of());
        entites.flush();
    }
}
