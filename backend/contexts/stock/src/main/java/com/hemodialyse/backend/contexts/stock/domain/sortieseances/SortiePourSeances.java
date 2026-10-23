package com.hemodialyse.backend.contexts.stock.domain.sortieseances;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sortie pour séances (ADR 0045) : pièce préparée par le gestionnaire de stock pour un jour et un périmètre ; le stock sort à
 * l'enregistrement (FEFO, PMP courant). Les infirmiers consomment ensuite sur cette pièce sans mouvement de stock ; le
 * rapprochement constate l'écart de chaque ligne (consommé − sorti), exige un motif pour tout écart, puis fige la pièce. Valeur
 * immuable.
 */
public final class SortiePourSeances {
    private final UUID id;
    private final CentreId centre;
    private final String reference;
    private final LocalDate jour;
    private final PerimetreDeSortie perimetre;
    private final Statut statut;
    private final OffsetDateTime enregistreeLe;
    private final String enregistreePar;
    private final OffsetDateTime valideeLe;
    private final String valideePar;
    private final List<LigneDePiece> lignes;

    private SortiePourSeances(UUID id, CentreId centre, String reference, LocalDate jour, PerimetreDeSortie perimetre, Statut statut,
                              OffsetDateTime enregistreeLe, String enregistreePar, OffsetDateTime valideeLe, String valideePar,
                              List<LigneDePiece> lignes) {
        this.id = Objects.requireNonNull(id, "L'identifiant est obligatoire");
        this.centre = Objects.requireNonNull(centre, "Le centre est obligatoire");
        this.reference = Objects.requireNonNull(reference, "La référence est obligatoire");
        this.jour = Objects.requireNonNull(jour, "Le jour est obligatoire");
        this.perimetre = Objects.requireNonNull(perimetre, "Le périmètre est obligatoire");
        this.statut = Objects.requireNonNull(statut, "Le statut est obligatoire");
        this.enregistreeLe = enregistreeLe;
        this.enregistreePar = enregistreePar;
        this.valideeLe = valideeLe;
        this.valideePar = valideePar;
        this.lignes = List.copyOf(lignes);
    }

    /**
     * @throws RefusDuReferentiel {@code PIECE_SANS_LIGNE} sans ligne
     */
    public static SortiePourSeances enregistrer(UUID id, CentreId centre, String reference, LocalDate jour, PerimetreDeSortie perimetre,
                                                OffsetDateTime enregistreeLe, String enregistreePar, List<LigneDePiece> lignes) {
        if (lignes.isEmpty()) {
            throw new RefusDuReferentiel("PIECE_SANS_LIGNE", "Une sortie pour séances doit contenir au moins une ligne");
        }
        return new SortiePourSeances(id, centre, reference, jour, perimetre, Statut.ENREGISTREE, enregistreeLe, enregistreePar, null,
                null, lignes);
    }

    /**
     * Une pièce telle qu'elle est enregistrée (aucun contrôle).
     */
    public static SortiePourSeances reconstituer(UUID id, CentreId centre, String reference, LocalDate jour, PerimetreDeSortie perimetre,
                                                 Statut statut, OffsetDateTime enregistreeLe, String enregistreePar,
                                                 OffsetDateTime valideeLe, String valideePar, List<LigneDePiece> lignes) {
        return new SortiePourSeances(id, centre, reference, jour, perimetre, statut, enregistreeLe, enregistreePar, valideeLe, valideePar,
                lignes);
    }

    public boolean estRapprochee() {
        return statut == Statut.RAPPROCHEE;
    }

    /**
     * @throws RefusDuReferentiel {@code PIECE_DEJA_RAPPROCHEE} : une pièce rapprochée est figée
     */
    public void exigerNonRapprochee() {
        if (estRapprochee()) {
            throw new RefusDuReferentiel("PIECE_DEJA_RAPPROCHEE", "Cette sortie pour séances est déjà rapprochée");
        }
    }

    /**
     * Constate les consommations : pour chaque ligne de lot, consommé = total imputé sur le lot, écart = consommé − sorti ; l'excédent
     * de chaque article (imputé hors des lots de la pièce) devient une ligne sans lot, écart « en plus ». Les motifs déjà saisis sont
     * conservés tant que l'écart subsiste.
     *
     * @param parLot   total imputé par lot de la pièce
     * @param horsLots total imputé par article au-delà des lots de la pièce (ou hors pièce)
     */
    public SortiePourSeances constater(Map<UUID, BigDecimal> parLot, Map<UUID, BigDecimal> horsLots, Supplier<UUID> identifiants) {
        exigerNonRapprochee();
        List<LigneDePiece> nouvelles = new ArrayList<>();
        Map<UUID, String> motifsDesExcedents = new HashMap<>();
        for (LigneDePiece ligne : lignes) {
            if (ligne.lotId() == null) {
                motifsDesExcedents.put(ligne.articleId(), ligne.motif());
                continue;
            }
            BigDecimal consomme = parLot.getOrDefault(ligne.lotId(), BigDecimal.ZERO);
            BigDecimal ecart = consomme.subtract(ligne.quantiteSortie());
            nouvelles.add(new LigneDePiece(ligne.id(), ligne.articleId(), ligne.lotId(), ligne.quantiteSortie(), ligne.valeurUnitaire(),
                    consomme, ecart, ecart.signum() == 0 ? null : ligne.motif()));
        }
        horsLots.forEach((article, quantite) -> {
            if (quantite.signum() > 0) {
                nouvelles.add(new LigneDePiece(identifiants.get(), article, null, BigDecimal.ZERO, BigDecimal.ZERO, quantite, quantite,
                        motifsDesExcedents.get(article)));
            }
        });
        return new SortiePourSeances(id, centre, reference, jour, perimetre, statut, enregistreeLe, enregistreePar, valideeLe,
                valideePar, nouvelles);
    }

    /**
     * @throws RefusDuReferentiel {@code PIECE_LIGNE_INCONNUE}, {@code ECART_SANS_MOTIF} (motif vide), {@code PIECE_DEJA_RAPPROCHEE}
     */
    public SortiePourSeances justifier(UUID ligneId, String motif) {
        exigerNonRapprochee();
        if (motif == null || motif.isBlank()) {
            throw new RefusDuReferentiel("ECART_SANS_MOTIF", "Le motif de l'écart est obligatoire");
        }
        if (lignes.stream().noneMatch(l -> l.id().equals(ligneId))) {
            throw new RefusDuReferentiel("PIECE_LIGNE_INCONNUE", "Ligne de sortie pour séances introuvable");
        }
        List<LigneDePiece> nouvelles = lignes.stream().map(l -> l.id().equals(ligneId) ? l.avecMotif(motif.trim()) : l).toList();
        return new SortiePourSeances(id, centre, reference, jour, perimetre, statut, enregistreeLe, enregistreePar, valideeLe,
                valideePar, nouvelles);
    }

    /**
     * @throws RefusDuReferentiel {@code ECART_SANS_MOTIF} si un écart n'a pas de motif, {@code PIECE_DEJA_RAPPROCHEE}
     */
    public void exigerRapprochable() {
        exigerNonRapprochee();
        if (lignes.stream().anyMatch(l -> l.aUnEcart() && (l.motif() == null || l.motif().isBlank()))) {
            throw new RefusDuReferentiel("ECART_SANS_MOTIF", "Tout écart doit être justifié par un motif avant la validation");
        }
    }

    public SortiePourSeances rapprocher(String validateur, OffsetDateTime le) {
        exigerRapprochable();
        return new SortiePourSeances(id, centre, reference, jour, perimetre, Statut.RAPPROCHEE, enregistreeLe, enregistreePar, le,
                validateur, lignes);
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

    public LocalDate jour() {
        return jour;
    }

    public PerimetreDeSortie perimetre() {
        return perimetre;
    }

    public Statut statut() {
        return statut;
    }

    public OffsetDateTime enregistreeLe() {
        return enregistreeLe;
    }

    public String enregistreePar() {
        return enregistreePar;
    }

    public OffsetDateTime valideeLe() {
        return valideeLe;
    }

    public String valideePar() {
        return valideePar;
    }

    public List<LigneDePiece> lignes() {
        return lignes;
    }

    public enum Statut {
        ENREGISTREE, RAPPROCHEE
    }
}