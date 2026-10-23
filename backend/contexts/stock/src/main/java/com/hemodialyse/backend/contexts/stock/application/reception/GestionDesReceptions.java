package com.hemodialyse.backend.contexts.stock.application.reception;

import com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommandeInvalide;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonsDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.StatutDuBon;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonsDeReception;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock.Lot;
import com.hemodialyse.backend.contexts.stock.domain.reception.LigneDeReception;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Fournisseurs;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage des bons de réception d'un centre (RG-STK-020 à 025, 040, 041) : créer, créer depuis un bon de commande, modifier
 * (brouillon puis correction d'un bon validé), valider, consulter, lister. La validation et la correction s'exécutent dans une
 * seule transaction : articles verrouillés par identifiant croissant, lots, mouvements d'entrée, rejeu du PMP, bon de
 * commande {@code RECU} — bon entier ou rien (ADR 0045).
 */
public final class GestionDesReceptions {

    private static final String AUTEUR_PAR_DEFAUT = "system";

    private final BonsDeReception bons;
    private final BonsDeCommande commandes;
    private final Articles articles;
    private final Fournisseurs fournisseurs;
    private final EcrituresDuStock ecritures;
    private final RejeuDuPmp rejeu;
    private final UniteDeTravail uniteDeTravail;
    private final NotificationsDuStock notifications;
    private final Supplier<UUID> identifiants;
    private final Clock horloge;

    public GestionDesReceptions(BonsDeReception bons, BonsDeCommande commandes, Articles articles, Fournisseurs fournisseurs,
                                EcrituresDuStock ecritures, RejeuDuPmp rejeu, UniteDeTravail uniteDeTravail,
                                NotificationsDuStock notifications, Supplier<UUID> identifiants, Clock horloge) {
        this.bons = bons;
        this.commandes = commandes;
        this.articles = articles;
        this.fournisseurs = fournisseurs;
        this.ecritures = ecritures;
        this.rejeu = rejeu;
        this.uniteDeTravail = uniteDeTravail;
        this.notifications = notifications;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    private static RefusDuReferentiel introuvable() {
        return new RefusDuReferentiel("RECEPTION_INTROUVABLE", "Bon de réception introuvable");
    }

    // ------------------------------------------------------------------ création

    public BonDeReception creer(CentreId centre, UUID bonCommandeId, UUID fournisseurId, LocalDate date, String auteur,
                                List<LigneSaisie> saisies) {
        List<LigneDeReception> lignes = lignes(saisies);
        exigerLesArticlesDuCentre(centre, lignes);
        exigerLeFournisseur(centre, fournisseurId);
        if (bonCommandeId != null) {
            exigerUnBonDeCommandeReceptionnable(centre, bonCommandeId);
        }
        return enregistrerLeBrouillon(centre, bonCommandeId, fournisseurId, date, auteur, lignes);
    }

    /**
     * @throws RefusDuReferentiel {@code BON_COMMANDE_INTROUVABLE}, {@code BON_COMMANDE_NON_VALIDE}
     */
    public BonDeReception depuisUnBonDeCommande(CentreId centre, UUID bonCommandeId, String auteur) {
        BonDeCommande commande = exigerUnBonDeCommandeReceptionnable(centre, bonCommandeId);
        List<LigneDeReception> lignes = commande.lignes().stream().map(l -> LigneDeReception.saisie(identifiants.get(),
                l.articleId(), l.quantite(), l.prixUnitaire(), null, null, null)).toList();
        return enregistrerLeBrouillon(centre, bonCommandeId, commande.fournisseurId(), null, auteur, lignes);
    }

    private BonDeReception enregistrerLeBrouillon(CentreId centre, UUID bonCommandeId, UUID fournisseurId, LocalDate date,
                                                  String auteur, List<LigneDeReception> lignes) {
        String createur = auteur == null || auteur.isBlank() ? AUTEUR_PAR_DEFAUT : auteur;
        LocalDate jour = date != null ? date : LocalDate.now(horloge);
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);
        return bons.creer(centre, reference -> BonDeReception.brouillon(identifiants.get(), centre, reference, bonCommandeId,
                fournisseurId, jour, createur, maintenant, lignes));
    }

    // ------------------------------------------------------------------ modification

    public BonDeReception modifier(CentreId centre, UUID id, UUID fournisseurId, LocalDate date, List<LigneSaisie> saisies) {
        List<LigneDeReception> lignes = lignes(saisies);
        exigerLesArticlesDuCentre(centre, lignes);
        exigerLeFournisseur(centre, fournisseurId);
        return uniteDeTravail.executer(() -> {
            BonDeReception bon = consulter(centre, id);
            if (!bon.estValide()) {
                return bons.modifier(centre, id, b -> b.modifierLeBrouillon(fournisseurId, date, lignes))
                        .orElseThrow(GestionDesReceptions::introuvable);
            }
            return corriger(centre, bon, fournisseurId, date, lignes);
        });
    }

    /**
     * Correction d'un bon validé : date et fournisseur, puis, si des lignes sont fournies, quantité, prix, numéro et péremption
     * de chaque lot (nombre de lignes et articles inchangés, quantité au moins égale à la quantité déjà consommée du lot) ;
     * les articles touchés sont rejoués.
     */
    private BonDeReception corriger(CentreId centre, BonDeReception bon, UUID fournisseurId, LocalDate date,
                                    List<LigneDeReception> demandees) {
        LocalDate jour = date != null ? date : bon.dateReception();
        List<Lot> lots = ecritures.lotsDuBon(centre, bon.id());
        Set<UUID> touches = new TreeSet<>();
        lots.forEach(l -> touches.add(l.articleId()));
        demandees.forEach(l -> touches.add(l.articleId()));
        ecritures.verrouiller(centre, touches);
        ecritures.exigerAucunInventaireEnCours(centre);
        ecritures.exigerUneDateOuverte(centre, jour);

        for (Lot lot : lots) {
            var entree = ecritures.entreeDuLot(centre, lot.id()).orElse(null);
            if (entree != null && entree.cloture()) {
                throw new RefusDuReferentiel("STOCK_MOVEMENT_CLOSED",
                        "Ce mouvement appartient à une période clôturée par inventaire : il ne peut plus être modifié.");
            }
            if (entree != null) {
                ecritures.dater(entree.mouvementId(), jour);
            }
        }
        List<LigneDeReception> lignesFinales = bon.lignes();
        if (!demandees.isEmpty()) {
            if (demandees.size() != lots.size()) {
                throw new RefusDuReferentiel("RECEPTION_NOMBRE_DE_LIGNES_FIXE",
                        "Pour un bon de réception déjà validé, le nombre de lignes ne peut pas changer.");
            }
            lignesFinales = new ArrayList<>();
            List<Lot> restants = new ArrayList<>(lots);
            restants.sort(Comparator.comparing(Lot::numero, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Lot::id));
            Map<UUID, UUID> lotDesLignesExistantes = new HashMap<>();
            bon.lignes().stream().filter(l -> l.lotId() != null).forEach(l -> lotDesLignesExistantes.put(l.id(), l.lotId()));
            for (int i = 0; i < demandees.size(); i++) {
                LigneDeReception demandee = demandees.get(i);
                if (demandee.prixUnitaire() == null) {
                    throw new BonDeCommandeInvalide("Le prix unitaire est requis pour corriger un bon de réception validé");
                }
                Lot lot = apparier(demandee, lotDesLignesExistantes, restants);
                lignesFinales.add(corrigerLeLot(centre, bon, jour, demandee, lot, i));
            }
        }
        for (UUID article : touches) {
            rejeu.rejouer(centre, article);
        }
        List<LigneDeReception> finales = lignesFinales;
        return bons.modifier(centre, bon.id(), b -> b.corriger(fournisseurId, date, finales)).orElseThrow(GestionDesReceptions::introuvable);
    }

    private Lot apparier(LigneDeReception demandee, Map<UUID, UUID> lotDesLignesExistantes, List<Lot> restants) {
        UUID lotConnu = lotDesLignesExistantes.get(demandee.id());
        Optional<Lot> trouve = lotConnu != null
                ? restants.stream().filter(l -> l.id().equals(lotConnu) && l.articleId().equals(demandee.articleId())).findFirst()
                : restants.stream().filter(l -> l.articleId().equals(demandee.articleId())).findFirst();
        Lot lot = trouve.orElseThrow(() -> new RefusDuReferentiel("RECEPTION_ARTICLE_FIXE",
                "Pour un bon de réception déjà validé, l'article d'une ligne ne peut pas être modifié."));
        restants.remove(lot);
        return lot;
    }

    private LigneDeReception corrigerLeLot(CentreId centre, BonDeReception bon, LocalDate jour, LigneDeReception demandee, Lot lot,
                                           int rang) {
        Article article = articles.parId(centre, demandee.articleId()).orElseThrow(
                () -> new RefusDuReferentiel("RECEPTION_ARTICLE_INCONNU", "Article inconnu pour ce centre"));
        BigDecimal consomme = lot.quantiteInitiale().subtract(lot.quantiteRestante());
        BigDecimal restante = demandee.quantite().subtract(consomme);
        if (restante.signum() < 0) {
            throw new RefusDuReferentiel("RECEPTION_QUANTITE_INFERIEURE_AU_CONSOMME",
                    "Quantité saisie inférieure à la quantité déjà consommée pour le lot " + lot.numero());
        }
        String numero = numeroDeLot(article, demandee.numeroLot(), bon.reference(), rang);
        LocalDate peremption = peremption(article, demandee.datePeremption(), numero);
        ecritures.modifierLot(new Lot(lot.id(), lot.centre(), lot.articleId(), lot.bonReceptionId(), lot.emplacementId(), numero,
                peremption, demandee.quantite(), restante, demandee.prixUnitaire()));
        ecritures.entreeDuLot(centre, lot.id()).ifPresent(e -> {
            if (!e.cloture()) {
                ecritures.corrigerEntree(e.mouvementId(), demandee.quantite(), demandee.prixUnitaire(), jour);
            }
        });
        return demandee.avecLot(lot.id());
    }

    // ------------------------------------------------------------------ validation

    public BonDeReception valider(CentreId centre, UUID id, String auteur) {
        BonDeReception valide = uniteDeTravail.executer(() -> {
            BonDeReception bon = consulter(centre, id);
            bon.exigerValidable();
            if (bon.bonCommandeId() != null) {
                exigerUnBonDeCommandeReceptionnable(centre, bon.bonCommandeId());
            }
            Set<UUID> touches = new TreeSet<>();
            bon.lignes().forEach(l -> touches.add(l.articleId()));
            ecritures.verrouiller(centre, touches);
            ecritures.exigerAucunInventaireEnCours(centre);
            ecritures.exigerUneDateOuverte(centre, bon.dateReception());

            String createur = auteur == null || auteur.isBlank() ? AUTEUR_PAR_DEFAUT : auteur;
            OffsetDateTime maintenant = OffsetDateTime.now(horloge);
            List<LigneDeReception> avecLot = new ArrayList<>();
            for (int i = 0; i < bon.lignes().size(); i++) {
                LigneDeReception ligne = bon.lignes().get(i);
                if (ligne.quantite() == null || ligne.quantite().signum() <= 0) {
                    throw new BonDeCommandeInvalide("Quantité invalide pour l'article " + ligne.articleId());
                }
                Article article = articles.parId(centre, ligne.articleId()).orElseThrow(
                        () -> new RefusDuReferentiel("RECEPTION_ARTICLE_INCONNU", "Article inconnu pour ce centre"));
                String numero = numeroDeLot(article, ligne.numeroLot(), bon.reference(), i);
                LocalDate peremption = peremption(article, ligne.datePeremption(), numero);
                UUID lotId = identifiants.get();
                ecritures.creerLot(new Lot(lotId, centre, ligne.articleId(), bon.id(), ligne.emplacementId(), numero, peremption,
                        ligne.quantite(), ligne.quantite(), ligne.prixUnitaire()));
                ecritures.enregistrerEntree(centre, ligne.articleId(), lotId, ligne.quantite(), ligne.prixUnitaire(), createur,
                        bon.dateReception(), maintenant.plusNanos(i * 1_000L));
                avecLot.add(ligne.avecLot(lotId));
            }
            for (UUID article : touches) {
                rejeu.rejouer(centre, article);
            }
            BonDeReception resultat = bons.modifier(centre, id, b -> b.valider(avecLot)).orElseThrow(GestionDesReceptions::introuvable);
            if (bon.bonCommandeId() != null) {
                commandes.modifier(centre, bon.bonCommandeId(), BonDeCommande::marquerRecu);
            }
            notifications.mouvementsModifies(centre.valeur(), "ENTREE", bon.reference(), touches.size());
            return resultat;
        });
        return valide;
    }

    // ------------------------------------------------------------------ lecture

    /**
     * @throws RefusDuReferentiel {@code RECEPTION_INTROUVABLE} (422, comme les autres bons) : inconnu ou d'un autre centre
     */
    public BonDeReception consulter(CentreId centre, UUID id) {
        return bons.parId(centre, id).orElseThrow(GestionDesReceptions::introuvable);
    }

    public List<BonDeReception> lister(CentreId centre) {
        return bons.tous(centre);
    }

    // ------------------------------------------------------------------ outils

    private List<LigneDeReception> lignes(List<LigneSaisie> saisies) {
        if (saisies == null) {
            return List.of();
        }
        return saisies.stream().map(l -> LigneDeReception.saisie(l.id() != null ? l.id() : identifiants.get(), l.articleId(),
                l.quantite(), l.prixUnitaire(), l.numeroLot(), l.datePeremption(), l.emplacementId())).toList();
    }

    private void exigerLesArticlesDuCentre(CentreId centre, List<LigneDeReception> lignes) {
        if (lignes.isEmpty()) {
            return;
        }
        var connus = articles.identifiants(centre);
        if (!lignes.stream().allMatch(l -> connus.contains(l.articleId()))) {
            throw new RefusDuReferentiel("RECEPTION_ARTICLE_INCONNU", "Une ligne porte un article qui n'appartient pas à ce centre");
        }
    }

    private void exigerLeFournisseur(CentreId centre, UUID fournisseurId) {
        if (fournisseurId != null && !fournisseurs.existe(centre, fournisseurId)) {
            throw new RefusDuReferentiel("RECEPTION_FOURNISSEUR_INCONNU", "Le fournisseur n'appartient pas à ce centre");
        }
    }

    private BonDeCommande exigerUnBonDeCommandeReceptionnable(CentreId centre, UUID bonCommandeId) {
        BonDeCommande commande = commandes.parId(centre, bonCommandeId).orElseThrow(
                () -> new RefusDuReferentiel("BON_COMMANDE_INTROUVABLE", "Bon de commande introuvable"));
        if (commande.statut() == StatutDuBon.BROUILLON) {
            throw new RefusDuReferentiel("BON_COMMANDE_NON_VALIDE", "Un bon de commande doit être validé avant d'être réceptionné");
        }
        return commande;
    }

    private static String numeroDeLot(Article article, String numero, String reference, int index) {
        boolean gere = article.fiche().gereParLot();
        if (gere && (numero == null || numero.isBlank())) {
            throw new RefusDuReferentiel("RECEPTION_NUMERO_DE_LOT_REQUIS", "Numéro de lot obligatoire pour un article géré par lot");
        }
        if (numero != null && !numero.isBlank()) {
            return numero.trim();
        }
        return String.format("AUTO-%s-%02d", reference != null ? reference : "BR", index + 1);
    }

    private static LocalDate peremption(Article article, LocalDate date, String numero) {
        if (article.fiche().gereParLot() && date == null) {
            throw new RefusDuReferentiel("RECEPTION_PEREMPTION_REQUISE", "Date de péremption obligatoire pour le lot " + numero);
        }
        return date;
    }

    /**
     * Ligne telle que saisie : identifiant facultatif (généré s'il manque).
     */
    public record LigneSaisie(UUID id, UUID articleId, BigDecimal quantite, BigDecimal prixUnitaire, String numeroLot,
                              LocalDate datePeremption, UUID emplacementId) {
    }
}




