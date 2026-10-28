package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.domain.planning.model.SalleVue;
import com.hemodialyse.backend.domain.planning.port.SalleVuePort;
import com.hemodialyse.backend.domain.planning.service.CapaciteSalleRegle;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Vue d'ensemble des salles du centre (générateurs affectés, capacité, places restantes) et contrôle de capacité à
 * l'affectation d'un générateur. Lecture bornée au centre, non mise en cache (l'affectation change à chaque
 * équipement enregistré).
 */
@Service
public class SalleGenerateursService {

    private final SalleVuePort salles;

    public SalleGenerateursService(SalleVuePort salles) {
        this.salles = salles;
    }

    public PagedResult<SalleVue> lister(UUID centerId, int page, int size) {
        return salles.findPaged(centerId, page, size);
    }

    /**
     * Refuse l'affectation d'un générateur à une salle qui n'a plus de place. Sans salle, rien à contrôler ; une salle
     * d'un autre centre est introuvable (aucune limite à appliquer).
     *
     * @param equipementId générateur affecté (ignoré dans le décompte s'il est déjà dans la salle), {@code null} à la
     *                     création
     */
    public void verifierAffectation(UUID centerId, UUID salleId, UUID equipementId) {
        if (salleId == null) return;
        salles.findById(centerId, salleId).ifPresent(salle -> {
            int deja = (int) salle.generateurs().stream().filter(g -> !g.id().equals(equipementId)).count();
            CapaciteSalleRegle.verifierAffectation(salle.capacite(), deja, salle.nom());
        });
    }
}
