package com.hemodialyse.backend.domain.medical.greffe.aggregate;

import com.hemodialyse.backend.domain.medical.greffe.valueobject.LienParenteDonneur;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.ResultatCrossmatch;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanDonneur;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aggregate Root — candidat donneur vivant pour un patient receveur donné (AGENTS.md §14).
 * Un patient peut avoir plusieurs candidats donneurs évalués au fil du temps.
 * <p>
 * Portée volontairement resserrée (v1) : pas de checklist structurée par donneur comme pour le
 * receveur ({@link EtapeBilanPreGreffe}) — {@code bilanRealise} est un champ texte libre décrivant
 * ce qui a été fait/manque. Une checklist structurée pourra être ajoutée plus tard si le besoin
 * se confirme (ajout d'un {@code donneurId} nullable sur {@code EtapeBilanPreGreffe}).
 */
public final class DonneurVivant {

    private final UUID id;
    private final UUID patientId;
    private final UUID centerId;
    private final OffsetDateTime createdAt;
    private String nom;
    private String prenom;
    private LocalDate dateNaissance;
    private LienParenteDonneur lienParente;
    private String telephone;
    private String groupeSanguin;
    private String typageHla;
    private StatutBilanDonneur statutBilan;
    private ResultatCrossmatch crossmatchResultat;
    private LocalDate dateCrossmatch;
    private String bilanRealise;
    private String contreIndications;
    private String decisionFinale;
    private LocalDate dateDecision;
    private OffsetDateTime updatedAt;

    private DonneurVivant(UUID id, UUID patientId, UUID centerId, String nom, String prenom,
                          LocalDate dateNaissance, LienParenteDonneur lienParente, String telephone,
                          String groupeSanguin, String typageHla, StatutBilanDonneur statutBilan,
                          ResultatCrossmatch crossmatchResultat, LocalDate dateCrossmatch, String bilanRealise,
                          String contreIndications, String decisionFinale, LocalDate dateDecision,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.patientId = patientId;
        this.centerId = centerId;
        this.nom = nom;
        this.prenom = prenom;
        this.dateNaissance = dateNaissance;
        this.lienParente = lienParente;
        this.telephone = telephone;
        this.groupeSanguin = groupeSanguin;
        this.typageHla = typageHla;
        this.statutBilan = statutBilan;
        this.crossmatchResultat = crossmatchResultat;
        this.dateCrossmatch = dateCrossmatch;
        this.bilanRealise = bilanRealise;
        this.contreIndications = contreIndications;
        this.decisionFinale = decisionFinale;
        this.dateDecision = dateDecision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DonneurVivant enregistrer(UUID patientId, UUID centerId, String nom, String prenom,
                                            LocalDate dateNaissance, LienParenteDonneur lienParente,
                                            String telephone, String groupeSanguin, String typageHla) {
        if (patientId == null || centerId == null || nom == null || nom.isBlank() || lienParente == null) {
            throw new BusinessException("DONNEUR_VIVANT_CHAMPS_REQUIS",
                    "Patient, centre, nom et lien de parenté sont obligatoires");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return new DonneurVivant(UUID.randomUUID(), patientId, centerId, nom, prenom, dateNaissance, lienParente,
                telephone, groupeSanguin, typageHla, StatutBilanDonneur.CANDIDAT, ResultatCrossmatch.NON_FAIT,
                null, null, null, null, null, now, now);
    }

    public static DonneurVivant reconstituer(UUID id, UUID patientId, UUID centerId, String nom, String prenom,
                                             LocalDate dateNaissance, LienParenteDonneur lienParente,
                                             String telephone, String groupeSanguin, String typageHla,
                                             StatutBilanDonneur statutBilan, ResultatCrossmatch crossmatchResultat,
                                             LocalDate dateCrossmatch, String bilanRealise, String contreIndications,
                                             String decisionFinale, LocalDate dateDecision, OffsetDateTime createdAt,
                                             OffsetDateTime updatedAt) {
        return new DonneurVivant(id, patientId, centerId, nom, prenom, dateNaissance, lienParente, telephone,
                groupeSanguin, typageHla, statutBilan, crossmatchResultat, dateCrossmatch, bilanRealise,
                contreIndications, decisionFinale, dateDecision, createdAt, updatedAt);
    }

    public void mettreAJour(String nom, String prenom, LocalDate dateNaissance, LienParenteDonneur lienParente,
                            String telephone, String groupeSanguin, String typageHla, StatutBilanDonneur statutBilan,
                            ResultatCrossmatch crossmatchResultat, LocalDate dateCrossmatch, String bilanRealise,
                            String contreIndications, String decisionFinale, LocalDate dateDecision) {
        if (nom == null || nom.isBlank() || lienParente == null || statutBilan == null) {
            throw new BusinessException("DONNEUR_VIVANT_CHAMPS_REQUIS",
                    "Nom, lien de parenté et statut de bilan sont obligatoires");
        }
        this.nom = nom;
        this.prenom = prenom;
        this.dateNaissance = dateNaissance;
        this.lienParente = lienParente;
        this.telephone = telephone;
        this.groupeSanguin = groupeSanguin;
        this.typageHla = typageHla;
        this.statutBilan = statutBilan;
        this.crossmatchResultat = crossmatchResultat != null ? crossmatchResultat : ResultatCrossmatch.NON_FAIT;
        this.dateCrossmatch = dateCrossmatch;
        this.bilanRealise = bilanRealise;
        this.contreIndications = contreIndications;
        this.decisionFinale = decisionFinale;
        this.dateDecision = dateDecision;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getCenterId() {
        return centerId;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public LienParenteDonneur getLienParente() {
        return lienParente;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getGroupeSanguin() {
        return groupeSanguin;
    }

    public String getTypageHla() {
        return typageHla;
    }

    public StatutBilanDonneur getStatutBilan() {
        return statutBilan;
    }

    public ResultatCrossmatch getCrossmatchResultat() {
        return crossmatchResultat;
    }

    public LocalDate getDateCrossmatch() {
        return dateCrossmatch;
    }

    public String getBilanRealise() {
        return bilanRealise;
    }

    public String getContreIndications() {
        return contreIndications;
    }

    public String getDecisionFinale() {
        return decisionFinale;
    }

    public LocalDate getDateDecision() {
        return dateDecision;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
