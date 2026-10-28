package com.hemodialyse.backend.contexts.stock.application.consommations;

import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationDeSeance;
import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationDeSeance.LotDeLaPiece;
import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationsImputees;
import com.hemodialyse.backend.contexts.stock.domain.consommations.PerimetreDesSeances;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock.LotDuStock;
import com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage des consommations de séance (ADR 0045, T37 à T39, T44) : imputer, fixer, annuler, favoris. La consommation est imputée
 * sur la sortie pour séances du jour et du périmètre de la séance (lots de la pièce en FEFO) ; aucun mouvement de stock ; un
 * dépassement, un article hors pièce ou l'absence de pièce sont acceptés avec alerte. Refus si la séance est facturée ou la pièce
 * rapprochée. La pièce est verrouillée pendant l'imputation : une validation de rapprochement concurrente attend, puis la
 * consommation suivante est refusée.
 */
public final class GestionDesConsommations {

    private final ConsommationsImputees imputees;
    private final SortiesPourSeances pieces;
    private final SortiesDuStock stock;
    private final Articles articles;
    private final PerimetreDesSeances perimetres;
    private final StatutDesSeances statuts;
    private final UniteDeTravail uniteDeTravail;
    private final Supplier<UUID> identifiants;

    public GestionDesConsommations(ConsommationsImputees imputees, SortiesPourSeances pieces, SortiesDuStock stock, Articles articles,
                                   PerimetreDesSeances perimetres, StatutDesSeances statuts, UniteDeTravail uniteDeTravail,
                                   Supplier<UUID> identifiants) {
        this.imputees = imputees;
        this.pieces = pieces;
        this.stock = stock;
        this.articles = articles;
        this.perimetres = perimetres;
        this.statuts = statuts;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
    }

    private static ConsommationRefusee pieceRapprochee() {
        return new ConsommationRefusee("PIECE_DEJA_RAPPROCHEE", "La sortie pour séances de cette séance est déjà rapprochée");
    }

    // ------------------------------------------------------------------ écritures

    /**
     * Ajoute {@code quantite} à la consommation de l'article.
     *
     * @throws ConsommationRefusee {@code QUANTITE_INVALIDE}, {@code ARTICLE_INTROUVABLE}, {@code ARTICLE_INACTIF},
     *                             {@code SEANCE_FACTUREE_NON_MODIFIABLE}, {@code PIECE_DEJA_RAPPROCHEE}
     */
    public void ajouter(CentreId centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite) {
        if (quantite == null || quantite.signum() <= 0) {
            throw new ConsommationRefusee("QUANTITE_INVALIDE", "La quantité doit être strictement positive");
        }
        uniteDeTravail.executer(() -> {
            modifier(centre, seance, patient, jour, article, quantite, true);
            return null;
        });
    }

    /**
     * Fixe la quantité totale consommée de l'article ({@code 0} le retire).
     */
    public void fixer(CentreId centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite) {
        if (quantite == null || quantite.signum() < 0) {
            throw new ConsommationRefusee("QUANTITE_INVALIDE", "La quantité ne peut pas être négative");
        }
        uniteDeTravail.executer(() -> {
            modifier(centre, seance, patient, jour, article, quantite, false);
            return null;
        });
    }

    /**
     * Annule les consommations de la séance (sans effet s'il n'y en a pas).
     */
    public void annuler(CentreId centre, UUID seance) {
        uniteDeTravail.executer(() -> {
            exigerNonFacturee(centre, seance);
            Optional<ConsommationDeSeance> existante = imputees.parSeance(centre, seance);
            if (existante.isEmpty()) {
                return null;
            }
            if (existante.get().sortieId() != null) {
                SortiePourSeances piece = pieces.verrouiller(centre, existante.get().sortieId()).orElse(null);
                if (piece != null && piece.estRapprochee()) {
                    throw pieceRapprochee();
                }
            }
            imputees.supprimer(centre, seance);
            return null;
        });
    }

    private void exigerNonFacturee(CentreId centre, UUID seance) {
        if (statuts.estFacturee(centre, seance)) {
            throw new ConsommationRefusee("SEANCE_FACTUREE_NON_MODIFIABLE", "La seance facturee ne peut plus etre modifiee");
        }
    }

    private void modifier(CentreId centre, UUID seance, UUID patient, LocalDate jour, UUID article, BigDecimal quantite, boolean ajout) {
        Objects.requireNonNull(seance, "La séance est obligatoire");
        exigerNonFacturee(centre, seance);
        Article fiche = articles.parId(centre, article).orElseThrow(
                () -> new ConsommationRefusee("ARTICLE_INTROUVABLE", "Article introuvable: " + article));
        ConsommationDeSeance existante = imputees.parSeance(centre, seance)
                .orElseGet(() -> ConsommationDeSeance.vide(seance, centre, patient)).avecPatient(patient);
        BigDecimal courant = existante.totalDe(article);
        BigDecimal cible = ajout ? courant.add(quantite) : quantite;
        if (cible.compareTo(courant) == 0) {
            return;
        }
        if (cible.compareTo(courant) > 0 && !fiche.actif()) {
            throw new ConsommationRefusee("ARTICLE_INACTIF", "Article inactif: " + fiche.fiche().code());
        }
        SortiePourSeances piece = pieceRetenue(centre, seance, jour, existante);
        if (piece != null && piece.estRapprochee()) {
            throw pieceRapprochee();
        }
        ConsommationDeSeance resultat = existante;
        UUID sortie = piece != null ? piece.id() : null;
        if (existante.sortieId() == null && sortie != null) {
            // première pièce rencontrée pour cette séance : les consommations déjà relevées s'y rattachent
            for (Map.Entry<UUID, BigDecimal> total : existante.totaux().entrySet()) {
                if (!total.getKey().equals(article)) {
                    resultat = imputer(resultat, centre, seance, piece, total.getKey(), total.getValue());
                }
            }
        }
        resultat = imputer(resultat, centre, seance, piece, article, cible);
        if (resultat.imputations().isEmpty()) {
            imputees.supprimer(centre, seance);
        } else {
            imputees.enregistrer(resultat);
        }
    }

    private ConsommationDeSeance imputer(ConsommationDeSeance consommation, CentreId centre, UUID seance, SortiePourSeances piece,
                                         UUID article, BigDecimal quantite) {
        if (piece == null) {
            return consommation.fixer(article, quantite, null, List.of(), ConsommationDeSeance.AUCUNE_PIECE, identifiants);
        }
        Map<UUID, BigDecimal> sorties = new LinkedHashMap<>();
        for (LigneDePiece ligne : piece.lignes()) {
            if (ligne.lotId() != null && ligne.articleId().equals(article)) {
                sorties.merge(ligne.lotId(), ligne.quantiteSortie(), BigDecimal::add);
            }
        }
        Map<UUID, BigDecimal> prises = imputees.totauxParLotSaufSeance(centre, piece.id(), seance);
        List<LotDeLaPiece> lots = new ArrayList<>();
        sorties.forEach((lot, sortie) -> lots.add(new LotDeLaPiece(lot, sortie.subtract(prises.getOrDefault(lot, BigDecimal.ZERO)).max(
                BigDecimal.ZERO))));
        lots.sort(Comparator.comparing((LotDeLaPiece l) -> stock.lot(centre, l.lotId()).map(LotDuStock::datePeremption).orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder())));
        String alerte = sorties.isEmpty() ? ConsommationDeSeance.HORS_PIECE : ConsommationDeSeance.DEPASSEMENT;
        return consommation.fixer(article, quantite, piece.id(), lots, alerte, identifiants);
    }

    /**
     * La pièce de la séance : celle déjà retenue, sinon la plus récente du jour au périmètre le plus précis (salle et demi-journée,
     * puis salle, puis demi-journée, puis « journée »). Elle est verrouillée.
     */
    private SortiePourSeances pieceRetenue(CentreId centre, UUID seance, LocalDate jour, ConsommationDeSeance existante) {
        if (existante.sortieId() != null) {
            return pieces.verrouiller(centre, existante.sortieId()).orElse(null);
        }
        if (jour == null) {
            return null;
        }
        PerimetreDeSortie voulu = perimetres.perimetreDe(centre, seance);
        List<SortiePourSeances> duJour = pieces.duJour(centre, jour);
        List<java.util.function.Predicate<PerimetreDeSortie>> niveaux = List.of(
                p -> Objects.equals(p.salle(), voulu.salle()) && Objects.equals(p.demiJournee(), voulu.demiJournee()),
                p -> voulu.salle() != null && Objects.equals(p.salle(), voulu.salle()) && p.demiJournee() == null,
                p -> p.salle() == null && voulu.demiJournee() != null && p.demiJournee() == voulu.demiJournee(),
                p -> p.salle() == null && p.demiJournee() == null);
        for (var niveau : niveaux) {
            Optional<SortiePourSeances> trouvee = duJour.stream().filter(p -> niveau.test(p.perimetre())).findFirst();
            if (trouvee.isPresent()) {
                return pieces.verrouiller(centre, trouvee.get().id()).orElse(null);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ lectures

    /**
     * La consommation de la séance (imputations et alertes), vide si rien n'est relevé.
     */
    public ConsommationDeSeance consulter(CentreId centre, UUID seance) {
        return imputees.parSeance(centre, seance).orElseGet(() -> ConsommationDeSeance.vide(seance, centre, null));
    }

    /**
     * Articles favoris de l'infirmier : ceux de la sortie pour séances du jour au périmètre indiqué, avec ce qu'il en reste.
     */
    public List<Favori> favoris(CentreId centre, LocalDate jour, PerimetreDeSortie perimetre) {
        List<SortiePourSeances> duJour = pieces.duJour(centre, jour);
        Optional<SortiePourSeances> piece = duJour.stream().filter(p -> !p.estRapprochee()
                && Objects.equals(p.perimetre().salle(), perimetre.salle()) && Objects.equals(p.perimetre().demiJournee(),
                perimetre.demiJournee())).findFirst();
        if (piece.isEmpty()) {
            piece = duJour.stream().filter(p -> !p.estRapprochee() && p.perimetre().salle() == null
                    && p.perimetre().demiJournee() == null).findFirst();
        }
        if (piece.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> sorties = new LinkedHashMap<>();
        Map<UUID, BigDecimal> consommees = new LinkedHashMap<>();
        UUID id = piece.get().id();
        for (LigneDePiece ligne : piece.get().lignes()) {
            if (ligne.lotId() != null) {
                sorties.merge(ligne.articleId(), ligne.quantiteSortie(), BigDecimal::add);
            }
        }
        Map<UUID, BigDecimal> parLot = imputees.totauxParLotSaufSeance(centre, id, new UUID(0, 0));
        for (LigneDePiece ligne : piece.get().lignes()) {
            if (ligne.lotId() != null) {
                consommees.merge(ligne.articleId(), parLot.getOrDefault(ligne.lotId(), BigDecimal.ZERO), BigDecimal::add);
            }
        }
        List<Favori> favoris = new ArrayList<>();
        sorties.forEach((article, sortie) -> favoris.add(new Favori(article, sortie, sortie.subtract(
                consommees.getOrDefault(article, BigDecimal.ZERO)).max(BigDecimal.ZERO))));
        return favoris;
    }

    /**
     * Articles favoris d'une séance : le périmètre (salle, demi-journée) est celui de la séance, lu par l'ACL.
     */
    public List<Favori> favorisDeLaSeance(CentreId centre, UUID seance, LocalDate jour) {
        return favoris(centre, jour, perimetres.perimetreDe(centre, seance));
    }

    /**
     * Article de la pièce du périmètre, avec la quantité sortie et ce qu'il en reste.
     */
    public record Favori(UUID articleId, BigDecimal quantiteSortie, BigDecimal reste) {
    }
}


