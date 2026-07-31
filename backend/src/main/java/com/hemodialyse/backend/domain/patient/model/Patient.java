package com.hemodialyse.backend.domain.patient.model;

import com.hemodialyse.backend.domain.patient.vo.*;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.shared.vo.Email;
import com.hemodialyse.backend.domain.shared.vo.PhoneNumber;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aggregate root — Patient.
 * Pure domain object: NO JPA annotations. Persistence is handled by infrastructure adapters.
 */
public class Patient {

    // Identity
    private PatientId id;
    private CenterId centerId;
    private String codePatient;

    // Civil status
    private String civilite;
    private String nom;
    private String prenom;
    private String sexe;
    private String groupeSanguin;
    private int nombreEnfants;
    private LocalDate dateNaissance;
    private String lieuNaissance;
    private String situationFamiliale;

    // Admission
    private LocalDate dateAdmission;
    private boolean enSommeil;
    private String etatPatient; // PERMANENT, OCCASIONNEL, TRANSFERE, DECEDE, GREFFE, GUERRI, VACANCIER_LOCAL, VACANCIER_ETRANGER
    private LocalDate dateEvenementEtat;

    // Contact
    private String profession;
    private String adresse;
    private String telPersonnel;
    private String telMobile;
    private String telBureau;
    private String email;

    // Medical
    private boolean sousKt;
    private boolean epoEnabled;
    private LocalDate epoDate;
    private boolean ferEnabled;
    private LocalDate ferDate;
    private String observation;
    private String photoBase64;

    // Insurance
    private NumeroAssurance numeroAssurance;
    private PatientType typePatient;
    private String qualiteAssure; // ASSURE_LUI_MEME, ENFANT, CONJOINT, ASCENDANT, AUTRE
    private UUID centrePayeurId;
    private String assureNumeroAssurance;
    private AssureInfo assureInfo;
    private String assureHistoryJson;
    private String piecesJointesJson;

    // Affectation
    private UUID medecinTraitantId;
    private UUID salleId;
    private UUID positionId;
    private UUID transporteurAllerId;
    private UUID transporteurRetourId;
    private UUID categorieTransportId;
    private JoursDialyse joursDialyse;

    // Audit
    private OffsetDateTime createdAt;

    public Patient() {}

    /**
     * Factory method — enforces domain invariants at creation time
     */
    public static Patient creer(
        CenterId centerId, String nom, String prenom, String sexe,
        LocalDate dateAdmission, LocalDate dateNaissance,
        NumeroAssurance numeroAssurance, PatientType typePatient
    ) {
        if (nom == null || nom.isBlank()) throw new IllegalArgumentException("Nom obligatoire");
        if (prenom == null || prenom.isBlank()) throw new IllegalArgumentException("Prenom obligatoire");
        if (sexe == null || sexe.isBlank()) throw new IllegalArgumentException("Sexe obligatoire");
        if (dateAdmission == null) throw new IllegalArgumentException("Date admission obligatoire");
        if (dateNaissance == null) throw new IllegalArgumentException("Date naissance obligatoire");

        Patient p = new Patient();
        p.id = PatientId.generate();
        p.centerId = centerId;
        p.codePatient = "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        p.nom = nom;
        p.prenom = prenom;
        p.sexe = sexe;
        p.dateAdmission = dateAdmission;
        p.dateNaissance = dateNaissance;
        p.numeroAssurance = numeroAssurance;
        p.typePatient = typePatient != null ? typePatient : PatientType.NON_VACANCIER;
        p.etatPatient = "PERMANENT";
        p.qualiteAssure = "ASSURE_LUI_MEME";
        p.joursDialyse = JoursDialyse.none();
        p.assureInfo = AssureInfo.empty();
        p.createdAt = OffsetDateTime.now();
        return p;
    }

    // ═══ Getters ═══
    public PatientId getId() { return id; }
    public CenterId getCenterId() { return centerId; }
    public String getCodePatient() { return codePatient; }
    public String getCivilite() { return civilite; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getSexe() { return sexe; }
    public String getGroupeSanguin() { return groupeSanguin; }
    public int getNombreEnfants() { return nombreEnfants; }
    public LocalDate getDateNaissance() { return dateNaissance; }
    public String getLieuNaissance() { return lieuNaissance; }
    public String getSituationFamiliale() { return situationFamiliale; }
    public LocalDate getDateAdmission() { return dateAdmission; }
    public boolean isEnSommeil() { return enSommeil; }
    public String getEtatPatient() { return etatPatient; }
    public LocalDate getDateEvenementEtat() { return dateEvenementEtat; }
    public String getProfession() { return profession; }
    public String getAdresse() { return adresse; }
    public String getTelPersonnel() { return telPersonnel; }
    public String getTelMobile() { return telMobile; }
    public String getTelBureau() { return telBureau; }
    public String getEmail() { return email; }
    public boolean isSousKt() { return sousKt; }
    public boolean isEpoEnabled() { return epoEnabled; }
    public LocalDate getEpoDate() { return epoDate; }
    public boolean isFerEnabled() { return ferEnabled; }
    public LocalDate getFerDate() { return ferDate; }
    public String getObservation() { return observation; }
    public String getPhotoBase64() { return photoBase64; }
    public NumeroAssurance getNumeroAssurance() { return numeroAssurance; }
    public PatientType getTypePatient() { return typePatient; }
    public String getQualiteAssure() { return qualiteAssure; }
    public UUID getCentrePayeurId() { return centrePayeurId; }
    public String getAssureNumeroAssurance() { return assureNumeroAssurance; }
    public AssureInfo getAssureInfo() { return assureInfo; }
    public String getAssureHistoryJson() { return assureHistoryJson; }
    public UUID getMedecinTraitantId() { return medecinTraitantId; }
    public UUID getSalleId() { return salleId; }
    public UUID getPositionId() { return positionId; }
    public UUID getTransporteurAllerId() { return transporteurAllerId; }
    public UUID getTransporteurRetourId() { return transporteurRetourId; }
    public UUID getCategorieTransportId() { return categorieTransportId; }
    public JoursDialyse getJoursDialyse() { return joursDialyse; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // ═══ Domain setters (for hydration from persistence & updates) ═══
    public void setId(PatientId id) { this.id = id; }
    public void setCenterId(CenterId centerId) { this.centerId = centerId; }
    public void setCodePatient(String v) { this.codePatient = v; }
    public void setCivilite(String v) { this.civilite = v; }
    public void setNom(String v) { this.nom = v; }
    public void setPrenom(String v) { this.prenom = v; }
    public void setSexe(String v) { this.sexe = v; }
    public void setGroupeSanguin(String v) { this.groupeSanguin = v; }
    public void setNombreEnfants(int v) { this.nombreEnfants = v; }
    public void setDateNaissance(LocalDate v) { this.dateNaissance = v; }
    public void setLieuNaissance(String v) { this.lieuNaissance = v; }
    public void setSituationFamiliale(String v) { this.situationFamiliale = v; }
    public void setDateAdmission(LocalDate v) { this.dateAdmission = v; }
    public void setEnSommeil(boolean v) { this.enSommeil = v; }
    public void setEtatPatient(String v) { this.etatPatient = v; }
    public void setDateEvenementEtat(LocalDate v) { this.dateEvenementEtat = v; }
    public void setProfession(String v) { this.profession = v; }
    public void setAdresse(String v) { this.adresse = v; }

    public void setTelPersonnel(String v) {
        this.telPersonnel = (v == null || v.isBlank()) ? null : PhoneNumber.of(v).value();
    }

    public void setTelMobile(String v) {
        this.telMobile = (v == null || v.isBlank()) ? null : PhoneNumber.of(v).value();
    }

    public void setTelBureau(String v) {
        this.telBureau = (v == null || v.isBlank()) ? null : PhoneNumber.of(v).value();
    }

    public void setEmail(String v) {
        this.email = (v == null || v.isBlank()) ? null : Email.of(v).value();
    }
    public void setSousKt(boolean v) { this.sousKt = v; }
    public void setEpoEnabled(boolean v) { this.epoEnabled = v; }
    public void setEpoDate(LocalDate v) { this.epoDate = v; }
    public void setFerEnabled(boolean v) { this.ferEnabled = v; }
    public void setFerDate(LocalDate v) { this.ferDate = v; }
    public void setObservation(String v) { this.observation = v; }
    public void setPhotoBase64(String v) { this.photoBase64 = v; }
    public void setNumeroAssurance(NumeroAssurance v) { this.numeroAssurance = v; }
    public void setTypePatient(PatientType v) { this.typePatient = v; }
    public void setQualiteAssure(String v) { this.qualiteAssure = v; }
    public void setCentrePayeurId(UUID v) { this.centrePayeurId = v; }
    public void setAssureNumeroAssurance(String v) { this.assureNumeroAssurance = v; }
    public void setAssureInfo(AssureInfo v) { this.assureInfo = v; }
    public void setAssureHistoryJson(String v) { this.assureHistoryJson = v; }

    public String getPiecesJointesJson() {
        return piecesJointesJson;
    }

    public void setPiecesJointesJson(String v) {
        this.piecesJointesJson = v;
    }
    public void setMedecinTraitantId(UUID v) { this.medecinTraitantId = v; }
    public void setSalleId(UUID v) { this.salleId = v; }
    public void setPositionId(UUID v) { this.positionId = v; }
    public void setTransporteurAllerId(UUID v) { this.transporteurAllerId = v; }
    public void setTransporteurRetourId(UUID v) { this.transporteurRetourId = v; }
    public void setCategorieTransportId(UUID v) { this.categorieTransportId = v; }
    public void setJoursDialyse(JoursDialyse v) { this.joursDialyse = v; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}

