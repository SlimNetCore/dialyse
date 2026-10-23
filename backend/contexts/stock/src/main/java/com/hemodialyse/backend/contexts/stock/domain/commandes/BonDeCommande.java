package com.hemodialyse.backend.contexts.stock.domain.commandes;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bon de commande d'un centre (RG-STK-005, 010 à 012) : référence séquentielle {@code BC-nnnnn}, fournisseur facultatif et
 * lignes. Les lignes ne se remplacent qu'en brouillon (KB-STK-18 corrigé) ; la validation exige au moins une ligne. Valeur
 * immuable : toute modification donne un nouveau bon.
 */
public final class BonDeCommande {

    private final UUID id;
    private final CentreId centre;
    private final String reference;
    private final UUID fournisseurId;
    private final StatutDuBon statut;
    private final String creePar;
    private final OffsetDateTime creeLe;
    private final List<LigneDeBon> lignes;

    private BonDeCommande(UUID id, CentreId centre, String reference, UUID fournisseurId, StatutDuBon statut, String creePar,
                          OffsetDateTime creeLe, List<LigneDeBon> lignes) {
        this.id = Objects.requireNonNull(id, "L'identifiant est obligatoire");
        this.centre = Objects.requireNonNull(centre, "Le centre est obligatoire");
        this.reference = Objects.requireNonNull(reference, "La référence est obligatoire");
        this.fournisseurId = fournisseurId;
        this.statut = Objects.requireNonNull(statut, "Le statut est obligatoire");
        this.creePar = creePar;
        this.creeLe = creeLe;
        this.lignes = List.copyOf(lignes);
    }

    public static BonDeCommande brouillon(UUID id, CentreId centre, String reference, UUID fournisseurId, String creePar,
                                          OffsetDateTime creeLe, List<LigneDeBon> lignes) {
        return new BonDeCommande(id, centre, reference, fournisseurId, StatutDuBon.BROUILLON, creePar, creeLe, lignes);
    }

    /**
     * Un bon tel qu'il est enregistré (aucun contrôle).
     */
    public static BonDeCommande reconstituer(UUID id, CentreId centre, String reference, UUID fournisseurId, StatutDuBon statut,
                                             String creePar, OffsetDateTime creeLe, List<LigneDeBon> lignes) {
        return new BonDeCommande(id, centre, reference, fournisseurId, statut, creePar, creeLe, lignes);
    }

    /**
     * @throws RefusDuReferentiel {@code BON_COMMANDE_NON_MODIFIABLE} si le bon n'est plus un brouillon
     */
    public BonDeCommande remplacerLesLignes(List<LigneDeBon> nouvelles) {
        if (statut != StatutDuBon.BROUILLON) {
            throw new RefusDuReferentiel("BON_COMMANDE_NON_MODIFIABLE", "Les lignes d'un bon ne se modifient qu'en brouillon");
        }
        return new BonDeCommande(id, centre, reference, fournisseurId, statut, creePar, creeLe, nouvelles);
    }

    /**
     * @throws RefusDuReferentiel {@code BON_COMMANDE_NON_VALIDABLE} hors brouillon, {@code BON_COMMANDE_SANS_LIGNE} sans ligne
     */
    public BonDeCommande valider() {
        if (statut != StatutDuBon.BROUILLON) {
            throw new RefusDuReferentiel("BON_COMMANDE_NON_VALIDABLE", "Seul un bon BROUILLON peut etre valide");
        }
        if (lignes.isEmpty()) {
            throw new RefusDuReferentiel("BON_COMMANDE_SANS_LIGNE", "Un bon de commande doit contenir au moins une ligne");
        }
        return new BonDeCommande(id, centre, reference, fournisseurId, StatutDuBon.VALIDE, creePar, creeLe, lignes);
    }

    /**
     * @throws RefusDuReferentiel {@code BON_COMMANDE_NON_VALIDE} si le bon est encore un brouillon (KB-STK-41 corrigé)
     */
    public BonDeCommande marquerRecu() {
        if (statut == StatutDuBon.BROUILLON) {
            throw new RefusDuReferentiel("BON_COMMANDE_NON_VALIDE", "Un bon de commande doit être validé avant d'être réceptionné");
        }
        return new BonDeCommande(id, centre, reference, fournisseurId, StatutDuBon.RECU, creePar, creeLe, lignes);
    }

    public BigDecimal total() {
        return lignes.stream().map(LigneDeBon::sousTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    public UUID fournisseurId() {
        return fournisseurId;
    }

    public StatutDuBon statut() {
        return statut;
    }

    public String creePar() {
        return creePar;
    }

    public OffsetDateTime creeLe() {
        return creeLe;
    }

    public List<LigneDeBon> lignes() {
        return lignes;
    }
}

