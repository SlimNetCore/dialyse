package com.hemodialyse.backend.contexts.stock.domain.consommations;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Consommation de séance (ADR 0045, T37 à T39) : ce que l'infirmier a réellement consommé pour une séance, imputé sur les lots de la
 * sortie pour séances de son périmètre, sans aucun mouvement de stock. Une quantité au-delà du reste de la pièce, un article absent de
 * la pièce ou l'absence de pièce sont acceptés avec alerte (l'excédent sera un écart « en plus » au rapprochement). Valeur immuable.
 */
public final class ConsommationDeSeance {
    public static final String DEPASSEMENT = "DEPASSEMENT_DE_LA_SORTIE";
    public static final String HORS_PIECE = "ARTICLE_HORS_PIECE";
    public static final String AUCUNE_PIECE = "AUCUNE_PIECE";
    private final UUID seanceId;
    private final CentreId centre;
    private final UUID patientId;
    private final UUID sortieId;
    private final List<Imputation> imputations;

    private ConsommationDeSeance(UUID seanceId, CentreId centre, UUID patientId, UUID sortieId, List<Imputation> imputations) {
        this.seanceId = Objects.requireNonNull(seanceId, "La séance est obligatoire");
        this.centre = Objects.requireNonNull(centre, "Le centre est obligatoire");
        this.patientId = patientId;
        this.sortieId = sortieId;
        this.imputations = List.copyOf(imputations);
    }

    public static ConsommationDeSeance vide(UUID seanceId, CentreId centre, UUID patientId) {
        return new ConsommationDeSeance(seanceId, centre, patientId, null, List.of());
    }

    /**
     * Une consommation telle qu'elle est enregistrée (aucun contrôle).
     */
    public static ConsommationDeSeance reconstituer(UUID seanceId, CentreId centre, UUID patientId, UUID sortieId,
                                                    List<Imputation> imputations) {
        return new ConsommationDeSeance(seanceId, centre, patientId, sortieId, imputations);
    }

    /**
     * Quantité totale consommée par article.
     */
    public Map<UUID, BigDecimal> totaux() {
        Map<UUID, BigDecimal> totaux = new LinkedHashMap<>();
        imputations.forEach(i -> totaux.merge(i.articleId(), i.quantite(), BigDecimal::add));
        return totaux;
    }

    public BigDecimal totalDe(UUID article) {
        return totaux().getOrDefault(article, BigDecimal.ZERO);
    }

    /**
     * Fixe la quantité totale d'un article : ses anciennes imputations disparaissent, la nouvelle quantité est répartie sur les lots
     * de la pièce dans l'ordre donné (FEFO), chacun dans la limite de son reste ; l'excédent est imputé sans lot, avec l'alerte
     * {@code alerteDeLExces}. Une quantité nulle retire l'article. Les autres articles ne changent pas.
     *
     * @param lotsDeLaPiece lots de la pièce pour cet article, FEFO, avec leur reste disponible (hors cette séance)
     * @param sortie        pièce retenue pour cette consommation, {@code null} s'il n'y en a pas
     */
    public ConsommationDeSeance fixer(UUID article, BigDecimal quantite, UUID sortie, List<LotDeLaPiece> lotsDeLaPiece,
                                      String alerteDeLExces, Supplier<UUID> identifiants) {
        List<Imputation> nouvelles = new ArrayList<>(imputations.stream().filter(i -> !i.articleId().equals(article)).toList());
        BigDecimal restant = quantite;
        for (LotDeLaPiece lot : lotsDeLaPiece) {
            if (restant.signum() <= 0) {
                break;
            }
            BigDecimal prise = restant.min(lot.reste());
            if (prise.signum() > 0) {
                nouvelles.add(new Imputation(identifiants.get(), article, lot.lotId(), prise, null));
                restant = restant.subtract(prise);
            }
        }
        if (restant.signum() > 0) {
            nouvelles.add(new Imputation(identifiants.get(), article, null, restant, alerteDeLExces));
        }
        return new ConsommationDeSeance(seanceId, centre, patientId, sortie, nouvelles);
    }

    public ConsommationDeSeance avecPatient(UUID patient) {
        return new ConsommationDeSeance(seanceId, centre, patient != null ? patient : patientId, sortieId, imputations);
    }

    public UUID seanceId() {
        return seanceId;
    }

    public CentreId centre() {
        return centre;
    }

    public UUID patientId() {
        return patientId;
    }

    public UUID sortieId() {
        return sortieId;
    }

    public List<Imputation> imputations() {
        return imputations;
    }

    /**
     * Lot d'une sortie pour séances et ce qu'il en reste pour d'autres consommations.
     */
    public record LotDeLaPiece(UUID lotId, BigDecimal reste) {
    }
}