package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.ParametrageDeLaPlanification;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.ProfilInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.port.ProfilInfirmierPort;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ACL du legacy vers les profils des infirmiers de la Planification (slice P1, ADR 0053) : lecture de l'optimisation par le
 * langage publié du contexte ; compétences traduites par leur nom.
 */
@Component
public class ProfilsInfirmiersDuContexte implements ProfilInfirmierPort {

    private final ParametrageDeLaPlanification parametrage;

    public ProfilsInfirmiersDuContexte(ParametrageDeLaPlanification parametrage) {
        this.parametrage = parametrage;
    }

    @Override
    public Map<UUID, ProfilInfirmier> profils(UUID centerId) {
        Map<UUID, ProfilInfirmier> profils = new HashMap<>();
        parametrage.profilsDesInfirmiers(centerId).forEach((infirmier, p) -> {
            Set<CompetenceInfirmier> competences = EnumSet.noneOf(CompetenceInfirmier.class);
            p.competences().forEach(nom -> competences.add(CompetenceInfirmier.valueOf(nom)));
            profils.put(infirmier, new ProfilInfirmier(infirmier, p.tauxActivite(), competences));
        });
        return profils;
    }
}
