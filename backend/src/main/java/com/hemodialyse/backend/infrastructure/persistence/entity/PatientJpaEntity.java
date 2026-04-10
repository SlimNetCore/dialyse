package com.hemodialyse.backend.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class PatientJpaEntity {
    @Id private UUID id;
    @Column(name = "center_id", nullable = false) private UUID centerId;
    @Column(name = "code_patient") private String codePatient;
    @Column(name = "civilite") private String civilite;
    @Column(name = "nom", nullable = false) private String nom;
    @Column(name = "prenom", nullable = false) private String prenom;
    @Column(name = "sexe", nullable = false) private String sexe;
    @Column(name = "groupe_sanguin") private String groupeSanguin;
    @Column(name = "nombre_enfants") private Integer nombreEnfants;
    @Column(name = "date_admission", nullable = false) private LocalDate dateAdmission;
    @Column(name = "en_sommeil") private Boolean enSommeil;
    @Column(name = "date_naissance") private LocalDate dateNaissance;
    @Column(name = "lieu_naissance") private String lieuNaissance;
    @Column(name = "situation_familiale") private String situationFamiliale;
    @Column(name = "profession1") private String profession;
    @Column(name = "adresse") private String adresse;
    @Column(name = "tel_personnel") private String telPersonnel;
    @Column(name = "tel_mobile") private String telMobile;
    @Column(name = "tel_bureau") private String telBureau;
    @Column(name = "email") private String email;
    @Column(name = "numero_assurance", nullable = false) private String numeroAssurance;
    @Column(name = "type_patient", nullable = false) private String typePatient;
    @Column(name = "etat_patient") private String etatPatient;
    @Column(name = "date_evenement_etat") private LocalDate dateEvenementEtat;
    @Column(name = "qualite_assure") private String qualiteAssure;
    @Column(name = "observation", columnDefinition = "TEXT") private String observation;
    @Column(name = "sous_kt") private Boolean sousKt;
    @Column(name = "epo_enabled") private Boolean epoEnabled;
    @Column(name = "epo_date") private LocalDate epoDate;
    @Column(name = "fer_enabled") private Boolean ferEnabled;
    @Column(name = "fer_date") private LocalDate ferDate;
    @Column(name = "photo_base64", columnDefinition = "TEXT") private String photoBase64;
    @Column(name = "centre_payeur_id") private UUID centrePayeurId;
    @Column(name = "medecin_traitant_id") private UUID medecinTraitantId;
    @Column(name = "salle_id") private UUID salleId;
    @Column(name = "position_id") private UUID positionId;
    @Column(name = "transporteur_aller_id") private UUID transporteurAllerId;
    @Column(name = "transporteur_retour_id") private UUID transporteurRetourId;
    @Column(name = "categorie_transport_id") private UUID categorieTransportId;
    @Column(name = "jour_dimanche") private Boolean jourDimanche;
    @Column(name = "jour_lundi") private Boolean jourLundi;
    @Column(name = "jour_mardi") private Boolean jourMardi;
    @Column(name = "jour_mercredi") private Boolean jourMercredi;
    @Column(name = "jour_jeudi") private Boolean jourJeudi;
    @Column(name = "jour_vendredi") private Boolean jourVendredi;
    @Column(name = "jour_samedi") private Boolean jourSamedi;
    @Column(name = "assure_nom") private String assureNom;
    @Column(name = "assure_prenom") private String assurePrenom;
    @Column(name = "assure_sexe") private String assureSexe;
    @Column(name = "assure_date_naissance") private LocalDate assureDateNaissance;
    @Column(name = "assure_tel_personnel") private String assureTelPersonnel;
    @Column(name = "assure_tel_mobile") private String assureTelMobile;
    @Column(name = "assure_tel_bureau") private String assureTelBureau;
    @Column(name = "assure_adresse") private String assureAdresse;
    @Column(name = "assure_groupe_sanguin") private String assureGroupeSanguin;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    // Standard getters/setters for JPA
    public UUID getId() { return id; } public void setId(UUID v) { this.id = v; }
    public UUID getCenterId() { return centerId; } public void setCenterId(UUID v) { this.centerId = v; }
    public String getCodePatient() { return codePatient; } public void setCodePatient(String v) { this.codePatient = v; }
    public String getCivilite() { return civilite; } public void setCivilite(String v) { this.civilite = v; }
    public String getNom() { return nom; } public void setNom(String v) { this.nom = v; }
    public String getPrenom() { return prenom; } public void setPrenom(String v) { this.prenom = v; }
    public String getSexe() { return sexe; } public void setSexe(String v) { this.sexe = v; }
    public String getGroupeSanguin() { return groupeSanguin; } public void setGroupeSanguin(String v) { this.groupeSanguin = v; }
    public Integer getNombreEnfants() { return nombreEnfants; } public void setNombreEnfants(Integer v) { this.nombreEnfants = v; }
    public LocalDate getDateAdmission() { return dateAdmission; } public void setDateAdmission(LocalDate v) { this.dateAdmission = v; }
    public Boolean getEnSommeil() { return enSommeil; } public void setEnSommeil(Boolean v) { this.enSommeil = v; }
    public LocalDate getDateNaissance() { return dateNaissance; } public void setDateNaissance(LocalDate v) { this.dateNaissance = v; }
    public String getLieuNaissance() { return lieuNaissance; } public void setLieuNaissance(String v) { this.lieuNaissance = v; }
    public String getSituationFamiliale() { return situationFamiliale; } public void setSituationFamiliale(String v) { this.situationFamiliale = v; }
    public String getProfession() { return profession; } public void setProfession(String v) { this.profession = v; }
    public String getAdresse() { return adresse; } public void setAdresse(String v) { this.adresse = v; }
    public String getTelPersonnel() { return telPersonnel; } public void setTelPersonnel(String v) { this.telPersonnel = v; }
    public String getTelMobile() { return telMobile; } public void setTelMobile(String v) { this.telMobile = v; }
    public String getTelBureau() { return telBureau; } public void setTelBureau(String v) { this.telBureau = v; }
    public String getEmail() { return email; } public void setEmail(String v) { this.email = v; }
    public String getNumeroAssurance() { return numeroAssurance; } public void setNumeroAssurance(String v) { this.numeroAssurance = v; }
    public String getTypePatient() { return typePatient; } public void setTypePatient(String v) { this.typePatient = v; }
    public String getEtatPatient() { return etatPatient; } public void setEtatPatient(String v) { this.etatPatient = v; }
    public LocalDate getDateEvenementEtat() { return dateEvenementEtat; } public void setDateEvenementEtat(LocalDate v) { this.dateEvenementEtat = v; }
    public String getQualiteAssure() { return qualiteAssure; } public void setQualiteAssure(String v) { this.qualiteAssure = v; }
    public String getObservation() { return observation; } public void setObservation(String v) { this.observation = v; }
    public Boolean getSousKt() { return sousKt; } public void setSousKt(Boolean v) { this.sousKt = v; }
    public Boolean getEpoEnabled() { return epoEnabled; } public void setEpoEnabled(Boolean v) { this.epoEnabled = v; }
    public LocalDate getEpoDate() { return epoDate; } public void setEpoDate(LocalDate v) { this.epoDate = v; }
    public Boolean getFerEnabled() { return ferEnabled; } public void setFerEnabled(Boolean v) { this.ferEnabled = v; }
    public LocalDate getFerDate() { return ferDate; } public void setFerDate(LocalDate v) { this.ferDate = v; }
    public String getPhotoBase64() { return photoBase64; } public void setPhotoBase64(String v) { this.photoBase64 = v; }
    public UUID getCentrePayeurId() { return centrePayeurId; } public void setCentrePayeurId(UUID v) { this.centrePayeurId = v; }
    public UUID getMedecinTraitantId() { return medecinTraitantId; } public void setMedecinTraitantId(UUID v) { this.medecinTraitantId = v; }
    public UUID getSalleId() { return salleId; } public void setSalleId(UUID v) { this.salleId = v; }
    public UUID getPositionId() { return positionId; } public void setPositionId(UUID v) { this.positionId = v; }
    public UUID getTransporteurAllerId() { return transporteurAllerId; } public void setTransporteurAllerId(UUID v) { this.transporteurAllerId = v; }
    public UUID getTransporteurRetourId() { return transporteurRetourId; } public void setTransporteurRetourId(UUID v) { this.transporteurRetourId = v; }
    public UUID getCategorieTransportId() { return categorieTransportId; } public void setCategorieTransportId(UUID v) { this.categorieTransportId = v; }
    public Boolean getJourDimanche() { return jourDimanche; } public void setJourDimanche(Boolean v) { this.jourDimanche = v; }
    public Boolean getJourLundi() { return jourLundi; } public void setJourLundi(Boolean v) { this.jourLundi = v; }
    public Boolean getJourMardi() { return jourMardi; } public void setJourMardi(Boolean v) { this.jourMardi = v; }
    public Boolean getJourMercredi() { return jourMercredi; } public void setJourMercredi(Boolean v) { this.jourMercredi = v; }
    public Boolean getJourJeudi() { return jourJeudi; } public void setJourJeudi(Boolean v) { this.jourJeudi = v; }
    public Boolean getJourVendredi() { return jourVendredi; } public void setJourVendredi(Boolean v) { this.jourVendredi = v; }
    public Boolean getJourSamedi() { return jourSamedi; } public void setJourSamedi(Boolean v) { this.jourSamedi = v; }
    public String getAssureNom() { return assureNom; } public void setAssureNom(String v) { this.assureNom = v; }
    public String getAssurePrenom() { return assurePrenom; } public void setAssurePrenom(String v) { this.assurePrenom = v; }
    public String getAssureSexe() { return assureSexe; } public void setAssureSexe(String v) { this.assureSexe = v; }
    public LocalDate getAssureDateNaissance() { return assureDateNaissance; } public void setAssureDateNaissance(LocalDate v) { this.assureDateNaissance = v; }
    public String getAssureTelPersonnel() { return assureTelPersonnel; } public void setAssureTelPersonnel(String v) { this.assureTelPersonnel = v; }
    public String getAssureTelMobile() { return assureTelMobile; } public void setAssureTelMobile(String v) { this.assureTelMobile = v; }
    public String getAssureTelBureau() { return assureTelBureau; } public void setAssureTelBureau(String v) { this.assureTelBureau = v; }
    public String getAssureAdresse() { return assureAdresse; } public void setAssureAdresse(String v) { this.assureAdresse = v; }
    public String getAssureGroupeSanguin() { return assureGroupeSanguin; } public void setAssureGroupeSanguin(String v) { this.assureGroupeSanguin = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}

