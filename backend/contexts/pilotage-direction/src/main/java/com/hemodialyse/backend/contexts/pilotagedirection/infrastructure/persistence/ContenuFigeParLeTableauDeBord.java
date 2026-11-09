package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ContenuDuMoisAFiger;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ContenuFige;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursAFigerFournis;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Constitue le contenu d'un instantané : {@code {overview, indicators, breakdown}} sérialisé par le MÊME {@code ObjectMapper} que le reste
 * de l'API, dans le même ordre de clés qu'avant (RG-DIR-090). La vue d'ensemble et les répartitions viennent du langage publié du lot A
 * (mêmes cas d'usage, mêmes champs) ; les indicateurs sont fournis par la plateforme et gravés tels quels.
 */
public final class ContenuFigeParLeTableauDeBord implements ContenuDuMoisAFiger {

    private final TableauDeBordDeLaDirection tableau;
    private final IndicateursAFigerFournis indicateurs;
    private final ObjectMapper mapper;

    public ContenuFigeParLeTableauDeBord(TableauDeBordDeLaDirection tableau, IndicateursAFigerFournis indicateurs, ObjectMapper mapper) {
        this.tableau = tableau;
        this.indicateurs = indicateurs;
        this.mapper = mapper;
    }

    @Override
    public ContenuFige constituer(UUID societeId, LocalDate debut, LocalDate fin) {
        var vue = tableau.vueDEnsemble(societeId, debut, fin);
        var ind = indicateurs.pour(societeId, debut, fin);
        var racine = mapper.createObjectNode();
        racine.set("overview", mapper.valueToTree(vue));
        racine.set("indicators", mapper.valueToTree(ind));
        racine.set("breakdown", mapper.valueToTree(tableau.repartition(societeId, debut, fin)));
        return new ContenuFige(mapper.writeValueAsString(racine));
    }
}
