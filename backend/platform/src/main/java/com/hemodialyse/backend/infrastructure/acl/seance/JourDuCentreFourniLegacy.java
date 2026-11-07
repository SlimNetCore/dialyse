package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.application.centre.JourDuCentre;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.JourDuCentreFourni;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/** Fournit à la Séance le langage publié {@link JourDuCentreFourni} : le jour du fuseau du centre, calculé par {@link JourDuCentre} (PC4). */
@Component
public class JourDuCentreFourniLegacy implements JourDuCentreFourni {

    private final JourDuCentre jourDuCentre;

    public JourDuCentreFourniLegacy(JourDuCentre jourDuCentre) {
        this.jourDuCentre = jourDuCentre;
    }

    @Override
    public LocalDate aujourdhui(UUID centre) {
        return jourDuCentre.aujourdhui(centre);
    }
}
