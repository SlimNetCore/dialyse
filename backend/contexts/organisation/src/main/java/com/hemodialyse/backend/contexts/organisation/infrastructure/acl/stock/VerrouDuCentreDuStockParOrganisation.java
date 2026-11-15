package com.hemodialyse.backend.contexts.organisation.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.VerrouDuCentre;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.VerrouDuCentreFourni;

import java.util.UUID;

/**
 * ACL du Stock vers le langage publié d'Organisation (O4 lot B, ADR 0108) : le verrou du centre d'un inventaire est celui qu'Organisation
 * publie. Traducteur mince, aucun SQL ; le verrou est pris dans la transaction en cours de l'appelant (même source de données). Remplace
 * le verrou de la ligne du centre d'{@code InventairesJdbc}.
 */
public class VerrouDuCentreDuStockParOrganisation implements VerrouDuCentreFourni {

    private final VerrouDuCentre verrou;

    public VerrouDuCentreDuStockParOrganisation(VerrouDuCentre verrou) {
        this.verrou = verrou;
    }

    @Override
    public void verrouiller(UUID centre) {
        verrou.verrouiller(centre);
    }
}
