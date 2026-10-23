package com.hemodialyse.backend.contexts.facturation.infrastructure.horloge;

import com.hemodialyse.backend.contexts.facturation.application.cloture.FuseauxDesCentres;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.ZoneId;

/**
 * Adaptateur de {@link FuseauxDesCentres} : la table des centres n'a pas de fuseau, tous les centres suivent le fuseau par défaut
 * ({@code app.facturation.fuseau-par-defaut}, par défaut celui des Absences, Africa/Algiers). Remplacé par la lecture du fuseau du
 * centre quand il existera.
 */
public class FuseauParDefaut implements FuseauxDesCentres {

    private final ZoneId fuseau;

    public FuseauParDefaut(ZoneId fuseau) {
        this.fuseau = fuseau;
    }

    @Override
    public ZoneId de(CentreId centre) {
        return fuseau;
    }
}

