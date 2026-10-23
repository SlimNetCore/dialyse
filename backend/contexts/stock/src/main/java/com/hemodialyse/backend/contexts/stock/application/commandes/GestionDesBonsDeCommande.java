package com.hemodialyse.backend.contexts.stock.application.commandes;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonsDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.LigneDeBon;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Fournisseurs;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage des bons de commande d'un centre (RG-STK-005, 010 à 012) : créer, remplacer les lignes, valider, consulter,
 * lister. Ordre des contrôles : lignes (400), articles et fournisseur du centre (422), puis l'état du bon.
 */
public final class GestionDesBonsDeCommande {

    private static final String AUTEUR_PAR_DEFAUT = "system";

    private final BonsDeCommande bons;
    private final Articles articles;
    private final Fournisseurs fournisseurs;
    private final Supplier<UUID> identifiants;
    private final Clock horloge;

    public GestionDesBonsDeCommande(BonsDeCommande bons, Articles articles, Fournisseurs fournisseurs,
                                    Supplier<UUID> identifiants, Clock horloge) {
        this.bons = bons;
        this.articles = articles;
        this.fournisseurs = fournisseurs;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    private static RefusDuReferentiel introuvable() {
        return new RefusDuReferentiel("BON_COMMANDE_INTROUVABLE", "Bon de commande introuvable");
    }

    public BonDeCommande creer(CentreId centre, UUID fournisseurId, String auteur, List<LigneSaisie> saisies) {
        List<LigneDeBon> lignes = lignes(saisies);
        exigerLesArticlesDuCentre(centre, lignes);
        if (fournisseurId != null && !fournisseurs.existe(centre, fournisseurId)) {
            throw new RefusDuReferentiel("BON_COMMANDE_FOURNISSEUR_INCONNU", "Le fournisseur n'appartient pas à ce centre");
        }
        String createur = auteur == null || auteur.isBlank() ? AUTEUR_PAR_DEFAUT : auteur;
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);
        return bons.creer(centre, reference -> BonDeCommande.brouillon(identifiants.get(), centre, reference, fournisseurId,
                createur, maintenant, lignes));
    }

    public BonDeCommande remplacerLesLignes(CentreId centre, UUID id, List<LigneSaisie> saisies) {
        List<LigneDeBon> lignes = lignes(saisies);
        exigerLesArticlesDuCentre(centre, lignes);
        return bons.modifier(centre, id, bon -> bon.remplacerLesLignes(lignes)).orElseThrow(GestionDesBonsDeCommande::introuvable);
    }

    public BonDeCommande valider(CentreId centre, UUID id) {
        return bons.modifier(centre, id, BonDeCommande::valider).orElseThrow(GestionDesBonsDeCommande::introuvable);
    }

    /**
     * @throws RefusDuReferentiel {@code BON_COMMANDE_INTROUVABLE} (422, comme les articles et les groupes) : inconnu ou d'un
     *                            autre centre
     */
    public BonDeCommande consulter(CentreId centre, UUID id) {
        return bons.parId(centre, id).orElseThrow(GestionDesBonsDeCommande::introuvable);
    }

    public List<BonDeCommande> lister(CentreId centre) {
        return bons.tous(centre);
    }

    private List<LigneDeBon> lignes(List<LigneSaisie> saisies) {
        if (saisies == null) {
            return List.of();
        }
        return saisies.stream().map(l -> LigneDeBon.saisie(l.id() != null ? l.id() : identifiants.get(), l.articleId(),
                l.quantite(), l.prixUnitaire())).toList();
    }

    private void exigerLesArticlesDuCentre(CentreId centre, List<LigneDeBon> lignes) {
        if (lignes.isEmpty()) {
            return;
        }
        var connus = articles.identifiants(centre);
        if (!lignes.stream().allMatch(l -> connus.contains(l.articleId()))) {
            throw new RefusDuReferentiel("BON_COMMANDE_ARTICLE_INCONNU", "Une ligne porte un article qui n'appartient pas à ce centre");
        }
    }

    /**
     * Ligne telle que saisie : identifiant facultatif (généré s'il manque).
     */
    public record LigneSaisie(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire) {
    }
}

