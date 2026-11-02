package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Le calendrier du centre lu par le module lui-même (P9, ADR 0069) : un jour est fermé s'il n'est pas un jour d'ouverture
 * hebdomadaire ou s'il porte une fermeture (férié, fermeture exceptionnelle). Aucun jour d'ouverture renseigné : tous. Lecture seule.
 */
public class CalendrierDuCentreDuModule implements CalendrierDuCentre {

    private final LectureDesRessources ressources;

    public CalendrierDuCentreDuModule(LectureDesRessources ressources) {
        this.ressources = ressources;
    }

    @Override
    public boolean ouvert(UUID centre, LocalDate jour) {
        RessourcesDuCentre r = ressources.duCentre(new CentreId(centre), jour);
        boolean ouvertHebdomadaire = r.joursOuverts().contains(JourDeLaSemaine.de(jour.getDayOfWeek()));
        boolean ferme = r.fermetures().stream().anyMatch(f -> f.date().equals(jour));
        return ouvertHebdomadaire && !ferme;
    }
}
