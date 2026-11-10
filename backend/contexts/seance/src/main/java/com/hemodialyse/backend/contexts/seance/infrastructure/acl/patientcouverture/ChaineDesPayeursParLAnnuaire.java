package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ChaineDesPayeursDuCentre;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL vers l'annuaire des payeurs de Patient et couverture (RF3b, ADR 0127) : remplace les jointures SQL sur {@code centre_payeur},
 * {@code agence} et {@code caisse_assurance} des lectures publiées de la Séance. Code et nom de la caisse tels quels.
 */
public class ChaineDesPayeursParLAnnuaire implements ChaineDesPayeursDuCentre {

    private final PayeursDuCentre annuaire;

    public ChaineDesPayeursParLAnnuaire(PayeursDuCentre annuaire) {
        this.annuaire = annuaire;
    }

    @Override
    public Map<UUID, CaisseDuPayeur> caissesDesCentresPayeurs(UUID centre) {
        Map<UUID, CaisseDuPayeur> caisses = new HashMap<>();
        for (PayeursDuCentre.ChaineDuPayeur chaine : annuaire.chaines(centre)) {
            if (chaine.caisse() != null) {
                caisses.put(chaine.centrePayeur().id(), new CaisseDuPayeur(chaine.caisse().code(), chaine.caisse().nom()));
            }
        }
        return caisses;
    }

    @Override
    public Optional<UUID> agenceDuCentrePayeur(UUID centre, UUID centrePayeur) {
        return annuaire.detail(centre, centrePayeur).map(chaine -> chaine.centrePayeur().agence());
    }
}
