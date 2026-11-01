package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Agr\u00e9gat {@code Serologie} — résultat sérologique d'un patient (VHB, VHC, VIH, syphilis, etc.).
 * <p>
 * Frontière transactionnelle : un résultat sérologique est un agrégat autonome rattaché à un patient
 * et à un centre. L'invariant métier est la détection sérologique positive : un résultat
 * {@code POSITIF} doit être signalé (événement {@code SerologiePositiveDetectee} construit par la
 * couche application après exécution de la commande).
 * <p>
 * L'agrégat ne génère jamais d'identifiant ni d'horloge lui-même : l'identifiant, l'instant et la
 * date de référence sont fournis par l'appelant (couche application), conformément à la
 * frontière hexagonale (règle ArchUnit {@code le_domaine_ne_tire_ni_identifiant_ni_heure}).
 * <p>
 * L'agrégat ne dépend d'aucun module de langage publié : l'événement
 * {@link com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SerologiePositiveDetectee}
 * est construit par la couche application après exécution des commandes.
 */
public final class Serologie {

    private final SerologieId id;
    private final UUID patientId;
    private final CentreId centreId;
    private final String prescripteurId;
    private final LocalDate datePrelevement;
    private final TypeSerologie typeSerologie;
    private final ResultatSerologie resultat;
    private final String valeur;
    private final String unite;
    private final Instant dateResultat;
    private final String laboratoire;
    private final String commentaire;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private Serologie(SerologieId id, UUID patientId, CentreId centreId, String prescripteurId,
                      LocalDate datePrelevement, TypeSerologie typeSerologie, ResultatSerologie resultat,
                      String valeur, String unite, Instant dateResultat, String laboratoire,
                      String commentaire, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centreId = centreId;
        this.prescripteurId = prescripteurId;
        this.datePrelevement = datePrelevement;
        this.typeSerologie = typeSerologie;
        this.resultat = resultat;
        this.valeur = valeur;
        this.unite = unite;
        this.dateResultat = dateResultat;
        this.laboratoire = laboratoire;
        this.commentaire = commentaire;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Crée un nouveau résultat sérologique.
     * <p>
     * Invariant : si le résultat est {@code POSITIF}, l'agrégat marque qu'un événement
     * {@code SerologiePositiveDetectee} doit être publié par la couche application.
     *
     * @param id              identifiant du résultat (généré par l'application)
     * @param patientId       identifiant du patient
     * @param centreId        centre de rattachement (isolation multi-centre)
     * @param prescripteurId  identifiant du prescripteur
     * @param datePrelevement date de prélèvement
     * @param typeSerologie   type de sérologie (VHB, VHC, VIH, SYPHILIS, ...)
     * @param resultat        résultat (POSITIF, NEGATIF, INDECIS, EN_ATTENTE)
     * @param valeur          valeur brute (optionnelle)
     * @param unite           unité de mesure (optionnelle)
     * @param dateResultat    date du résultat (optionnelle, si EN_ATTENTE alors null)
     * @param laboratoire     laboratoire d'analyse (optionnel)
     * @param commentaire     commentaire clinique (optionnel)
     * @param maintenant      instant de création (fourni par l'application)
     */
    public static Serologie enregistrer(SerologieId id, UUID patientId, CentreId centreId,
                                        String prescripteurId, LocalDate datePrelevement,
                                        TypeSerologie typeSerologie, ResultatSerologie resultat,
                                        String valeur, String unite, Instant dateResultat,
                                        String laboratoire, String commentaire,
                                        OffsetDateTime maintenant) {
        if (id == null) throw new IllegalArgumentException("SerologieId requis");
        if (patientId == null) throw new IllegalArgumentException("patientId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (typeSerologie == null) throw new IllegalArgumentException("typeSerologie requis");
        if (resultat == null) throw new IllegalArgumentException("resultat requis");
        if (datePrelevement == null) throw new IllegalArgumentException("datePrelevement requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        if (resultat == ResultatSerologie.POSITIF && (dateResultat == null)) {
            throw new IllegalArgumentException("SEROLOGIE_POSITIF_REQUIRE_DATE_RESULTAT");
        }
        LocalDate aujourdhui = LocalDate.now(java.time.ZoneOffset.UTC);
        if (datePrelevement.isAfter(aujourdhui)) {
            throw new IllegalArgumentException("SEROLOGIE_DATE_PRELEVEMENT_FUTURE");
        }
        Serologie serologie = new Serologie(id, patientId, centreId, prescripteurId, datePrelevement,
                typeSerologie, resultat, valeur, unite, dateResultat, laboratoire, commentaire,
                maintenant, maintenant);
        return serologie;
    }

    /**
     * Reconstitue un résultat sérologique depuis la persistance.
     */
    public static Serologie reconstituer(SerologieId id, UUID patientId, CentreId centreId,
                                         String prescripteurId, LocalDate datePrelevement,
                                         TypeSerologie typeSerologie, ResultatSerologie resultat,
                                         String valeur, String unite, Instant dateResultat,
                                         String laboratoire, String commentaire,
                                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Serologie(id, patientId, centreId, prescripteurId, datePrelevement, typeSerologie,
                resultat, valeur, unite, dateResultat, laboratoire, commentaire, createdAt, updatedAt);
    }

    /**
     * Indique si ce résultat est une détection sérologique positive.
     */
    public boolean estPositif() {
        return resultat == ResultatSerologie.POSITIF;
    }

    public SerologieId id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String prescripteurId() {
        return prescripteurId;
    }

    public LocalDate datePrelevement() {
        return datePrelevement;
    }

    public TypeSerologie typeSerologie() {
        return typeSerologie;
    }

    public ResultatSerologie resultat() {
        return resultat;
    }

    public String valeur() {
        return valeur;
    }

    public String unite() {
        return unite;
    }

    public Instant dateResultat() {
        return dateResultat;
    }

    public String laboratoire() {
        return laboratoire;
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
