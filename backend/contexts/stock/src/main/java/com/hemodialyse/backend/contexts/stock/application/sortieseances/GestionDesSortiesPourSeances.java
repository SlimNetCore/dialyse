package com.hemodialyse.backend.contexts.stock.application.sortieseances;

import com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock.LotDuStock;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage des sorties pour séances d'un centre (ADR 0045, T35 à T44) : enregistrer une pièce (le stock sort à
 * l'enregistrement, FEFO, PMP courant, mouvements datés du jour d'enregistrement), préparer le rapprochement (constat des
 * consommations imputées), justifier un écart, valider (sortie supplémentaire pour un écart « en plus », retour en stock à la valeur
 * de sortie pour un écart « en moins »). Chaque écriture est une transaction, articles verrouillés par identifiant croissant, puis
 * rejeu du PMP des articles touchés. Le rôle du validateur est contrôlé à l'entrée HTTP.
 */
public final class GestionDesSortiesPourSeances {

    private static final String AUTEUR_PAR_DEFAUT = "system";

    private final SortiesPourSeances pieces;
    private final Articles articles;
    private final SortiesDuStock stock;
    private final EcrituresDuStock ecritures;
    private final RejeuDuPmp rejeu;
    private final UniteDeTravail uniteDeTravail;
    private final NotificationsDuStock notifications;
    private final Supplier<UUID> identifiants;
    private final Clock horloge;

    public GestionDesSortiesPourSeances(SortiesPourSeances pieces, Articles articles, SortiesDuStock stock, EcrituresDuStock ecritures,
                                        RejeuDuPmp rejeu, UniteDeTravail uniteDeTravail, NotificationsDuStock notifications,
                                        Supplier<UUID> identifiants, Clock horloge) {
        this.pieces = pieces;
        this.articles = articles;
        this.stock = stock;
        this.ecritures = ecritures;
        this.rejeu = rejeu;
        this.uniteDeTravail = uniteDeTravail;
        this.notifications = notifications;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    private static RefusDuReferentiel introuvable() {
        return new RefusDuReferentiel("PIECE_INTROUVABLE", "Sortie pour séances introuvable");
    }

    private static String auteur(String auteur) {
        return auteur == null || auteur.isBlank() ? AUTEUR_PAR_DEFAUT : auteur;
    }

    // ------------------------------------------------------------------ enregistrement (T35, T36)

    /**
     * @param jour jour des séances visées (le stock sort à la date d'aujourd'hui)
     * @throws RefusDuReferentiel {@code PIECE_SANS_LIGNE}, {@code PIECE_LIGNE_INVALIDE}, {@code PIECE_ARTICLE_INCONNU},
     *                            {@code PIECE_ARTICLE_INACTIF}, {@code STOCK_INSUFFISANT}, {@code PIECE_PRECEDENTE_NON_RAPPROCHEE},
     *                            {@code STOCK_INVENTORY_IN_PROGRESS}, {@code STOCK_PERIOD_CLOSED}
     */
    public SortiePourSeances enregistrer(CentreId centre, LocalDate jour, PerimetreDeSortie perimetre, String auteur,
                                         List<Demande> demandes) {
        if (demandes == null || demandes.isEmpty()) {
            throw new RefusDuReferentiel("PIECE_SANS_LIGNE", "Une sortie pour séances doit contenir au moins une ligne");
        }
        Map<UUID, BigDecimal> parArticle = new LinkedHashMap<>();
        for (Demande demande : demandes) {
            if (demande.articleId() == null || demande.quantite() == null || demande.quantite().signum() <= 0) {
                throw new RefusDuReferentiel("PIECE_LIGNE_INVALIDE", "Chaque ligne exige un article et une quantité strictement positive");
            }
            parArticle.merge(demande.articleId(), demande.quantite(), BigDecimal::add);
        }
        String par = auteur(auteur);
        LocalDate aujourdHui = LocalDate.now(horloge);
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);
        PerimetreDeSortie cible = perimetre != null ? perimetre : PerimetreDeSortie.journee();
        return uniteDeTravail.executer(() -> {
            Set<UUID> touches = new TreeSet<>(parArticle.keySet());
            ecritures.verrouiller(centre, touches);
            ecritures.exigerAucunInventaireEnCours(centre);
            ecritures.exigerUneDateOuverte(centre, aujourdHui);
            pieces.dernieresDuPerimetre(centre, cible).filter(p -> !p.estRapprochee()).ifPresent(p -> {
                throw new RefusDuReferentiel("PIECE_PRECEDENTE_NON_RAPPROCHEE",
                        "La sortie pour séances " + p.reference() + " du même périmètre n'est pas rapprochée");
            });
            List<LigneDePiece> lignes = new ArrayList<>();
            int rang = 0;
            for (Map.Entry<UUID, BigDecimal> ligne : parArticle.entrySet()) {
                Article article = articles.parId(centre, ligne.getKey()).orElseThrow(
                        () -> new RefusDuReferentiel("PIECE_ARTICLE_INCONNU", "Article inconnu pour ce centre"));
                if (!article.actif()) {
                    throw new RefusDuReferentiel("PIECE_ARTICLE_INACTIF", "Article inactif : " + article.fiche().code());
                }
                BigDecimal pmp = article.pmp() != null ? article.pmp() : BigDecimal.ZERO;
                BigDecimal restant = ligne.getValue();
                for (LotDuStock lot : stock.lotsDisponibles(centre, article.id())) {
                    if (restant.signum() <= 0) {
                        break;
                    }
                    BigDecimal prise = restant.min(lot.quantiteRestante());
                    stock.consommer(centre, lot.id(), prise);
                    stock.enregistrerSortie(centre, article.id(), BonDeSortie.AUCUNE_SEANCE, lot.id(), prise, pmp, par, aujourdHui,
                            maintenant.plusNanos(++rang * 1_000L));
                    lignes.add(LigneDePiece.sortie(identifiants.get(), article.id(), lot.id(), prise, pmp));
                    restant = restant.subtract(prise);
                }
                if (restant.signum() > 0) {
                    throw new RefusDuReferentiel("STOCK_INSUFFISANT", "Stock insuffisant pour l'article " + article.fiche().code()
                            + " (manque : " + restant.toPlainString() + ")");
                }
            }
            SortiePourSeances piece = pieces.creer(centre, reference -> SortiePourSeances.enregistrer(identifiants.get(), centre,
                    reference, jour != null ? jour : aujourdHui, cible, maintenant, par, lignes));
            touches.forEach(a -> rejeu.rejouer(centre, a));
            notifications.mouvementsModifies(centre.valeur(), "SORTIE", piece.reference(), touches.size());
            return piece;
        });
    }

    // ------------------------------------------------------------------ rapprochement (T40, T41)

    /**
     * Constate les consommations imputées sur la pièce (consommé et écart de chaque ligne ; l'excédent devient une ligne sans lot).
     */
    public SortiePourSeances preparerLeRapprochement(CentreId centre, UUID id) {
        return uniteDeTravail.executer(() -> constater(centre, id));
    }

    private SortiePourSeances constater(CentreId centre, UUID id) {
        SortiePourSeances piece = consulter(centre, id);
        piece.exigerNonRapprochee();
        SortiesPourSeances.Consommations consommations = pieces.consommations(centre, id);
        return pieces.modifier(centre, id, p -> p.constater(consommations.parLot(), consommations.horsLots(), identifiants))
                .orElseThrow(GestionDesSortiesPourSeances::introuvable);
    }

    /**
     * @throws RefusDuReferentiel {@code ECART_SANS_MOTIF} (motif vide), {@code PIECE_LIGNE_INCONNUE}, {@code PIECE_DEJA_RAPPROCHEE}
     */
    public SortiePourSeances justifierUnEcart(CentreId centre, UUID id, UUID ligneId, String motif) {
        return uniteDeTravail.executer(() -> pieces.modifier(centre, id, p -> p.justifier(ligneId, motif))
                .orElseThrow(GestionDesSortiesPourSeances::introuvable));
    }

    /**
     * Valide le rapprochement : constat des consommations à jour, motif exigé pour tout écart, puis par ligne, écart « en plus » ⇒
     * sortie supplémentaire au PMP courant (lots de la pièce d'abord, puis FEFO), écart « en moins » ⇒ retour en stock dans le lot
     * d'origine à la valeur de sortie ; la pièce est ensuite figée.
     */
    public SortiePourSeances valider(CentreId centre, UUID id, String validateur) {
        String par = auteur(validateur);
        LocalDate aujourdHui = LocalDate.now(horloge);
        OffsetDateTime maintenant = OffsetDateTime.now(horloge);
        return uniteDeTravail.executer(() -> {
            SortiePourSeances courante = consulter(centre, id);
            courante.exigerNonRapprochee();
            Set<UUID> touches = new TreeSet<>();
            courante.lignes().forEach(l -> touches.add(l.articleId()));
            SortiesPourSeances.Consommations consommations = pieces.consommations(centre, id);
            consommations.horsLots().keySet().forEach(touches::add);
            ecritures.verrouiller(centre, touches);
            SortiePourSeances constatee = constater(centre, id);
            constatee.exigerRapprochable();
            ecritures.exigerAucunInventaireEnCours(centre);
            ecritures.exigerUneDateOuverte(centre, aujourdHui);
            int rang = 0;
            for (LigneDePiece ligne : constatee.lignes()) {
                if (!ligne.aUnEcart()) {
                    continue;
                }
                if (ligne.ecart().signum() > 0) {
                    rang = sortieSupplementaire(centre, constatee, ligne, par, aujourdHui, maintenant, rang);
                } else {
                    stock.restituer(centre, ligne.lotId(), ligne.ecart().negate());
                    stock.enregistrerRetour(centre, ligne.articleId(), ligne.lotId(), ligne.ecart().negate(), ligne.valeurUnitaire(),
                            par, aujourdHui, maintenant.plusNanos(++rang * 1_000L));
                }
            }
            SortiePourSeances rapprochee = pieces.modifier(centre, id, p -> p.rapprocher(par, maintenant))
                    .orElseThrow(GestionDesSortiesPourSeances::introuvable);
            touches.forEach(a -> rejeu.rejouer(centre, a));
            notifications.mouvementsModifies(centre.valeur(), "SORTIE", rapprochee.reference(), touches.size());
            return rapprochee;
        });
    }

    private int sortieSupplementaire(CentreId centre, SortiePourSeances piece, LigneDePiece ligne, String auteur, LocalDate jour,
                                     OffsetDateTime maintenant, int rang) {
        Article article = articles.parId(centre, ligne.articleId()).orElseThrow(
                () -> new RefusDuReferentiel("PIECE_ARTICLE_INCONNU", "Article inconnu pour ce centre"));
        BigDecimal pmp = article.pmp() != null ? article.pmp() : BigDecimal.ZERO;
        Set<UUID> lotsDeLaPiece = new LinkedHashSet<>();
        piece.lignes().stream().filter(l -> l.articleId().equals(ligne.articleId()) && l.lotId() != null)
                .forEach(l -> lotsDeLaPiece.add(l.lotId()));
        List<LotDuStock> disponibles = stock.lotsDisponibles(centre, ligne.articleId());
        List<LotDuStock> ordre = new ArrayList<>(disponibles.stream().filter(l -> lotsDeLaPiece.contains(l.id())).toList());
        ordre.addAll(disponibles.stream().filter(l -> !lotsDeLaPiece.contains(l.id())).toList());
        BigDecimal restant = ligne.ecart();
        for (LotDuStock lot : ordre) {
            if (restant.signum() <= 0) {
                break;
            }
            BigDecimal prise = restant.min(lot.quantiteRestante());
            stock.consommer(centre, lot.id(), prise);
            stock.enregistrerSortie(centre, ligne.articleId(), BonDeSortie.AUCUNE_SEANCE, lot.id(), prise, pmp, auteur, jour,
                    maintenant.plusNanos(++rang * 1_000L));
            restant = restant.subtract(prise);
        }
        if (restant.signum() > 0) {
            throw new RefusDuReferentiel("STOCK_INSUFFISANT", "Stock insuffisant pour la sortie supplémentaire de l'article "
                    + article.fiche().code() + " (manque : " + restant.toPlainString() + ")");
        }
        return rang;
    }

    // ------------------------------------------------------------------ lecture

    /**
     * @throws RefusDuReferentiel {@code PIECE_INTROUVABLE} : inconnue ou d'un autre centre
     */
    public SortiePourSeances consulter(CentreId centre, UUID id) {
        return pieces.parId(centre, id).orElseThrow(GestionDesSortiesPourSeances::introuvable);
    }

    public List<SortiePourSeances> lister(CentreId centre) {
        return pieces.toutes(centre);
    }

    /**
     * Ligne demandée à l'enregistrement : article et quantité à sortir.
     */
    public record Demande(UUID articleId, BigDecimal quantite) {
    }
}


