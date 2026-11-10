package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CaissesDesCentresPayeurs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACL : la caisse de chaque centre payeur lue dans l'annuaire des payeurs de {@code patientcouverture.v1} ({@link PayeursDuCentre}, RF3b,
 * ADR 0127). Remplace la jointure SQL sur {@code centre_payeur}, {@code agence} et {@code caisse_assurance}.
 */
public final class CaissesParLAnnuaireDesPayeurs implements CaissesDesCentresPayeurs {

    private final PayeursDuCentre annuaire;

    public CaissesParLAnnuaireDesPayeurs(PayeursDuCentre annuaire) {
        this.annuaire = annuaire;
    }

    @Override
    public Map<UUID, CaisseDuPayeur> parCentrePayeur(UUID centre) {
        Map<UUID, CaisseDuPayeur> caisses = new HashMap<>();
        for (PayeursDuCentre.ChaineDuPayeur chaine : annuaire.chaines(centre)) {
            if (chaine.caisse() != null) {
                caisses.put(chaine.centrePayeur().id(), new CaisseDuPayeur(chaine.caisse().code(), chaine.caisse().nom()));
            }
        }
        return caisses;
    }
}
