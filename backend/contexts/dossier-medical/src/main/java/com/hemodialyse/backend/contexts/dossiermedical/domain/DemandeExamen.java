package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Agr\u00e9gat {@code DemandeExamen} — demande d'examen m\u00e9dical (biologie, imagerie, fonctionnel, anapath)
 * de sa prescription \u00e0 la validation du r\u00e9sultat.
 * <p>
 * Fronti\u00e8re transactionnelle : la demande et ses lignes forment un seul agr\u00e8tat. Les r\u00e9sultats bruts
 * vivent dans l'agr\u00e9gat s\u00e9par\u00e9 des observations biologiques, reli\u00e9s par {@code demandeExamenId}.
 * <p>
 * Machine \u00e0 \u00e9tats : {@code DEMANDE \u2192 PRELEVE \u2192 RESULTAT_DISPONIBLE \u2192 VALIDE}, avec
 * {@code ANNULE} accessible uniquement avant {@code RESULTAT_DISPONIBLE}.
 * <p>
 * L'agr\u00e9gat ne g\u00e9n\u00e8re jamais d'identifiant ni d'horloge lui-m\u00eame : l'identifiant, l'instant et la
 * date de r\u00e9f\u00e9rence sont fournis par l'appelant (couche application), conform\u00e9ment \u00e0 la
 * fronti\u00e8re hexagonale (r\u00e8gle ArchUnit {@code le_domaine_ne_tire_ni_identifiant_ni_heure}).
 * <p>
 * L'agr\u00e9gat ne d\u00e9pend d'aucun module de langage publi\u00e9 : les \u00e9v\u00e9nements ({@link
 * com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.ResultatExamenRecu}) sont construits par la
 * couche application apr\u00e8s ex\u00e9cution des commandes.
 */
public final class DemandeExamen {

    private final DemandeExamenId id;
    private final UUID patientId;
    private final CentreId centreId;
    private final String prescripteurId;
    private final LocalDate dateDemande;
    private final CategorieExamen categorie;
    private final boolean urgent;
    private final String motif;
    private final List<LigneDemandeExamen> lignes;
    private final OffsetDateTime createdAt;
    private StatutDemandeExamen statut;
    private String conclusion;
    private OffsetDateTime updatedAt;

    private DemandeExamen(DemandeExamenId id, UUID patientId, CentreId centreId, String prescripteurId,
                          LocalDate dateDemande, CategorieExamen categorie, boolean urgent, String motif,
                          StatutDemandeExamen statut, String conclusion, List<LigneDemandeExamen> lignes,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centreId = centreId;
        this.prescripteurId = prescripteurId;
        this.dateDemande = dateDemande;
        this.categorie = categorie;
        this.urgent = urgent;
        this.motif = motif;
        this.statut = statut;
        this.conclusion = conclusion;
        this.lignes = new ArrayList<>(lignes);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Cr\u00e9e une nouvelle demande d'examen en statut {@code DEMANDE}.
     *
     * @param id             identifiant de la demande (g\u00e9n\u00e9r\u00e9 par l'application)
     * @param patientId      identifiant du patient
     * @param centreId       centre de rattachement (isolation multi-centre)
     * @param prescripteurId identifiant du prescripteur
     * @param dateDemande    date de la demande
     * @param aujourdhui     date du jour (fournie par l'application, pour validation de coh\u00e9rence)
     * @param categorie      cat\u00e9gorie d'examen (BIOLOGIE, IMAGERIE, FONCTIONNEL, ANAPATH)
     * @param urgent         caract\u00e8re urgent
     * @param motif          motif clinique de la demande
     * @param lignes         lignes d'examen (au moins une obligatoire)
     * @param maintenant     instant de cr\u00e9ation (fourni par l'application)
     */
    public static DemandeExamen creer(DemandeExamenId id, UUID patientId, CentreId centreId,
                                      String prescripteurId, LocalDate dateDemande, LocalDate aujourdhui,
                                      CategorieExamen categorie, boolean urgent, String motif,
                                      List<LigneDemandeExamen> lignes, OffsetDateTime maintenant) {
        if (id == null) throw new IllegalArgumentException("DemandeExamenId requis");
        if (patientId == null) throw new IllegalArgumentException("patientId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (aujourdhui == null) throw new IllegalArgumentException("aujourdhui requis");
        if (categorie == null) throw new IllegalArgumentException("categorie requise");
        if (lignes == null || lignes.isEmpty()) {
            throw new IllegalArgumentException("DEMANDE_EXAMEN_LIGNE_REQUISE");
        }
        if (dateDemande == null) throw new IllegalArgumentException("dateDemande requise");
        if (dateDemande.isAfter(aujourdhui)) {
            throw new IllegalArgumentException("DEMANDE_EXAMEN_DATE_FUTURE");
        }
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        DemandeExamen demande = new DemandeExamen(id, patientId, centreId, prescripteurId, dateDemande,
                categorie, urgent, motif, StatutDemandeExamen.DEMANDE, null, lignes, maintenant, maintenant);
        return demande;
    }

    /**
     * Reconstitue une demande d'examen depuis la persistance.
     */
    public static DemandeExamen reconstituer(DemandeExamenId id, UUID patientId, CentreId centreId,
                                             String prescripteurId, LocalDate dateDemande,
                                             CategorieExamen categorie, boolean urgent, String motif,
                                             StatutDemandeExamen statut, String conclusion,
                                             List<LigneDemandeExamen> lignes, OffsetDateTime createdAt,
                                             OffsetDateTime updatedAt) {
        return new DemandeExamen(id, patientId, centreId, prescripteurId, dateDemande, categorie, urgent, motif,
                statut, conclusion, lignes, createdAt, updatedAt);
    }

    /**
     * Le pr\u00e9l\u00e8vement a \u00e9t\u00e9 effectu\u00e9. {@code DEMANDE \u2192 PRELEVE}.
     */
    public void preleve(OffsetDateTime maintenant) {
        transitionner(StatutDemandeExamen.PRELEVE, maintenant);
    }

    /**
     * Un r\u00e9sultat vient d'\u00eatre saisi pour cette demande. Passe en {@code RESULTAT_DISPONIBLE}.
     * L'\u00e9v\u00e9ment {@code ResultatExamenRecu} est construit par la couche application.
     */
    public void marquerResultatDisponible(Instant maintenant) {
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        transitionner(StatutDemandeExamen.RESULTAT_DISPONIBLE,
                maintenant.atOffset(java.time.ZoneOffset.UTC));
    }

    /**
     * Le m\u00e9decin valide les r\u00e9sultats et cloture la demande.
     * {@code RESULTAT_DISPONIBLE \u2192 VALIDE}.
     */
    public void valider(String conclusion, OffsetDateTime maintenant) {
        transitionner(StatutDemandeExamen.VALIDE, maintenant);
        this.conclusion = conclusion;
    }

    /**
     * Annule la demande. Accessible uniquement avant {@code RESULTAT_DISPONIBLE}.
     */
    public void annuler(OffsetDateTime maintenant) {
        transitionner(StatutDemandeExamen.ANNULE, maintenant);
    }

    private void transitionner(StatutDemandeExamen vers, OffsetDateTime maintenant) {
        if (!TransitionStatutDemandeSpecification.estAutorisee(statut, vers)) {
            throw new IllegalStateException("DEMANDE_EXAMEN_TRANSITION_INVALIDE");
        }
        this.statut = vers;
        this.updatedAt = maintenant;
    }

    public DemandeExamenId id() {
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

    public LocalDate dateDemande() {
        return dateDemande;
    }

    public CategorieExamen categorie() {
        return categorie;
    }

    public boolean urgent() {
        return urgent;
    }

    public String motif() {
        return motif;
    }

    public List<LigneDemandeExamen> lignes() {
        return Collections.unmodifiableList(lignes);
    }

    public StatutDemandeExamen statut() {
        return statut;
    }

    public String conclusion() {
        return conclusion;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
