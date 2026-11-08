package com.hemodialyse.backend.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.LiaisonDesSeancesFournie;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiaisonDesSeancesAUneFacturePubliee;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Fournisseur de la Facturation (ADR 0097, SE7c2, ARGENT) : la liaison séance -> facture passe par {@link LiaisonDesSeancesAUneFacturePubliee}
 * (seance.v1). Logé dans la plateforme (cycle Maven). Délégation pure : l'échec d'une séance (422) vient du port de la Séance et annule la
 * transaction d'émission ENTIÈRE, qui n'est pas scindée ; aucune transaction n'est ouverte ici.
 */
@Component
public class LiaisonDesSeancesParLaSeance implements LiaisonDesSeancesFournie {

    private final LiaisonDesSeancesAUneFacturePubliee liaison;

    public LiaisonDesSeancesParLaSeance(LiaisonDesSeancesAUneFacturePubliee liaison) {
        this.liaison = liaison;
    }

    @Override
    public void lier(UUID centre, UUID facture, List<UUID> seances) {
        liaison.lier(centre, facture, seances);
    }
}
