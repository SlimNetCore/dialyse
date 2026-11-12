package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Tâche du 1er du mois : fige le mois écoulé de chaque société active (une société sans centre comprise). Rejouable : une société dont
 * l'instantané existe est ignorée. Un échec sur une société n'empêche pas les suivantes (il est journalisé).
 */
public final class CapturerLesInstantanesDuMoisEcoule {

    private static final Logger log = LoggerFactory.getLogger(CapturerLesInstantanesDuMoisEcoule.class);

    private final SocietesActives societes;
    private final InstantanesMensuels instantanes;
    private final ContenuDuMoisAFiger contenu;
    private final HorlogeDuPilotage horloge;

    public CapturerLesInstantanesDuMoisEcoule(SocietesActives societes, InstantanesMensuels instantanes, ContenuDuMoisAFiger contenu,
                                              HorlogeDuPilotage horloge) {
        this.societes = societes;
        this.instantanes = instantanes;
        this.contenu = contenu;
        this.horloge = horloge;
    }

    /** @return le nombre d'instantanés créés */
    public int execute() {
        YearMonth courant = YearMonth.from(horloge.aujourdhui());
        MoisDInstantane precedent = MoisDInstantane.precedent(courant);
        int crees = 0;
        for (UUID societeId : societes.identifiants()) {
            try {
                boolean existe = instantanes.lister(societeId).stream().anyMatch(i -> i.mois().equals(precedent.texte()));
                if (!existe && instantanes.enregistrer(InstantaneMensuel.figerLeMoisEcoule(societeId, courant,
                        contenu.constituer(societeId, precedent.debut(), precedent.fin())), horloge.maintenant())) {
                    crees++;
                }
            } catch (RuntimeException e) {
                log.error("Instantané {} de la société {} non généré", precedent.texte(), societeId, e);
            }
        }
        log.info("Instantanés mensuels {} : {} créé(s)", precedent.texte(), crees);
        return crees;
    }
}
