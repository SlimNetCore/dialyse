package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels.InstantaneFige;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Renvoie l'instantané d'un mois écoulé, en le figeant maintenant s'il n'existe pas encore. Idempotent : un instantané existant n'est jamais
 * recalculé ni remplacé (le contenu n'est même pas constitué). Le mois est contrôlé AVANT toute lecture.
 */
public final class CapturerUnInstantaneMensuel {

    private final InstantanesMensuels instantanes;
    private final ContenuDuMoisAFiger contenu;
    private final HorlogeDuPilotage horloge;
    private final ConsulterUnInstantane consulter;

    public CapturerUnInstantaneMensuel(InstantanesMensuels instantanes, ContenuDuMoisAFiger contenu, HorlogeDuPilotage horloge) {
        this.instantanes = instantanes;
        this.contenu = contenu;
        this.horloge = horloge;
        this.consulter = new ConsulterUnInstantane(instantanes);
    }

    public InstantaneFige execute(UUID societeId, String texteDuMois) {
        MoisDInstantane mois = MoisDInstantane.lire(texteDuMois);
        YearMonth courant = YearMonth.from(horloge.aujourdhui());
        mois.exigerCapturable(courant);
        boolean dejaFige = instantanes.lister(societeId).stream().anyMatch(i -> i.mois().equals(mois.texte()));
        if (!dejaFige) {
            instantanes.enregistrer(InstantaneMensuel.figer(societeId, mois, courant,
                    contenu.constituer(societeId, mois.debut(), mois.fin())));
        }
        return consulter.execute(societeId, mois.texte());
    }
}
