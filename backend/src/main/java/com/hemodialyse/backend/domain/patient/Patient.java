package com.hemodialyse.backend.domain.patient;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    private UUID id;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "code_patient")
    private String codePatient;

    @Column(name = "civilite")
    private String civilite;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "prenom", nullable = false)
    private String prenom;

    @Column(name = "sexe", nullable = false)
    private String sexe;

    @Column(name = "groupe_sanguin")
    private String groupeSanguin;

    @Column(name = "nombre_enfants")
    private Integer nombreEnfants;

    @Column(name = "date_admission", nullable = false)
    private LocalDate dateAdmission;

    @Column(name = "en_sommeil")
    private Boolean enSommeil;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "lieu_naissance")
    private String lieuNaissance;

    @Column(name = "situation_familiale")
    private String situationFamiliale;

    @Column(name = "profession1")
    private String profession1;

    @Column(name = "profession2")
    private String profession2;

    @Column(name = "adresse")
    private String adresse;

    @Column(name = "tel_personnel")
    private String telPersonnel;

    @Column(name = "tel_mobile")
    private String telMobile;

    @Column(name = "tel_bureau")
    private String telBureau;

    @Column(name = "email")
    private String email;

    @Column(name = "numero_assurance", nullable = false)
    private String numeroAssurance;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_patient", nullable = false)
    private PatientType typePatient;

    @Column(name = "etat_patient")
    private String etatPatient;

    @Column(name = "qualite_assure")
    private String qualiteAssure;

    @Column(name = "observation", columnDefinition = "TEXT")
    private String observation;

    @Column(name = "sous_kt")
    private Boolean sousKt;

    @Column(name = "photo_base64", columnDefinition = "TEXT")
    private String photoBase64;

    // FK references (stored as UUID, resolved via referential tables)
    @Column(name = "centre_payeur_id")
    private UUID centrePayeurId;

    @Column(name = "medecin_traitant_id")
    private UUID medecinTraitantId;

    @Column(name = "salle_id")
    private UUID salleId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "transporteur_aller_id")
    private UUID transporteurAllerId;

    @Column(name = "transporteur_retour_id")
    private UUID transporteurRetourId;

    @Column(name = "categorie_transport_id")
    private UUID categorieTransportId;

    // Jours de dialyse
    @Column(name = "jour_dimanche") private Boolean jourDimanche;
    @Column(name = "jour_lundi") private Boolean jourLundi;
    @Column(name = "jour_mardi") private Boolean jourMardi;
    @Column(name = "jour_mercredi") private Boolean jourMercredi;
    @Column(name = "jour_jeudi") private Boolean jourJeudi;
    @Column(name = "jour_vendredi") private Boolean jourVendredi;
    @Column(name = "jour_samedi") private Boolean jourSamedi;

    // Assure info (embedded)
    @Column(name = "assure_nom") private String assureNom;
    @Column(name = "assure_prenom") private String assurePrenom;
    @Column(name = "assure_sexe") private String assureSexe;
    @Column(name = "assure_date_naissance") private LocalDate assureDateNaissance;
    @Column(name = "assure_tel_personnel") private String assureTelPersonnel;
    @Column(name = "assure_adresse") private String assureAdresse;
    @Column(name = "assure_groupe_sanguin") private String assureGroupeSanguin;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected Patient() {}

    // Builder-style constructor for service layer
    public Patient(UUID id, UUID centerId, String nom, String prenom, String sexe,
                   LocalDate dateAdmission, LocalDate dateNaissance, String numeroAssurance,
                   PatientType typePatient) {
        this.id = id;
        this.centerId = centerId;
        this.nom = nom;
        this.prenom = prenom;
        this.sexe = sexe;
        this.dateAdmission = dateAdmission;
        this.dateNaissance = dateNaissance;
        this.numeroAssurance = numeroAssurance;
        this.typePatient = typePatient;
        this.createdAt = OffsetDateTime.now();
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getCenterId() { return centerId; }
    public String getCodePatient() { return codePatient; }
    public String getCivilite() { return civilite; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getSexe() { return sexe; }
    public String getGroupeSanguin() { return groupeSanguin; }
    public Integer getNombreEnfants() { return nombreEnfants; }
    public LocalDate getDateAdmission() { return dateAdmission; }
    public Boolean getEnSommeil() { return enSommeil; }
    public LocalDate getDateNaissance() { return dateNaissance; }
    public String getLieuNaissance() { return lieuNaissance; }
    public String getSituationFamiliale() { return situationFamiliale; }
    public String getProfession1() { return profession1; }
    public String getProfession2() { return profession2; }
    public String getAdresse() { return adresse; }
    public String getTelPersonnel() { return telPersonnel; }
    public String getTelMobile() { return telMobile; }
    public String getTelBureau() { return telBureau; }
    public String getEmail() { return email; }
    public String getNumeroAssurance() { return numeroAssurance; }
    public PatientType getTypePatient() { return typePatient; }
    public String getEtatPatient() { return etatPatient; }
    public String getQualiteAssure() { return qualiteAssure; }
    public String getObservation() { return observation; }
    public Boolean getSousKt() { return sousKt; }
    public String getPhotoBase64() { return photoBase64; }
    public UUID getCentrePayeurId() { return centrePayeurId; }
    public UUID getMedecinTraitantId() { return medecinTraitantId; }
    public UUID getSalleId() { return salleId; }
    public UUID getPositionId() { return positionId; }
    public UUID getTransporteurAllerId() { return transporteurAllerId; }
    public UUID getTransporteurRetourId() { return transporteurRetourId; }
    public UUID getCategorieTransportId() { return categorieTransportId; }
    public Boolean getJourDimanche() { return jourDimanche; }
    public Boolean getJourLundi() { return jourLundi; }
    public Boolean getJourMardi() { return jourMardi; }
    public Boolean getJourMercredi() { return jourMercredi; }
    public Boolean getJourJeudi() { return jourJeudi; }
    public Boolean getJourVendredi() { return jourVendredi; }
    public Boolean getJourSamedi() { return jourSamedi; }
    public String getAssureNom() { return assureNom; }
    public String getAssurePrenom() { return assurePrenom; }
    public String getAssureSexe() { return assureSexe; }
    public LocalDate getAssureDateNaissance() { return assureDateNaissance; }
    public String getAssureTelPersonnel() { return assureTelPersonnel; }
    public String getAssureAdresse() { return assureAdresse; }
    public String getAssureGroupeSanguin() { return assureGroupeSanguin; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // Setters for extended fields
    public void setCodePatient(String v) { this.codePatient = v; }
    public void setCivilite(String v) { this.civilite = v; }
    public void setGroupeSanguin(String v) { this.groupeSanguin = v; }
    public void setNombreEnfants(Integer v) { this.nombreEnfants = v; }
    public void setEnSommeil(Boolean v) { this.enSommeil = v; }
    public void setLieuNaissance(String v) { this.lieuNaissance = v; }
    public void setSituationFamiliale(String v) { this.situationFamiliale = v; }
    public void setProfession1(String v) { this.profession1 = v; }
    public void setProfession2(String v) { this.profession2 = v; }
    public void setAdresse(String v) { this.adresse = v; }
    public void setTelPersonnel(String v) { this.telPersonnel = v; }
    public void setTelMobile(String v) { this.telMobile = v; }
    public void setTelBureau(String v) { this.telBureau = v; }
    public void setEmail(String v) { this.email = v; }
    public void setEtatPatient(String v) { this.etatPatient = v; }
    public void setQualiteAssure(String v) { this.qualiteAssure = v; }
    public void setObservation(String v) { this.observation = v; }
    public void setSousKt(Boolean v) { this.sousKt = v; }
    public void setPhotoBase64(String v) { this.photoBase64 = v; }
    public void setCentrePayeurId(UUID v) { this.centrePayeurId = v; }
    public void setMedecinTraitantId(UUID v) { this.medecinTraitantId = v; }
    public void setSalleId(UUID v) { this.salleId = v; }
    public void setPositionId(UUID v) { this.positionId = v; }
    public void setTransporteurAllerId(UUID v) { this.transporteurAllerId = v; }
    public void setTransporteurRetourId(UUID v) { this.transporteurRetourId = v; }
    public void setCategorieTransportId(UUID v) { this.categorieTransportId = v; }
    public void setJourDimanche(Boolean v) { this.jourDimanche = v; }
    public void setJourLundi(Boolean v) { this.jourLundi = v; }
    public void setJourMardi(Boolean v) { this.jourMardi = v; }
    public void setJourMercredi(Boolean v) { this.jourMercredi = v; }
    public void setJourJeudi(Boolean v) { this.jourJeudi = v; }
    public void setJourVendredi(Boolean v) { this.jourVendredi = v; }
    public void setJourSamedi(Boolean v) { this.jourSamedi = v; }
    public void setAssureNom(String v) { this.assureNom = v; }
    public void setAssurePrenom(String v) { this.assurePrenom = v; }
    public void setAssureSexe(String v) { this.assureSexe = v; }
    public void setAssureDateNaissance(LocalDate v) { this.assureDateNaissance = v; }
    public void setAssureTelPersonnel(String v) { this.assureTelPersonnel = v; }
    public void setAssureAdresse(String v) { this.assureAdresse = v; }
    public void setAssureGroupeSanguin(String v) { this.assureGroupeSanguin = v; }
}
