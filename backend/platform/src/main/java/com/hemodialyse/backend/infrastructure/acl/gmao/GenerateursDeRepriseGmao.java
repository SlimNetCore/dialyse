package com.hemodialyse.backend.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.domain.migration.port.GenerateursDeReprise;

import java.util.List;
import java.util.UUID;

/** Traduit le langage publié {@link GenerateursDuCentre} du contexte GMAO vers le port de la reprise de données. */
public class GenerateursDeRepriseGmao implements GenerateursDeReprise {

    private final GenerateursDuCentre generateurs;

    public GenerateursDeRepriseGmao(GenerateursDuCentre generateurs) {
        this.generateurs = generateurs;
    }

    @Override
    public List<GenerateurRepris> duCentre(UUID centreId) {
        return generateurs.duCentre(centreId).stream()
                .map(g -> new GenerateurRepris(g.id(), g.code(), g.salleId()))
                .toList();
    }
}
