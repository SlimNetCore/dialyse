package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SurEffectifDuRoulement;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link SurEffectifDuRoulement} (ACL vers le calcul de présence du legacy,
 * slice P4) : les jours de la semaine de {@code aujourdhui} où la case dépasse l'effectif requis.
 */
@Component
public class SurEffectifDuRoulementLegacy implements SurEffectifDuRoulement {

    private final PresenceInfirmierQueryService presence;

    public SurEffectifDuRoulementLegacy(PresenceInfirmierQueryService presence) {
        this.presence = presence;
    }

    @Override
    public List<String> joursEnSureffectif(UUID centre, UUID salle, UUID creneau, Set<String> jours, LocalDate aujourdhui) {
        Set<JourSemaine> voulus = EnumSet.noneOf(JourSemaine.class);
        jours.forEach(j -> voulus.add(JourSemaine.valueOf(j)));
        return presence.joursEnSureffectif(centre, salle, creneau, voulus, aujourdhui).stream().map(Enum::name).toList();
    }
}
