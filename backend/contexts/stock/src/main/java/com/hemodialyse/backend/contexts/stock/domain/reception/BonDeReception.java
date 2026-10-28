package com.hemodialyse.backend.contexts.stock.domain.reception;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bon de réception d'un centre (RG-STK-020 à 025) : référence séquentielle {@code BR-nnnnn}, fournisseur et bon de commande
 * facultatifs, date de réception, lignes. Brouillon (tout se modifie) puis {@code VALIDE} (lots créés, stock et PMP à jour ;
 * seules les quantités, prix, numéro et péremption se corrigent). Valeur immuable.
 */
public final class BonDeReception {

    private final UUID id;
    private final CentreId centre;
    private final String reference;
    private final UUID bonCommandeId;
    private final UUID fournisseurId;
    private final LocalDate dateReception;
    private final Statut statut;
    private final String creePar;
    private final OffsetDateTime creeLe;
    private final List<LigneDeReception> lignes;

    private BonDeReception(UUID id, CentreId centre, String reference, UUID bonCommandeId, UUID fournisseurId,
                           LocalDate dateReception, Statut statut, String creePar, OffsetDateTime creeLe,
                           List<LigneDeReception> lignes) {
        this.id = Objects.requireNonNull(id, "L'identifiant est obligatoire");
        this.centre = Objects.requireNonNull(centre, "Le centre est obligatoire");
        this.reference = Objects.requireNonNull(reference, "La référence est obligatoire");
        this.bonCommandeId = bonCommandeId;
        this.fournisseurId = fournisseurId;
        this.dateReception = Objects.requireNonNull(dateReception, "La date de réception est obligatoire");
        this.statut = Objects.requireNonNull(statut, "Le statut est obligatoire");
        this.creePar = creePar;
        this.creeLe = creeLe;
        this.lignes = List.copyOf(lignes);
    }

    public static BonDeReception brouillon(UUID id, CentreId centre, String reference, UUID bonCommandeId, UUID fournisseurId,
                                           LocalDate dateReception, String creePar, OffsetDateTime creeLe,
                                           List<LigneDeReception> lignes) {
        return new BonDeReception(id, centre, reference, bonCommandeId, fournisseurId, dateReception, Statut.BROUILLON, creePar,
                creeLe, lignes);
    }

    /**
     * Un bon tel qu'il est enregistré (aucun contrôle).
     */
    public static BonDeReception reconstituer(UUID id, CentreId centre, String reference, UUID bonCommandeId, UUID fournisseurId,
                                              LocalDate dateReception, Statut statut, String creePar, OffsetDateTime creeLe,
                                              List<LigneDeReception> lignes) {
        return new BonDeReception(id, centre, reference, bonCommandeId, fournisseurId, dateReception, statut, creePar, creeLe,
                lignes);
    }

    public boolean estValide() {
        return statut == Statut.VALIDE;
    }

    /**
     * Brouillon : le fournisseur et la date ne changent que s'ils sont fournis, les lignes sont remplacées.
     *
     * @throws RefusDuReferentiel {@code RECEPTION_DEJA_VALIDEE} si le bon est validé (voir {@link #corriger})
     */
    public BonDeReception modifierLeBrouillon(UUID nouveauFournisseur, LocalDate nouvelleDate, List<LigneDeReception> nouvelles) {
        if (estValide()) {
            throw new RefusDuReferentiel("RECEPTION_DEJA_VALIDEE", "Ce bon de réception est validé");
        }
        return new BonDeReception(id, centre, reference, bonCommandeId, nouveauFournisseur != null ? nouveauFournisseur : fournisseurId,
                nouvelleDate != null ? nouvelleDate : dateReception, statut, creePar, creeLe, nouvelles);
    }

    /**
     * Bon validé : fournisseur et date comme au brouillon, lignes remplacées si elles sont fournies (leur lot est conservé).
     */
    public BonDeReception corriger(UUID nouveauFournisseur, LocalDate nouvelleDate, List<LigneDeReception> nouvelles) {
        return new BonDeReception(id, centre, reference, bonCommandeId, nouveauFournisseur != null ? nouveauFournisseur : fournisseurId,
                nouvelleDate != null ? nouvelleDate : dateReception, statut, creePar, creeLe, nouvelles);
    }

    /**
     * @param lignesAvecLot les lignes, chacune rattachée au lot créé
     * @throws RefusDuReferentiel {@code RECEPTION_NON_VALIDABLE} hors brouillon, {@code RECEPTION_SANS_LIGNE} sans ligne
     */
    public BonDeReception valider(List<LigneDeReception> lignesAvecLot) {
        exigerValidable();
        return new BonDeReception(id, centre, reference, bonCommandeId, fournisseurId, dateReception, Statut.VALIDE, creePar,
                creeLe, lignesAvecLot);
    }

    public void exigerValidable() {
        if (estValide()) {
            throw new RefusDuReferentiel("RECEPTION_NON_VALIDABLE", "Seul un bon de reception BROUILLON peut etre valide");
        }
        if (lignes.isEmpty()) {
            throw new RefusDuReferentiel("RECEPTION_SANS_LIGNE", "Un bon de reception doit contenir au moins une ligne");
        }
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

    public UUID bonCommandeId() {
        return bonCommandeId;
    }

    public UUID fournisseurId() {
        return fournisseurId;
    }

    public LocalDate dateReception() {
        return dateReception;
    }

    public Statut statut() {
        return statut;
    }

    public String creePar() {
        return creePar;
    }

    public OffsetDateTime creeLe() {
        return creeLe;
    }

    public List<LigneDeReception> lignes() {
        return lignes;
    }

    public enum Statut {
        BROUILLON, VALIDE
    }
}

