package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PoidsSecsPrescritsPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PoidsSecDesPrescriptions;

import java.util.List;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link PoidsSecDesPrescriptions} (LEC-PLN-GMAO c, ADR 0180) à partir des poids secs prescrits du
 * Dossier médical ({@link PoidsSecsPrescritsPublies}) : traduction terme à terme, l'ordre est conservé.
 */
public class PoidsSecsPourLaSeance implements PoidsSecDesPrescriptions {

    private final PoidsSecsPrescritsPublies prescrits;

    public PoidsSecsPourLaSeance(PoidsSecsPrescritsPublies prescrits) {
        this.prescrits = prescrits;
    }

    @Override
    public List<PoidsSecPrescrit> poidsSecsCiblesPrescrits(UUID centre, UUID patient) {
        return prescrits.poidsSecsCiblesPrescrits(centre, patient).stream()
                .map(p -> new PoidsSecPrescrit(p.datePrescription(), p.poidsSecCibleKg())).toList();
    }
}
