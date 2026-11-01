package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Agrégat {@code BilanPreGreffe} — bilan d'éligibilité à la greffe d'un patient (slice DM7).
 * <p>
 * Agrégat isolé et extractible (ADR 0007) : cycle {@code EN_COURS → COMPLET}, {@code ANNULE}
 * seulement avant complétion. Invariants : au moins une étape au démarrage, une étape ne peut
 * être validée qu'une fois, la complétion exige toutes les étapes validées.
 * <p>
 * L'agrégat ne génère jamais d'identifiant ni d'horloge lui-même : l'identifiant et l'instant
 * sont fournis par l'appelant (couche application), conformément à la frontière hexagonale
 * (règle ArchUnit {@code le_domaine_ne_tire_ni_identifiant_ni_heure}).
 */
public final class BilanPreGreffe {

    private final BilanPreGreffeId id;
    private final UUID patientId;
    private final CentreId centreId;
    private final StatutBilan statut;
    private final List<EtapeBilan> etapes;
    private final LocalDate dateDemarrage;
    private final String commentaire;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private BilanPreGreffe(BilanPreGreffeId id, UUID patientId, CentreId centreId,
                           StatutBilan statut, List<EtapeBilan> etapes, LocalDate dateDemarrage,
                           String commentaire, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centreId = centreId;
        this.statut = statut;
        this.etapes = List.copyOf(etapes);
        this.dateDemarrage = dateDemarrage;
        this.commentaire = commentaire;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Démarre un nouveau bilan avec ses étapes à effectuer.
     */
    public static BilanPreGreffe demarrer(BilanPreGreffeId id, UUID patientId, CentreId centreId,
                                          List<EtapeBilan> etapes, LocalDate dateDemarrage,
                                          String commentaire, OffsetDateTime maintenant) {
        if (id == null) throw new IllegalArgumentException("BilanPreGreffeId requis");
        if (patientId == null) throw new IllegalArgumentException("patientId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (etapes == null || etapes.isEmpty()) throw new IllegalArgumentException("BILAN_ETAPES_REQUISES");
        if (dateDemarrage == null) throw new IllegalArgumentException("dateDemarrage requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        List<EtapeBilan> copie = etapes.stream().map(e -> EtapeBilan.aFaire(e.code(), e.libelle())).toList();
        return new BilanPreGreffe(id, patientId, centreId, StatutBilan.EN_COURS, copie,
                dateDemarrage, commentaire, maintenant, maintenant);
    }

    /**
     * Reconstitue un bilan depuis la persistance.
     */
    public static BilanPreGreffe reconstituer(BilanPreGreffeId id, UUID patientId, CentreId centreId,
                                              StatutBilan statut, List<EtapeBilan> etapes,
                                              LocalDate dateDemarrage, String commentaire,
                                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new BilanPreGreffe(id, patientId, centreId, statut, etapes,
                dateDemarrage, commentaire, createdAt, updatedAt);
    }

    /**
     * Valide une étape du bilan.
     */
    public BilanPreGreffe validerEtape(String codeEtape, String validePar, OffsetDateTime maintenant) {
        if (statut != StatutBilan.EN_COURS) throw new IllegalArgumentException("BILAN_NON_MODIFIABLE");
        if (codeEtape == null || codeEtape.isBlank()) throw new IllegalArgumentException("code etape requis");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        List<EtapeBilan> resultat = new ArrayList<>();
        boolean trouvee = false;
        for (EtapeBilan etape : etapes) {
            if (etape.code().equals(codeEtape)) {
                resultat.add(etape.valider(maintenant, validePar));
                trouvee = true;
            } else {
                resultat.add(etape);
            }
        }
        if (!trouvee) throw new IllegalArgumentException("BILAN_ETAPE_INCONNUE");
        return new BilanPreGreffe(id, patientId, centreId, statut, resultat,
                dateDemarrage, commentaire, createdAt, maintenant);
    }

    /**
     * Complète le bilan : toutes les étapes doivent être validées.
     */
    public BilanPreGreffe completer(OffsetDateTime maintenant) {
        if (statut != StatutBilan.EN_COURS) throw new IllegalArgumentException("BILAN_NON_MODIFIABLE");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        boolean toutesValidees = etapes.stream().allMatch(EtapeBilan::validee);
        if (!toutesValidees) throw new IllegalArgumentException("BILAN_ETAPES_RESTANTES");
        return new BilanPreGreffe(id, patientId, centreId, StatutBilan.COMPLET, etapes,
                dateDemarrage, commentaire, createdAt, maintenant);
    }

    /**
     * Annule le bilan (seulement s'il est en cours).
     */
    public BilanPreGreffe annuler(OffsetDateTime maintenant) {
        if (statut != StatutBilan.EN_COURS) throw new IllegalArgumentException("BILAN_NON_MODIFIABLE");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        return new BilanPreGreffe(id, patientId, centreId, StatutBilan.ANNULE, etapes,
                dateDemarrage, commentaire, createdAt, maintenant);
    }

    public boolean estComplet() {
        return statut == StatutBilan.COMPLET;
    }

    public BilanPreGreffeId id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public CentreId centreId() {
        return centreId;
    }

    public StatutBilan statut() {
        return statut;
    }

    public List<EtapeBilan> etapes() {
        return etapes;
    }

    public LocalDate dateDemarrage() {
        return dateDemarrage;
    }

    public String commentaire() {
        return commentaire;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    /**
     * Liste des identifiants de centres concernés par cet agrégat (ici un seul : centreId).
     * Utilisé pour le contrôle d'isolation multi-centre.
     */
    public List<CentreId> centresConcernees() {
        return Collections.singletonList(centreId);
    }
}
