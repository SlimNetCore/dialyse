package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ValidationFournie;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
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

    public ValidationFournieLegacy(SeanceUseCase seances) {
        this.seances = seances;
    }

    @Override
    public void validerParLInfirmier(UUID centre, UUID seance, String auteur) {
        seances.validate(CenterId.of(centre), seance, auteur, List.of());
    }
}
