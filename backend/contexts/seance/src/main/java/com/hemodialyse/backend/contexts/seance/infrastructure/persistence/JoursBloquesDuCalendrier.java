package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.JoursBloques;
import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Les jours non ouvrés d'un mois lus dans le calendrier du centre (jours fériés puis fermetures exceptionnelles), dédoublonnés.
 * <p>
 * Tolérance reproduite du legacy (« environnements où les tables calendrier ne sont pas encore créées ») : une lecture qui échoue, quelle
 * que soit l'exception, ne bloque pas le tableau de bord et ne compte aucun jour. Elle n'est plus silencieuse : un WARN porte
 * l'identifiant du centre et la nature de jour, jamais le message de l'exception (il peut citer du SQL ou des valeurs).
 */
public class JoursBloquesDuCalendrier implements JoursBloques {

    private static final Logger JOURNAL = LoggerFactory.getLogger(JoursBloquesDuCalendrier.class);

    private final CalendrierDuCentre calendrier;

    public JoursBloquesDuCalendrier(CalendrierDuCentre calendrier) {
        this.calendrier = calendrier;
    }

    @Override
    public Set<LocalDate> du(CentreId centre, MoisDuTableauDeBord mois) {
        MoisDuCalendrier moisDuCalendrier = new MoisDuCalendrier(mois.annee(), mois.mois());
        Set<LocalDate> jours = new HashSet<>();
        for (NatureDeJour nature : NatureDeJour.values()) {
            try {
                calendrier.duMois(centre, nature, moisDuCalendrier).stream().map(JourDuCalendrier::jour).forEach(jours::add);
            } catch (RuntimeException e) {
                JOURNAL.warn("Calendrier du centre {} illisible ({}) : aucun jour non ouvré compté pour le tableau de bord", centre.valeur(), nature);
            }
        }
        return jours;
    }
}
