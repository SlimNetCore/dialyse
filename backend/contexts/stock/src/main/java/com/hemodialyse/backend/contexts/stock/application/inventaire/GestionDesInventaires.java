package com.hemodialyse.backend.contexts.stock.application.inventaire;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.ComptageImporte;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.PeriodeDeStock;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.ResultatDImport;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.StockInventorie;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Cas d'usage de l'inventaire (S7, RG-STK-050 à 057) : ouvrir, consulter, lister, compter, ajouter ou retirer une ligne, reporter le
 * théorique, clôturer, annuler. La clôture est une transaction unique, tout ou rien : les quantités comptées deviennent le stock des
 * lots, les mouvements des articles inventoriés sont figés, un mouvement {@code INVENTAIRE} par lot compté pose le stock de départ,
 * puis le PMP de ces articles est rejoué. Les articles sans ligne (inactifs) ne sont ni figés ni rejoués (KB-STK-50).
 */
public final class GestionDesInventaires {

    private final Inventaires inventaires;
    private final StockInventorie stock;
    private final Articles articles;
    private final EcrituresDuStock ecritures;
    private final RejeuDuPmp rejeu;
    private final SortiesPourSeances sorties;
    private final Predicate<CentreId> recalculEnCours;
    private final UniteDeTravail uniteDeTravail;
    private final NotificationsDInventaire notifications;
    private final HorlogeDesCentres horloge;
    private final Supplier<UUID> identifiants;

    public GestionDesInventaires(Inventaires inventaires, StockInventorie stock, Articles articles, EcrituresDuStock ecritures,
                                 RejeuDuPmp rejeu, SortiesPourSeances sorties, Predicate<CentreId> recalculEnCours,
                                 UniteDeTravail uniteDeTravail, NotificationsDInventaire notifications, HorlogeDesCentres horloge,
                                 Supplier<UUID> identifiants) {
        this.inventaires = inventaires;
        this.stock = stock;
        this.articles = articles;
        this.ecritures = ecritures;
        this.rejeu = rejeu;
        this.sorties = sorties;
        this.recalculEnCours = recalculEnCours;
        this.uniteDeTravail = uniteDeTravail;
        this.notifications = notifications;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    private static String par(String utilisateur) {
        return utilisateur != null ? utilisateur : "system";
    }

    private static RefusDuReferentiel introuvable() {
        return new RefusDuReferentiel("INVENTORY_NOT_FOUND", "Inventaire introuvable pour ce centre.");
    }

    // ------------------------------------------------------------------ lectures

    /**
     * Ligne visée : par identifiant (colonne cachée), sinon par code article + n° de lot.
     */
    private static List<LigneDInventaire> rapprocher(Inventaire inventaire, ComptageImporte row) {
        if (row.ligneId() != null) {
            Optional<LigneDInventaire> parId = inventaire.lignes().stream().filter(l -> l.id().equals(row.ligneId())).findFirst();
            if (parId.isPresent()) {
                return List.of(parId.get());
            }
        }
        String code = norm(row.articleCode());
        if (code.isEmpty()) {
            return List.of();
        }
        String lot = norm(row.numeroLot());
        List<LigneDInventaire> memeArticle = inventaire.lignes().stream().filter(l -> code.equals(norm(l.articleCode()))).toList();
        if (lot.isEmpty()) {
            List<LigneDInventaire> sansLot = memeArticle.stream().filter(l -> norm(l.numeroLot()).isEmpty()).toList();
            return sansLot.isEmpty() ? memeArticle : sansLot;
        }
        return memeArticle.stream().filter(l -> lot.equals(norm(l.numeroLot()))).toList();
    }

    /**
     * « 12,000 » saisi dans un tableur = 12 (le contrôle des 3 décimales porte sur la valeur, pas sur l'écriture).
     */
    private static BigDecimal sansZerosInutiles(BigDecimal valeur) {
        BigDecimal reduite = valeur.stripTrailingZeros();
        return reduite.scale() < 0 ? reduite.setScale(0) : reduite;
    }

    private static String norm(String valeur) {
        return valeur == null ? "" : valeur.trim().toUpperCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------ écritures

    private static String nz(String valeur) {
        return valeur == null || valeur.isBlank() ? "?" : valeur.trim();
    }

    /**
     * @throws RefusDuReferentiel {@code INVENTORY_NOT_FOUND}
     */
    public Inventaire consulter(CentreId centre, UUID id) {
        return inventaires.parId(centre, id).orElseThrow(GestionDesInventaires::introuvable);
    }

    public Inventaires.PageDInventaires lister(CentreId centre, int page, int taille) {
        return inventaires.lister(centre, Math.max(page, 0), taille <= 0 ? 20 : Math.min(taille, 100));
    }

    /**
     * Situation du centre : l'inventaire en cours (s'il y en a un) et la date de la dernière clôture.
     */
    public Etat etat(CentreId centre) {
        return new Etat(inventaires.enCours(centre), inventaires.periode(centre).derniereCloture());
    }

    /**
     * @throws RefusDuReferentiel les refus de {@link PeriodeDeStock#exigerOuvrable} et de {@link Inventaire#ouvrir}
     */
    public Inventaire ouvrir(CentreId centre, LocalDate dateDemandee, String commentaire, String utilisateur) {
        OffsetDateTime maintenant = horloge.maintenant();
        LocalDate date = dateDemandee != null ? dateDemandee : maintenant.toLocalDate();
        String par = par(utilisateur);
        return uniteDeTravail.executer(() -> {
            inventaires.verrouillerLaPeriode(centre);
            // Une écriture de stock commencée avant l'ouverture se termine avant le gel (l'ouverture attend les verrous des articles) ;
            // celles qui suivent voient l'inventaire en cours et sont refusées (elles vérifient après avoir pris leurs verrous).
            ecritures.verrouiller(centre, articles.identifiants(centre));
            inventaires.periode(centre).exigerOuvrable(date, sorties.premiereNonRapprochee(centre),
                    stock.mouvementsApres(centre, Inventaire.coupure(date)), recalculEnCours.test(centre));
            Inventaire ouvert = inventaires.creer(centre, reference -> Inventaire.ouvrir(identifiants.get(), centre, reference, date,
                    commentaire, par, maintenant, stock.theorique(centre)));
            notifier(ouvert, par);
            return ouvert;
        });
    }

    public Inventaire compter(CentreId centre, UUID id, UUID ligne, BigDecimal quantite, String motif, String utilisateur) {
        String par = par(utilisateur);
        return modifier(centre, id, inventaire -> inventaire.compter(ligne, quantite, motif, par, horloge.maintenant()));
    }

    /**
     * Ajoute un lot trouvé en rayon (théorique 0) et le compte ; un lot épuisé retrouvé est rattaché à son lot existant.
     *
     * @throws RefusDuReferentiel {@code INVENTORY_ARTICLE_NOT_FOUND}, {@code INVENTORY_LOT_REQUIRED},
     *                            {@code INVENTORY_LINE_DUPLICATE}, et les refus du comptage
     */
    public Inventaire ajouterUneLigne(CentreId centre, UUID id, UUID articleId, String numeroLot, LocalDate datePeremption,
                                      BigDecimal quantite, String motif, String utilisateur) {
        String par = par(utilisateur);
        return modifier(centre, id, inventaire -> {
            Article article = articles.parId(centre, articleId).orElseThrow(() -> new RefusDuReferentiel(
                    "INVENTORY_ARTICLE_NOT_FOUND", "Article introuvable pour ce centre."));
            String numero = numeroLot == null || numeroLot.isBlank() ? null : numeroLot.trim();
            if (article.fiche().gereParLot() && numero == null) {
                throw new RefusDuReferentiel("INVENTORY_LOT_REQUIRED", "L'article " + article.fiche().code()
                        + " est géré par lot : le numéro de lot est obligatoire.");
            }
            Optional<StockInventorie.LotConnu> existant = numero == null ? Optional.empty()
                    : stock.lotDeLArticle(centre, articleId, numero);
            LigneDInventaire ligne = LigneDInventaire.theorique(identifiants.get(), articleId, article.fiche().code(),
                    article.fiche().libelle(), article.fiche().unite(), existant.map(StockInventorie.LotConnu::id).orElse(null),
                    numero, existant.isPresent() && datePeremption == null ? existant.get().datePeremption() : datePeremption,
                    BigDecimal.ZERO, article.pmp());
            return inventaire.ajouterLigne(ligne, quantite, motif, par, horloge.maintenant());
        });
    }

    public Inventaire retirerUneLigne(CentreId centre, UUID id, UUID ligne) {
        return modifier(centre, id, inventaire -> inventaire.retirerLigne(ligne));
    }

    public Inventaire reporterLeTheorique(CentreId centre, UUID id, String utilisateur) {
        String par = par(utilisateur);
        return modifier(centre, id, inventaire -> inventaire.reporterLeTheorique(par, horloge.maintenant()));
    }

    /**
     * Importe une feuille de comptage remplie : les quantités (et motifs) renseignés sont enregistrés, les lignes vides ignorées, les
     * lignes invalides listées dans le bilan sans bloquer les autres.
     *
     * @throws RefusDuReferentiel {@code INVENTORY_NOT_FOUND}, {@code INVENTORY_NOT_IN_PROGRESS}
     */
    public ResultatDImport importerUnComptage(CentreId centre, UUID id, List<ComptageImporte> lignes, String utilisateur) {
        String par = par(utilisateur);
        return uniteDeTravail.executer(() -> {
            Inventaire courant = inventaires.verrouiller(centre, id).orElseThrow(GestionDesInventaires::introuvable);
            courant.exigerEnCours();
            OffsetDateTime maintenant = horloge.maintenant();
            List<ResultatDImport.Anomalie> anomalies = new ArrayList<>();
            Map<UUID, Integer> dejaVues = new HashMap<>();
            int misesAJour = 0;
            int inchangees = 0;
            int vides = 0;
            for (ComptageImporte row : lignes) {
                if (row.quantite() == null) {
                    vides++;
                    continue;
                }
                List<LigneDInventaire> candidates = rapprocher(courant, row);
                if (candidates.isEmpty()) {
                    anomalies.add(new ResultatDImport.Anomalie(row.ligneFichier(), "Article " + nz(row.articleCode())
                            + (row.numeroLot() != null ? " / lot " + row.numeroLot() : "")
                            + " absent de l'inventaire : ajoutez le lot depuis l'écran de comptage."));
                    continue;
                }
                if (candidates.size() > 1) {
                    anomalies.add(new ResultatDImport.Anomalie(row.ligneFichier(), "Article " + nz(row.articleCode())
                            + " : plusieurs lots correspondent, précisez le n° de lot."));
                    continue;
                }
                LigneDInventaire ligne = candidates.getFirst();
                Integer premiere = dejaVues.putIfAbsent(ligne.id(), row.ligneFichier());
                if (premiere != null) {
                    anomalies.add(new ResultatDImport.Anomalie(row.ligneFichier(),
                            "Lot déjà renseigné à la ligne " + premiere + " du fichier : ligne ignorée."));
                    continue;
                }
                // PL7, KB-PL7-4 (corrigé le 2026-11-12) : un motif d'écart sur un lot compté conforme est refusé (400).
                if (row.motif() != null && row.quantite().compareTo(ligne.quantiteTheorique()) == 0) {
                    throw new FeuilleInvalide("INVENTORY_SHEET_MOTIF_WITHOUT_GAP", "Ligne " + row.ligneFichier()
                            + " : un motif d'écart est renseigné alors que la quantité comptée est égale à la quantité théorique.");
                }
                // motif vide dans le fichier : le motif déjà saisi à l'écran est conservé
                String motif = row.motif() != null ? row.motif() : ligne.motifEcart();
                if (ligne.estComptee() && ligne.quantiteComptee().compareTo(row.quantite()) == 0
                        && Objects.equals(motif, ligne.motifEcart())) {
                    inchangees++;
                    continue;
                }
                try {
                    courant = courant.compter(ligne.id(), sansZerosInutiles(row.quantite()), motif, par, maintenant);
                    misesAJour++;
                } catch (RefusDuReferentiel refus) {
                    anomalies.add(new ResultatDImport.Anomalie(row.ligneFichier(), refus.getMessage()));
                }
            }
            if (misesAJour > 0) {
                inventaires.enregistrer(courant);
            }
            return new ResultatDImport(courant, misesAJour, inchangees, vides, anomalies);
        });
    }

    /**
     * Clôture : tout compté, tout écart motivé ; puis stock des lots, lots trouvés créés, mouvements figés, stock de départ, PMP
     * rejoué — dans une seule transaction.
     *
     * @throws RefusDuReferentiel {@code INVENTORY_NOT_COMPLETE}, {@code INVENTORY_GAP_WITHOUT_REASON},
     *                            {@code INVENTORY_LOT_NOT_FOUND} (un lot compté a disparu), {@code INVENTORY_NOT_IN_PROGRESS}
     */
    public Inventaire cloturer(CentreId centre, UUID id, String utilisateur) {
        String par = par(utilisateur);
        OffsetDateTime maintenant = horloge.maintenant();
        return uniteDeTravail.executer(() -> {
            inventaires.verrouillerLaPeriode(centre);
            Inventaire courant = inventaires.verrouiller(centre, id).orElseThrow(GestionDesInventaires::introuvable);
            Inventaire cloture = courant.cloturer(par, maintenant);
            ecritures.verrouiller(centre, cloture.articlesInventories());
            OffsetDateTime coupure = cloture.coupure();

            List<LigneDInventaire> lignes = new ArrayList<>();
            int nouveaux = 0;
            for (LigneDInventaire ligne : cloture.lignes()) {
                BigDecimal compte = ligne.quantiteComptee();
                if (ligne.lotId() != null) {
                    if (!stock.poserLeStockDuLot(centre, ligne.lotId(), compte)) {
                        throw new RefusDuReferentiel("INVENTORY_LOT_NOT_FOUND", "Lot introuvable : " + ligne.numeroLot());
                    }
                    lignes.add(ligne);
                } else if (compte.signum() > 0) {
                    String numero = ligne.numeroLot() != null ? ligne.numeroLot() : cloture.reference() + "-" + (++nouveaux);
                    UUID lot = stock.creerLeLot(centre, ligne.articleId(), numero, ligne.datePeremption(), compte, ligne.pmp(),
                            maintenant);
                    lignes.add(ligne.avecLot(lot, numero));
                } else {
                    lignes.add(ligne);
                }
            }
            Inventaire termine = cloture.avecLesLignes(lignes);

            stock.figerLesMouvements(centre, id, coupure, termine.articlesInventories());
            for (LigneDInventaire ligne : termine.lignes()) {
                if (ligne.lotId() != null && ligne.quantiteComptee().signum() > 0) {
                    stock.poserLeStockDeDepart(centre, ligne.articleId(), ligne.lotId(), ligne.quantiteComptee(), ligne.pmp(), par,
                            coupure);
                }
            }
            termine.articlesInventories().forEach(article -> rejeu.rejouer(centre, article));

            inventaires.enregistrer(termine);
            notifier(termine, par);
            return termine;
        });
    }

    public Inventaire annuler(CentreId centre, UUID id, String utilisateur) {
        String par = par(utilisateur);
        OffsetDateTime maintenant = horloge.maintenant();
        return uniteDeTravail.executer(() -> {
            inventaires.verrouillerLaPeriode(centre);
            Inventaire annule = inventaires.verrouiller(centre, id).orElseThrow(GestionDesInventaires::introuvable)
                    .annuler(par, maintenant);
            inventaires.enregistrer(annule);
            notifier(annule, par);
            return annule;
        });
    }

    private Inventaire modifier(CentreId centre, UUID id, java.util.function.UnaryOperator<Inventaire> changement) {
        return uniteDeTravail.executer(() -> {
            Inventaire modifie = changement.apply(inventaires.verrouiller(centre, id).orElseThrow(GestionDesInventaires::introuvable));
            inventaires.enregistrer(modifie);
            return modifie;
        });
    }

    private void notifier(Inventaire inventaire, String par) {
        notifications.inventaireModifie(inventaire.centre().valeur(), inventaire.statut().name(), inventaire.reference(),
                inventaire.dateInventaire(), par);
    }

    /**
     * @param enCours l'inventaire en cours (bandeau « mouvements gelés »), vide s'il n'y en a pas
     */
    public record Etat(Optional<Inventaire> enCours, LocalDate derniereCloture) {
        public boolean mouvementsBloques() {
            return enCours.isPresent();
        }
    }
}






