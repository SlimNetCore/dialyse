package com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Function;

/**
 * « Aujourd'hui » dans le fuseau d'un centre (SE7f, ADR 0100), fuseau lu dans le langage publié d'Organisation (O4 lot A, ADR 0107) ;
 * remplace {@code JourDuCentreJdbc}. Centre inconnu ou nul, fuseau absent, vide ou illisible : le fuseau par défaut de la plateforme
 * ({@code app.planification.fuseau-par-defaut}), choix de la Séance.
 */
public class JourDuCentrePublie implements Function<UUID, LocalDate> {

    private final AnnuaireDesCentres centres;
    private final Clock horloge;
    private final ZoneId fuseauParDefaut;

    public JourDuCentrePublie(AnnuaireDesCentres centres, Clock horloge, ZoneId fuseauParDefaut) {
        this.centres = centres;
        this.horloge = horloge;
        this.fuseauParDefaut = fuseauParDefaut;
    }

    @Override
    public LocalDate apply(UUID centre) {
        return LocalDate.now(horloge.withZone(fuseauDe(centre)));
    }

    ZoneId fuseauDe(UUID centre) {
        if (centre == null) return fuseauParDefaut;
        return centres.fuseauHoraireDe(centre).orElse(fuseauParDefaut);
    }
}
