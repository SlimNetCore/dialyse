package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SocietesActives;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SocietesSuivies;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;

import java.util.List;
import java.util.UUID;

/**
 * ACL vers le langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : les sociétés suivies et leurs centres. Remplace
 * {@code SocietesEtCentresJdbc} (SQL direct sur {@code societes} et {@code centers}). Un centre sans état renseigné est actif.
 */
public final class SocietesEtCentresPublies implements SocietesSuivies, SocietesActives {

    private final AnnuaireDesSocietes societes;

    public SocietesEtCentresPublies(AnnuaireDesSocietes societes) {
        this.societes = societes;
    }

    @Override
    public List<UUID> identifiants() {
        return societes.societesActives();
    }

    @Override
    public SocieteSuivie societe(UUID societeId) {
        var societe = societes.societe(societeId).orElseThrow(() -> new SocieteInconnue(societeId));
        List<CentreDeLaSociete> centres = societe.centres().stream()
                .map(c -> new CentreDeLaSociete(c.id(), c.nom(), c.actif())).toList();
        return new SocieteSuivie(societeId, societe.raisonSociale(), centres);
    }
}
