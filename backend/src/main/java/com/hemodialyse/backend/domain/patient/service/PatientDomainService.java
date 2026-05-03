package com.hemodialyse.backend.domain.patient.service;

import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.patient.vo.AssureInfo;
import com.hemodialyse.backend.domain.patient.vo.JoursDialyse;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.pec.model.PriseEnCharge;
import com.hemodialyse.backend.domain.pec.port.PecRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Patient business rules.
 */
@Service
@Transactional
public class PatientDomainService implements PatientUseCase {

    private final PatientRepositoryPort patientRepo;
    private final AttestationRepositoryPort attestationRepo;
    private final PecRepositoryPort pecRepo;
    private final AssureRepositoryPort assureRepo;
    private final AssurePatientRepositoryPort assurePatientRepo;

    public PatientDomainService(PatientRepositoryPort patientRepo,
                                AttestationRepositoryPort attestationRepo,
                                PecRepositoryPort pecRepo,
                                AssureRepositoryPort assureRepo,
                                AssurePatientRepositoryPort assurePatientRepo) {
        this.patientRepo = patientRepo;
        this.attestationRepo = attestationRepo;
        this.pecRepo = pecRepo;
        this.assureRepo = assureRepo;
        this.assurePatientRepo = assurePatientRepo;
    }

    @Override
    public Patient createPatient(CenterId centerId, CreatePatientCommand cmd) {
        // Business rule: uniqueness of insurance number within a center
        if (patientRepo.findByNumeroAssurance(centerId, cmd.numeroAssurance()).isPresent()) {
            throw new IllegalStateException("Numero assurance deja utilise pour ce centre");
        }

        // Business rule: attestation mandatory unless patient is vacancier (from etatPatient)
        String etat = cmd.etatPatient() != null ? cmd.etatPatient() : "PERMANENT";
        boolean isVacancier = "VACANCIER_LOCAL".equals(etat) || "VACANCIER_ETRANGER".equals(etat);
        PatientType type = isVacancier ? PatientType.VACANCIER : PatientType.NON_VACANCIER;
        if (!isVacancier) {
            if (cmd.attestationDebut() == null || cmd.attestationFin() == null) {
                throw new IllegalArgumentException("Attestation obligatoire pour un patient non-vacancier");
            }
        }

        // Create aggregate root via factory method
        Patient patient = Patient.creer(
            centerId, cmd.nom(), cmd.prenom(), cmd.sexe(),
            cmd.dateAdmission(), cmd.dateNaissance(),
            new NumeroAssurance(cmd.numeroAssurance()), type
        );

        // Hydrate value objects and extended fields
        patient.setCivilite(cmd.civilite());
        patient.setGroupeSanguin(cmd.groupeSanguin());
        patient.setNombreEnfants(cmd.nombreEnfants());
        patient.setLieuNaissance(cmd.lieuNaissance());
        patient.setSituationFamiliale(cmd.situationFamiliale());
        patient.setProfession(cmd.profession());
        patient.setAdresse(cmd.adresse());
        patient.setTelPersonnel(cmd.telPersonnel());
        patient.setTelMobile(cmd.telMobile());
        patient.setTelBureau(cmd.telBureau());
        patient.setEmail(cmd.email());
        patient.setSousKt(cmd.sousKt());
        patient.setEpoEnabled(cmd.epoEnabled());
        patient.setEpoDate(cmd.epoDate());
        patient.setFerEnabled(cmd.ferEnabled());
        patient.setFerDate(cmd.ferDate());
        patient.setObservation(cmd.observation());
        patient.setQualiteAssure(cmd.qualiteAssure());
        patient.setPhotoBase64(cmd.photoBase64());
        patient.setEnSommeil(cmd.enSommeil());
        patient.setEtatPatient(etat);
        patient.setDateEvenementEtat(cmd.dateEvenementEtat());
        patient.setCentrePayeurId(cmd.centrePayeurId());
        patient.setAssureNumeroAssurance(cmd.assureNumeroAssurance());
        patient.setMedecinTraitantId(cmd.medecinTraitantId());
        patient.setSalleId(cmd.salleId());
        patient.setPositionId(cmd.positionId());
        patient.setTransporteurAllerId(cmd.transporteurAllerId());
        patient.setTransporteurRetourId(cmd.transporteurRetourId());
        patient.setCategorieTransportId(cmd.categorieTransportId());
        patient.setJoursDialyse(new JoursDialyse(
            cmd.jourDimanche(), cmd.jourLundi(), cmd.jourMardi(),
            cmd.jourMercredi(), cmd.jourJeudi(), cmd.jourVendredi(), cmd.jourSamedi()
        ));
        patient.setAssureInfo(new AssureInfo(
            cmd.assureSexe(), cmd.assureNom(), cmd.assurePrenom(),
            cmd.assureDateNaissance(), cmd.assureTelPersonnel(), cmd.assureAdresse(),
            cmd.assureGroupeSanguin(), cmd.assureTelMobile(), cmd.assureTelBureau()
        ));
        patient.setAssureHistoryJson(cmd.assureHistoryJson());
        patient.setPiecesJointesJson(cmd.piecesJointesJson());

        Patient saved = patientRepo.save(patient);
        syncAssureRelation(saved, cmd, centerId);

        // Create attestation if provided
        if (cmd.attestationDebut() != null && cmd.attestationFin() != null) {
            attestationRepo.save(new AttestationDroit(
                cmd.attestationId() != null ? cmd.attestationId() : UUID.randomUUID(),
                saved.getId().value(), centerId.value(),
                cmd.attestationDebut(), cmd.attestationFin()
            ));
        }

        // Create PEC if provided
        if (cmd.pecDateDebutDemande() != null && cmd.pecDateFinDemande() != null) {
            pecRepo.save(new PriseEnCharge(
                cmd.pecId() != null ? cmd.pecId() : UUID.randomUUID(),
                saved.getId().value(), centerId.value(),
                cmd.pecDateDebutDemande(), cmd.pecDateFinDemande(), cmd.pecForfaitDemandeId()
            ));
        }

        return saved;
    }

    @Override
    public Patient updatePatient(CenterId centerId, UUID patientId, CreatePatientCommand cmd) {
        Patient patient = patientRepo.findById(PatientId.of(patientId), centerId)
            .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));

        String etat = cmd.etatPatient() != null ? cmd.etatPatient() : patient.getEtatPatient();
        boolean isVacancier = "VACANCIER_LOCAL".equals(etat) || "VACANCIER_ETRANGER".equals(etat);
        if (!isVacancier) {
            if (cmd.attestationDebut() == null || cmd.attestationFin() == null) {
                // allowed for update without touching attestation if already exists
                var existingAtt = attestationRepo.findByPatient(centerId, patientId);
                if (existingAtt == null || existingAtt.isEmpty()) {
                    throw new IllegalArgumentException("Attestation obligatoire pour un patient non-vacancier");
                }
            }
        }

        // Update core fields
        patient.setCivilite(cmd.civilite());
        patient.setNom(cmd.nom());
        patient.setPrenom(cmd.prenom());
        patient.setSexe(cmd.sexe());
        patient.setGroupeSanguin(cmd.groupeSanguin());
        patient.setNombreEnfants(cmd.nombreEnfants());
        patient.setDateAdmission(cmd.dateAdmission());
        patient.setDateNaissance(cmd.dateNaissance());
        patient.setLieuNaissance(cmd.lieuNaissance());
        patient.setSituationFamiliale(cmd.situationFamiliale());
        patient.setProfession(cmd.profession());
        patient.setAdresse(cmd.adresse());
        patient.setTelPersonnel(cmd.telPersonnel());
        patient.setTelMobile(cmd.telMobile());
        patient.setTelBureau(cmd.telBureau());
        patient.setEmail(cmd.email());
        patient.setSousKt(cmd.sousKt());
        patient.setEpoEnabled(cmd.epoEnabled());
        patient.setEpoDate(cmd.epoDate());
        patient.setFerEnabled(cmd.ferEnabled());
        patient.setFerDate(cmd.ferDate());
        patient.setObservation(cmd.observation());
        patient.setQualiteAssure(cmd.qualiteAssure());
        patient.setPhotoBase64(cmd.photoBase64());
        patient.setEnSommeil(cmd.enSommeil());
        patient.setEtatPatient(etat);
        patient.setDateEvenementEtat(cmd.dateEvenementEtat());
        patient.setCentrePayeurId(cmd.centrePayeurId());
        patient.setAssureNumeroAssurance(cmd.assureNumeroAssurance());
        patient.setMedecinTraitantId(cmd.medecinTraitantId());
        patient.setSalleId(cmd.salleId());
        patient.setPositionId(cmd.positionId());
        patient.setTransporteurAllerId(cmd.transporteurAllerId());
        patient.setTransporteurRetourId(cmd.transporteurRetourId());
        patient.setCategorieTransportId(cmd.categorieTransportId());
        patient.setJoursDialyse(new JoursDialyse(
            cmd.jourDimanche(), cmd.jourLundi(), cmd.jourMardi(),
            cmd.jourMercredi(), cmd.jourJeudi(), cmd.jourVendredi(), cmd.jourSamedi()
        ));
        patient.setAssureInfo(new AssureInfo(
            cmd.assureSexe(), cmd.assureNom(), cmd.assurePrenom(),
            cmd.assureDateNaissance(), cmd.assureTelPersonnel(), cmd.assureAdresse(),
            cmd.assureGroupeSanguin(), cmd.assureTelMobile(), cmd.assureTelBureau()
        ));
        patient.setAssureHistoryJson(cmd.assureHistoryJson());
        patient.setPiecesJointesJson(cmd.piecesJointesJson());

        Patient saved = patientRepo.save(patient);
        syncAssureRelation(saved, cmd, centerId);

        // Upsert attestation only if explicitly provided by the UI.
        if (cmd.attestationDebut() != null && cmd.attestationFin() != null) {
            UUID attId = cmd.attestationId() != null ? cmd.attestationId() : UUID.randomUUID();
            attestationRepo.save(new AttestationDroit(
                attId, saved.getId().value(), centerId.value(),
                cmd.attestationDebut(), cmd.attestationFin()
            ));
        }

        // Upsert PEC only if explicitly provided by the UI.
        if (cmd.pecDateDebutDemande() != null && cmd.pecDateFinDemande() != null) {
            UUID pecId = cmd.pecId() != null ? cmd.pecId() : UUID.randomUUID();
            pecRepo.save(new PriseEnCharge(
                pecId, saved.getId().value(), centerId.value(),
                cmd.pecDateDebutDemande(), cmd.pecDateFinDemande(), cmd.pecForfaitDemandeId()
            ));
        }

        return saved;
    }

    @Override
    public Patient getPatient(CenterId centerId, UUID patientId) {
        Patient p = patientRepo.findById(PatientId.of(patientId), centerId)
            .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        if (!"ASSURE_LUI_MEME".equals(p.getQualiteAssure())) {
            assurePatientRepo.findPrimary(centerId, patientId).ifPresent(primary -> {
                assureRepo.findByNumeroAssurance(primary.getNumeroAssurance()).ifPresent(a -> {
                    p.setAssureNumeroAssurance(a.getNumeroAssurance());
                    p.setAssureInfo(new AssureInfo(
                        a.getSexe(), a.getNom(), a.getPrenom(),
                        a.getDateNaissance() != null ? a.getDateNaissance().toString() : null,
                        a.getTelPersonnel(), a.getAdresse(), a.getGroupeSanguin(),
                        a.getTelMobile(), a.getTelBureau()
                    ));
                });
            });
        }
        return p;
    }

    @Override
    public List<Patient> listPatients(CenterId centerId) {
        return patientRepo.findAllByCenter(centerId);
    }

    private void syncAssureRelation(Patient patient, CreatePatientCommand cmd, CenterId centerId) {
        if ("ASSURE_LUI_MEME".equals(cmd.qualiteAssure())) {
            assurePatientRepo.clearPrimary(centerId, patient.getId().value());
            patient.setAssureNumeroAssurance(cmd.numeroAssurance());
            patient.setAssureInfo(new AssureInfo(
                cmd.sexe(), cmd.nom(), cmd.prenom(),
                cmd.dateNaissance() != null ? cmd.dateNaissance().toString() : null,
                cmd.telPersonnel(), cmd.adresse(), cmd.groupeSanguin(),
                cmd.telMobile(), cmd.telBureau()
            ));
            patientRepo.save(patient);
            return;
        }

        String numero = cmd.assureNumeroAssurance();
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("N° assurance assuré obligatoire pour cette qualité d'assuré");
        }

        Assure assure = assureRepo.findByNumeroAssurance(numero).orElseGet(Assure::new);
        assure.setNumeroAssurance(numero);
        assure.setCenterId(centerId.value());
        assure.setNom(cmd.assureNom());
        assure.setPrenom(cmd.assurePrenom());
        assure.setSexe(cmd.assureSexe());
        if (cmd.assureDateNaissance() != null && !cmd.assureDateNaissance().isBlank()) {
            try { assure.setDateNaissance(LocalDate.parse(cmd.assureDateNaissance())); } catch (Exception ignored) {}
        }
        assure.setTelPersonnel(cmd.assureTelPersonnel());
        assure.setTelMobile(cmd.assureTelMobile());
        assure.setTelBureau(cmd.assureTelBureau());
        assure.setAdresse(cmd.assureAdresse());
        assure.setGroupeSanguin(cmd.assureGroupeSanguin());
        if (assure.getCreatedAt() == null) assure.setCreatedAt(OffsetDateTime.now());
        assureRepo.save(assure);

        assurePatientRepo.clearPrimary(centerId, patient.getId().value());
        AssurePatientAssignment ap = new AssurePatientAssignment();
        ap.setPatientId(patient.getId().value());
        ap.setNumeroAssurance(numero);
        ap.setCenterId(centerId.value());
        ap.setPrimary(true);
        ap.setDateAffectation(OffsetDateTime.now());
        assurePatientRepo.save(ap);

        patient.setAssureNumeroAssurance(numero);
        patientRepo.save(patient);
    }
}
