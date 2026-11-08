package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.LiaisonDesSeancesFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.UUID;

/**
 * ACL de la Facturation vers la Séance (ADR 0097, SE7c2, ARGENT) : la liaison séance -> facture passe par {@link LiaisonDesSeancesFournie}.
 * Remplace {@code LiaisonDesSeancesJdbc}. Traduction pure (centre en identifiant) : l'échec d'une séance (422) vient du port fourni et annule
 * la transaction d'émission ENTIÈRE, qui n'est pas scindée.
 */
public class LiaisonDesSeancesPubliee implements LiaisonDesSeances {

    private final LiaisonDesSeancesFournie liaison;

    public LiaisonDesSeancesPubliee(LiaisonDesSeancesFournie liaison) {
        this.liaison = liaison;
    }

    @Override
    public void lier(CentreId centre, UUID facture, List<UUID> seances) {
        liaison.lier(centre.valeur(), facture, seances);
    }
}
