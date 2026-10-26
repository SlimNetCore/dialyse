package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.infirmier.port.AffectationInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort.CompteRef;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lecture d'une fiche d'infirmier avec son roulement et son compte, pour « Mon planning » (slice P3). Le référentiel des
 * infirmiers (création, modification, activation, comptes) est servi par le contexte Planification (slice P2,
 * {@code contexts/planification}) ; ce service disparaît avec P3.
 */
@Service
public class InfirmierService {

    private final AffectationInfirmierRepositoryPort affectations;
    private final ComptesInfirmierPort comptes;

    public InfirmierService(AffectationInfirmierRepositoryPort affectations, ComptesInfirmierPort comptes) {
        this.affectations = affectations;
        this.comptes = comptes;
    }

    /**
     * Fiche avec son roulement et son compte.
     */
    InfirmierDetail detail(java.util.UUID centerId, Infirmier infirmier) {
        CompteRef compte = infirmier.userId() == null ? null
                : comptes.trouverParIds(List.of(infirmier.userId())).stream().findFirst().orElse(null);
        return new InfirmierDetail(infirmier, affectations.findByInfirmierIds(centerId, List.of(infirmier.id())), compte);
    }

    /**
     * Infirmier avec son roulement et son compte utilisateur éventuel.
     */
    public record InfirmierDetail(Infirmier infirmier, List<AffectationInfirmier> affectations, CompteRef compte) {
        public InfirmierDetail(Infirmier infirmier, List<AffectationInfirmier> affectations) {
            this(infirmier, affectations, null);
        }
    }
}
