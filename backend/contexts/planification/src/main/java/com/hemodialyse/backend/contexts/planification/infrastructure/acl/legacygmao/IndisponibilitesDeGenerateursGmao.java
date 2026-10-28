package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacygmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.IndisponibilitesDesGenerateurs;
import com.hemodialyse.backend.contexts.planification.application.IndisponibilitesDeGenerateurs;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.IndisponibiliteDatee;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;

import java.time.LocalDate;
import java.util.List;

/**
 * ACL vers GMAO (langage publié {@link IndisponibilitesDesGenerateurs}) : traduit les périodes d'immobilisation des générateurs dans
 * le langage du contexte. Le motif est le type d'intervention.
 */
public class IndisponibilitesDeGenerateursGmao implements IndisponibilitesDeGenerateurs {

    private final IndisponibilitesDesGenerateurs gmao;

    public IndisponibilitesDeGenerateursGmao(IndisponibilitesDesGenerateurs gmao) {
        this.gmao = gmao;
    }

    @Override
    public List<IndisponibiliteDatee> entre(CentreId centre, LocalDate du, LocalDate au) {
        return gmao.entre(centre.valeur(), du, au).stream()
                .map(i -> new IndisponibiliteDatee(new GenerateurId(i.generateurId()), i.debut(), i.fin(), i.type())).toList();
    }
}
