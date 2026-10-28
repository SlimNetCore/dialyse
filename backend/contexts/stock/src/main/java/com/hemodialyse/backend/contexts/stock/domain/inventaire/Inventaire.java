package com.hemodialyse.backend.contexts.stock.domain.inventaire;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Inventaire de stock d'un centre à une date. À l'ouverture, le stock théorique de chaque lot est figé ; tant que l'inventaire est
 * {@code EN_COURS}, aucun mouvement du centre n'est permis (garde portée par {@link PeriodeDeStock}). À la clôture, chaque ligne
 * doit être comptée et tout écart justifié ; les quantités comptées deviennent le stock de départ. Seuls les articles qui ont une
 * ligne sont inventoriés : les autres (inactifs) ne sont ni figés ni rejoués (décision du 2026-10-24, KB-STK-50). Valeur immuable.
 */
public final class Inventaire {
    private final UUID id;
    private final CentreId centre;
    private final String reference;
    private final LocalDate dateInventaire;
    private final StatutDInventaire statut;
    private final String commentaire;
    private final String creePar;
    private final OffsetDateTime creeLe;
    private final String clotureePar;
    private final OffsetDateTime clotureeLe;
    private final List<LigneDInventaire> lignes;

    private Inventaire(UUID id, CentreId centre, String reference, LocalDate dateInventaire, StatutDInventaire statut,
                       String commentaire, String creePar, OffsetDateTime creeLe, String clotureePar, OffsetDateTime clotureeLe,
                       List<LigneDInventaire> lignes) {
        this.id = Objects.requireNonNull(id, "L'identifiant est obligatoire");
        this.centre = Objects.requireNonNull(centre, "Le centre est obligatoire");
        this.reference = Objects.requireNonNull(reference, "La référence est obligatoire");
        this.dateInventaire = Objects.requireNonNull(dateInventaire, "La date est obligatoire");
        this.statut = Objects.requireNonNull(statut, "Le statut est obligatoire");
        this.commentaire = commentaire;
        this.creePar = creePar;
        this.creeLe = creeLe;
        this.clotureePar = clotureePar;
        this.clotureeLe = clotureeLe;
        this.lignes = List.copyOf(lignes);
    }

    /**
     * @throws RefusDuReferentiel {@code INVENTORY_DATE_REQUIRED}, {@code INVENTORY_DATE_IN_FUTURE}
     */
    public static Inventaire ouvrir(UUID id, CentreId centre, String reference, LocalDate date, String commentaire, String par,
                                    OffsetDateTime maintenant, List<LigneDInventaire> theorique) {
        if (date == null) {
            throw new RefusDuReferentiel("INVENTORY_DATE_REQUIRED", "La date d'inventaire est obligatoire.");
        }
        if (date.isAfter(maintenant.toLocalDate())) {
            throw new RefusDuReferentiel("INVENTORY_DATE_IN_FUTURE", "La date d'inventaire ne peut pas être dans le futur.");
        }
        return new Inventaire(id, centre, reference, date, StatutDInventaire.EN_COURS,
                commentaire == null || commentaire.isBlank() ? null : commentaire.trim(), par, maintenant, null, null, theorique);
    }

    /**
     * Un inventaire tel qu'il est enregistré (aucun contrôle).
     */
    public static Inventaire reconstituer(UUID id, CentreId centre, String reference, LocalDate dateInventaire,
                                          StatutDInventaire statut, String commentaire, String creePar, OffsetDateTime creeLe,
                                          String clotureePar, OffsetDateTime clotureeLe, List<LigneDInventaire> lignes) {
        return new Inventaire(id, centre, reference, dateInventaire, statut, commentaire, creePar, creeLe, clotureePar, clotureeLe,
                lignes);
    }

    /**
     * Fin du jour d'inventaire : tout mouvement daté jusqu'à cet instant est clôturé.
     */
    public static OffsetDateTime coupure(LocalDate date) {
        return date.atTime(LocalTime.of(23, 59, 59)).atOffset(ZoneOffset.UTC);
    }

    private static String normalise(String numeroLot) {
        return numeroLot == null ? "" : numeroLot.trim().toUpperCase(Locale.ROOT);
    }

    private Inventaire avec(StatutDInventaire nouveauStatut, String parCloture, OffsetDateTime leCloture,
                            List<LigneDInventaire> nouvellesLignes) {
        return new Inventaire(id, centre, reference, dateInventaire, nouveauStatut, commentaire, creePar, creeLe, parCloture,
                leCloture, nouvellesLignes);
    }

    private Inventaire avecLignes(List<LigneDInventaire> nouvellesLignes) {
        return avec(statut, clotureePar, clotureeLe, nouvellesLignes);
    }

    public OffsetDateTime coupure() {
        return coupure(dateInventaire);
    }

    /**
     * @throws RefusDuReferentiel {@code INVENTORY_NOT_IN_PROGRESS}
     */
    public void exigerEnCours() {
        if (statut != StatutDInventaire.EN_COURS) {
            throw new RefusDuReferentiel("INVENTORY_NOT_IN_PROGRESS", "L'inventaire " + reference + " est "
                    + (statut == StatutDInventaire.CLOTURE ? "clôturé" : "annulé") + " : il n'est plus modifiable.");
        }
    }

    /**
     * @throws RefusDuReferentiel {@code INVENTORY_LINE_NOT_FOUND}
     */
    public LigneDInventaire ligne(UUID ligneId) {
        return lignes.stream().filter(l -> l.id().equals(ligneId)).findFirst()
                .orElseThrow(() -> new RefusDuReferentiel("INVENTORY_LINE_NOT_FOUND", "Ligne d'inventaire introuvable."));
    }

    public Inventaire compter(UUID ligneId, BigDecimal quantite, String motif, String par, OffsetDateTime le) {
        exigerEnCours();
        LigneDInventaire comptee = ligne(ligneId).compter(quantite, motif, par, le);
        return avecLignes(lignes.stream().map(l -> l.id().equals(ligneId) ? comptee : l).toList());
    }

    /**
     * Lot trouvé physiquement mais absent du stock théorique (théorique = 0), compté dans la foulée.
     *
     * @throws RefusDuReferentiel {@code INVENTORY_LINE_DUPLICATE} si l'article a déjà une ligne pour ce lot
     */
    public Inventaire ajouterLigne(LigneDInventaire ligne, BigDecimal quantite, String motif, String par, OffsetDateTime le) {
        exigerEnCours();
        String numero = normalise(ligne.numeroLot());
        boolean doublon = lignes.stream().anyMatch(l -> l.articleId().equals(ligne.articleId()) && numero.equals(normalise(l.numeroLot())));
        if (doublon) {
            throw new RefusDuReferentiel("INVENTORY_LINE_DUPLICATE",
                    "Ce lot figure déjà dans l'inventaire : saisissez son comptage sur la ligne existante.");
        }
        List<LigneDInventaire> nouvelles = new ArrayList<>(lignes);
        nouvelles.add(ligne.commeAjoutee().compter(quantite, motif, par, le));
        return avecLignes(nouvelles);
    }

    /**
     * @throws RefusDuReferentiel {@code INVENTORY_LINE_NOT_REMOVABLE} pour une ligne du théorique (comptez 0 pour un lot absent)
     */
    public Inventaire retirerLigne(UUID ligneId) {
        exigerEnCours();
        LigneDInventaire ligne = ligne(ligneId);
        if (!ligne.ajoutee()) {
            throw new RefusDuReferentiel("INVENTORY_LINE_NOT_REMOVABLE",
                    "Seules les lignes ajoutées pendant le comptage peuvent être retirées (comptez 0 pour un lot absent).");
        }
        return avecLignes(lignes.stream().filter(l -> !l.id().equals(ligneId)).toList());
    }

    /**
     * Reporte la quantité théorique sur les lignes non encore comptées.
     */
    public Inventaire reporterLeTheorique(String par, OffsetDateTime le) {
        exigerEnCours();
        return avecLignes(lignes.stream().map(l -> l.estComptee() ? l : l.compter(l.quantiteTheorique(), null, par, le)).toList());
    }

    public long lignesNonComptees() {
        return lignes.stream().filter(l -> !l.estComptee()).count();
    }

    public long ecartsSansMotif() {
        return lignes.stream().filter(l -> l.aUnEcart() && l.motifEcart() == null).count();
    }

    /**
     * @throws RefusDuReferentiel {@code INVENTORY_NOT_COMPLETE} (ligne non comptée), {@code INVENTORY_GAP_WITHOUT_REASON}
     */
    public Inventaire cloturer(String par, OffsetDateTime le) {
        exigerEnCours();
        long nonComptees = lignesNonComptees();
        if (nonComptees > 0) {
            throw new RefusDuReferentiel("INVENTORY_NOT_COMPLETE", nonComptees
                    + " ligne(s) ne sont pas encore comptées : comptez-les (0 si le lot est absent) avant de clôturer.");
        }
        long sansMotif = ecartsSansMotif();
        if (sansMotif > 0) {
            throw new RefusDuReferentiel("INVENTORY_GAP_WITHOUT_REASON",
                    sansMotif + " écart(s) sans motif : justifiez chaque écart avant de clôturer.");
        }
        return avec(StatutDInventaire.CLOTURE, par, le, lignes);
    }

    public Inventaire annuler(String par, OffsetDateTime le) {
        exigerEnCours();
        return avec(StatutDInventaire.ANNULE, par, le, lignes);
    }

    /**
     * Après la clôture : les lignes rattachées aux lots créés pour les lots trouvés en rayon.
     */
    public Inventaire avecLesLignes(List<LigneDInventaire> nouvellesLignes) {
        return avecLignes(nouvellesLignes);
    }

    /**
     * Les articles inventoriés (qui ont au moins une ligne) : seuls leurs mouvements sont figés et seul leur PMP est rejoué.
     */
    public Set<UUID> articlesInventories() {
        Set<UUID> articles = new LinkedHashSet<>();
        lignes.forEach(l -> articles.add(l.articleId()));
        return articles;
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public String reference() {
        return reference;
    }

    public LocalDate dateInventaire() {
        return dateInventaire;
    }

    public StatutDInventaire statut() {
        return statut;
    }

    public String commentaire() {
        return commentaire;
    }

    public String creePar() {
        return creePar;
    }

    public OffsetDateTime creeLe() {
        return creeLe;
    }

    public String clotureePar() {
        return clotureePar;
    }

    public OffsetDateTime clotureeLe() {
        return clotureeLe;
    }

    public List<LigneDInventaire> lignes() {
        return lignes;
    }
}

