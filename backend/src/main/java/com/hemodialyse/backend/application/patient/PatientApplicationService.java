package com.hemodialyse.backend.application.patient;

import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.insurance.model.AttestationDroit;
import com.hemodialyse.backend.domain.insurance.port.AttestationRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import com.hemodialyse.backend.domain.patient.service.PatientDomainService;
import com.hemodialyse.backend.domain.patient.vo.AssureInfo;
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
 * Application Service — transactional boundary AND cross-context orchestration for
 * the Patient use cases.
 * <p>
 * The pure {@link PatientDomainService} owns the patient aggregate rules only. This
 * facade (application layer — allowed to depend on several bounded contexts) wires in
 * the assuré / attestation (insurance) / PEC (règlements) contexts, keeping the
 * {@code domain/patient} package free of cross-context dependencies so it can become
 * an independent Maven module (microservices readiness — AGENTS.md §14). It owns the
 * {@code @Transactional} boundary guaranteeing atomicity of the multi-context writes.
 */
@Service
@Transactional
public class PatientApplicationService implements PatientUseCase {

    private final PatientDomainService delegate;
    private final PatientRepositoryPort patientRepo;
    private final AttestationRepositoryPort attestationRepo;
    private final PecRepositoryPort pecRepo;
    private final AssureRepositoryPort assureRepo;
    private final AssurePatientRepositoryPort assurePatientRepo;

    public PatientApplicationService(PatientRepositoryPort patientRepo,
                                     AttestationRepositoryPort attestationRepo,
                                     PecRepositoryPort pecRepo,
                                     AssureRepositoryPort assureRepo,
                                     AssurePatientRepositoryPort assurePatientRepo) {
        this.patientRepo = patientRepo;
        this.attestationRepo = attestationRepo;
        this.pecRepo = pecRepo;
        this.assureRepo = assureRepo;
        this.assurePatientRepo = assurePatientRepo;
        this.delegate = new PatientDomainService(patientRepo);
    }

    @Override
    public Patient createPatient(CenterId centerId, CreatePatientCommand cmd) {
        Patient saved = delegate.createPatient(centerId, cmd);
        syncAssureRelation(saved, cmd, centerId);

        if (cmd.attestationDebut() != null && cmd.attestationFin() != null) {
            attestationRepo.save(new AttestationDroit(
                    cmd.attestationId() != null ? cmd.attestationId() : UUID.randomUUID(),
                    saved.getId().value(), centerId.value(),
                    cmd.attestationDebut(), cmd.attestationFin()
            ));
        }
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
        // Cross-context invariant (moved from the domain service): a non-vacancier
        // patient must have an attestation, either provided now or already existing.
        Patient current = patientRepo.findById(PatientId.of(patientId), centerId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));
        String etat = cmd.etatPatient() != null ? cmd.etatPatient() : current.getEtatPatient();
        boolean isVacancier = "VACANCIER_LOCAL".equals(etat) || "VACANCIER_ETRANGER".equals(etat);
        if (!isVacancier && (cmd.attestationDebut() == null || cmd.attestationFin() == null)) {
            var existingAtt = attestationRepo.findByPatient(centerId, patientId);
            if (existingAtt == null || existingAtt.isEmpty()) {
                throw new IllegalArgumentException("Attestation obligatoire pour un patient non-vacancier");
            }
        }

        Patient saved = delegate.updatePatient(centerId, patientId, cmd);
        syncAssureRelation(saved, cmd, centerId);

        if (cmd.attestationDebut() != null && cmd.attestationFin() != null) {
            UUID attId = cmd.attestationId() != null ? cmd.attestationId() : UUID.randomUUID();
            attestationRepo.save(new AttestationDroit(
                    attId, saved.getId().value(), centerId.value(),
                    cmd.attestationDebut(), cmd.attestationFin()
            ));
        }
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
    @Transactional(readOnly = true)
    public Patient getPatient(CenterId centerId, UUID patientId) {
        Patient p = delegate.getPatient(centerId, patientId);
        if (!"ASSURE_LUI_MEME".equals(p.getQualiteAssure())) {
            assurePatientRepo.findPrimary(centerId, patientId).ifPresent(primary ->
                    assureRepo.findByNumeroAssurance(primary.getNumeroAssurance()).ifPresent(a -> {
                        p.setAssureNumeroAssurance(a.getNumeroAssurance());
                        p.setAssureInfo(new AssureInfo(
                                a.getSexe(), a.getNom(), a.getPrenom(),
                                a.getDateNaissance() != null ? a.getDateNaissance().toString() : null,
                                a.getTelPersonnel(), a.getAdresse(), a.getGroupeSanguin(),
                                a.getTelMobile(), a.getTelBureau()
                        ));
                    }));
        }
        return p;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Patient> listPatients(CenterId centerId) {
        return delegate.listPatients(centerId);
    }

    /**
     * Cross-context orchestration (moved verbatim from the former PatientDomainService).
     */
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
            try {
                assure.setDateNaissance(LocalDate.parse(cmd.assureDateNaissance()));
            } catch (Exception ignored) {
            }
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

