package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.util.Map;
import java.util.UUID;

/**
 * Langage publié du mapping comptable (RG-CPT-006, 010, 011) : les comptes et le journal de chaque opération d'un centre, en
 * lecture, pour la génération des écritures qui n'est pas encore dans ce contexte (ventes, règlements, stock).
 */
public interface MappingComptableDuCentre {

    /**
     * Le mapping enregistré du centre ; le mapping par défaut tant qu'il n'a rien enregistré.
     */
    MappingPublie mapping(UUID centre);

    /**
     * @param compteTvaCollectee compte de TVA collectée ; nul si le centre n'en a pas (jamais le cas d'un mapping relu : une
     *                           colonne vide y vaut 44571, KB-CPT-14)
     * @param journaux           code du journal de chaque opération comptable ({@code VENTE}, {@code REGLEMENT_BANQUE},
     *                           {@code REGLEMENT_CAISSE}, {@code STOCK_RECEPTION}, {@code STOCK_SORTIE},
     *                           {@code STOCK_INVENTAIRE}), toutes présentes
     */
    record MappingPublie(UUID centre, String compteVentes, String compteClientPatient, String compteClientDefaut,
                         String compteBanque, String compteCaisse, String compteTvaCollectee, String compteStock,
                         String compteConsommation, String compteFacturesNonParvenues, String compteBoniInventaire,
                         String compteMaliInventaire, Map<String, String> journaux) {
    }
}
