package com.hemodialyse.backend.contexts.comptabilite.application.payeur;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ComptesClientsDesPayeurs;

import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié : le compte client propre à un payeur, pour la génération des écritures (slices suivantes).
 */
public final class ConsulterLeCompteDuPayeur implements ComptesClientsDesPayeurs {

    private final LectureDesPayeurs payeurs;

    public ConsulterLeCompteDuPayeur(LectureDesPayeurs payeurs) {
        this.payeurs = payeurs;
    }

    @Override
    public Optional<String> compteDuPayeur(UUID centre, UUID payeur) {
        return payeurs.compteDuPayeur(new CentreId(centre), new PayeurId(payeur));
    }
}
