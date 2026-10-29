package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.CalendrierDuCentre;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link CalendrierDuCentre} (ACL vers les données de planning du legacy, retirée
 * avec la slice P4) : un jour est fermé s'il n'est pas un jour d'ouverture hebdomadaire ou s'il porte une fermeture (férié, fermeture
 * exceptionnelle). Même règle que le calcul de présence ({@code CalendrierCentre}).
 */
@Component
public class CalendrierDuCentreLegacy implements CalendrierDuCentre {

    private final PlanningDonneesPort planning;

    public CalendrierDuCentreLegacy(PlanningDonneesPort planning) {
        this.planning = planning;
    }

    @Override
    public boolean ouvert(UUID centre, LocalDate jour) {
        DonneesPlanning donnees = planning.chargerPeriode(centre, jour, jour);
        boolean ouvertHebdomadaire = donnees.joursOuverts() == null || donnees.joursOuverts().isEmpty()
                || donnees.joursOuverts().contains(JourSemaine.de(jour.getDayOfWeek()));
        boolean ferme = donnees.fermetures() != null && donnees.fermetures().stream().anyMatch(f -> f.date().equals(jour));
        return ouvertHebdomadaire && !ferme;
    }
}
