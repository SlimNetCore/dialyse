package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.commandes.GestionDesBonsDeCommande.LigneSaisie;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.LigneDeBon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JSON des bons de commande, identique au legacy ({@code BonCommande}, {@code LigneBonCommande}) : mêmes champs en
 * lecture ; en écriture, le centre et l'auteur du corps sont facultatifs et l'auteur est ignoré (session).
 */
final class BonsDeCommandeJson {

    private BonsDeCommandeJson() {
    }

    private static List<LigneSaisie> versSaisies(List<LigneRequete> lignes) {
        return lignes == null ? List.of() : lignes.stream().map(LigneRequete::saisie).toList();
    }

    record LigneRequete(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire) {
        LigneSaisie saisie() {
            return new LigneSaisie(id, articleId, quantite, prixUnitaire);
        }
    }

    record CreationRequete(UUID centerId, UUID fournisseurId, String userId, List<LigneRequete> lignes) {
        List<LigneSaisie> saisies() {
            return versSaisies(lignes);
        }
    }

    record RemplacementRequete(UUID centerId, List<LigneRequete> lignes) {
        List<LigneSaisie> saisies() {
            return versSaisies(lignes);
        }
    }

    record CentreRequete(UUID centerId) {
    }

    record LigneReponse(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire) {
        static LigneReponse depuis(LigneDeBon l) {
            return new LigneReponse(l.id(), l.articleId(), l.quantite(), l.prixUnitaire());
        }
    }

    record BonReponse(UUID id, UUID centerId, String reference, UUID fournisseurId, String statut, String createdBy,
                      OffsetDateTime createdAt, List<LigneReponse> lignes) {
        static BonReponse depuis(BonDeCommande b) {
            return new BonReponse(b.id(), b.centre().valeur(), b.reference(), b.fournisseurId(), b.statut().name(), b.creePar(),
                    b.creeLe(), b.lignes().stream().map(LigneReponse::depuis).toList());
        }
    }
}

