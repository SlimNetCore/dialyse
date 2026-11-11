package com.hemodialyse.backend.infrastructure.migration;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;
import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.infrastructure.migration.model.EntityRun;
import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationBatch;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.migration.port.IdMappingPort;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationBatchRepositoryPort;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.infrastructure.migration.port.ValueMappingPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.hemodialyse.backend.infrastructure.migration.port.GenerateursDeReprise.GenerateurRepris;

/**
 * Adaptateurs en mémoire pour tester la reprise sans base de données.
 */
final class InMemoryMigrationPorts {

    private InMemoryMigrationPorts() {
    }

    static final class Batches implements MigrationBatchRepositoryPort {
        final Map<UUID, MigrationBatch> batches = new LinkedHashMap<>();
        final List<EntityRun> runs = new ArrayList<>();

        public MigrationBatch save(MigrationBatch batch) {
            batches.put(batch.getId(), batch);
            return batch;
        }

        public Optional<MigrationBatch> findById(CenterId centerId, UUID batchId) {
            return Optional.ofNullable(batches.get(batchId)).filter(b -> b.getCenterId().equals(centerId));
        }

        public Optional<MigrationBatch> findActive(CenterId centerId) {
            return batches.values().stream()
                    .filter(b -> b.getCenterId().equals(centerId) && b.getStatus() == MigrationBatch.Status.EN_COURS).findFirst();
        }

        public PagedResult<MigrationBatch> findPaged(CenterId centerId, int page, int size) {
            List<MigrationBatch> all = batches.values().stream().filter(b -> b.getCenterId().equals(centerId)).toList();
            return PagedResult.of(all, all.size(), page, size);
        }

        public void saveRun(CenterId centerId, UUID batchId, EntityRun run) {
            runs.add(run);
        }

        public List<EntityRun> findLatestRuns(CenterId centerId, UUID batchId) {
            return runs;
        }
    }

    static final class Ids implements IdMappingPort {
        final List<Stored> rows = new ArrayList<>();

        public Optional<String> findTarget(CenterId centerId, MigrationEntity entity, String legacyId) {
            return Optional.ofNullable(findAll(centerId, entity).get(legacyId));
        }

        public Map<String, String> findAll(CenterId centerId, MigrationEntity entity) {
            Map<String, String> out = new HashMap<>();
            rows.stream().filter(r -> r.centerId().equals(centerId) && r.mapping().entity() == entity)
                    .forEach(r -> out.put(r.mapping().legacyId(), r.mapping().targetId()));
            return out;
        }

        public void save(CenterId centerId, UUID batchId, IdMapping mapping) {
            Optional<Stored> existing = rows.stream().filter(r -> r.centerId().equals(centerId)
                    && r.mapping().entity() == mapping.entity() && r.mapping().legacyId().equals(mapping.legacyId())).findFirst();
            if (existing.isPresent()) {
                rows.remove(existing.get());
                rows.add(new Stored(centerId, existing.get().batchId(), new IdMapping(mapping.entity(), mapping.legacyId(),
                        mapping.targetId(), existing.get().mapping().operation())));
            } else {
                rows.add(new Stored(centerId, batchId, mapping));
            }
        }

        public List<IdMapping> findByBatch(CenterId centerId, UUID batchId) {
            return rows.stream().filter(r -> r.centerId().equals(centerId) && r.batchId().equals(batchId)).map(Stored::mapping).toList();
        }

        public void deleteByBatch(CenterId centerId, UUID batchId) {
            rows.removeIf(r -> r.centerId().equals(centerId) && r.batchId().equals(batchId));
        }

        record Stored(CenterId centerId, UUID batchId, IdMapping mapping) {
        }
    }

    static final class Values implements ValueMappingPort {
        final Map<CenterId, Map<String, Map<String, String>>> maps = new HashMap<>();

        public Map<String, Map<String, String>> findAll(CenterId centerId) {
            return maps.getOrDefault(centerId, Map.of());
        }

        public void save(CenterId centerId, String column, String normalizedSource, String target) {
            maps.computeIfAbsent(centerId, c -> new HashMap<>()).computeIfAbsent(column, c -> new HashMap<>()).put(normalizedSource, target);
        }

        public void delete(CenterId centerId, String column, String normalizedSource) {
            maps.getOrDefault(centerId, Map.of()).getOrDefault(column, new HashMap<>()).remove(normalizedSource);
        }
    }

    static final class Rollback implements MigrationRollbackPort {
        final List<String> blockers = new ArrayList<>();
        final List<IdMapping> deleted = new ArrayList<>();

        public List<String> blockers(CenterId centerId, List<IdMapping> created) {
            return blockers;
        }

        public void delete(CenterId centerId, List<IdMapping> created) {
            deleted.addAll(created);
        }
    }

    static final class Patients implements PatientRepositoryPort {
        final Map<UUID, Patient> rows = new LinkedHashMap<>();
        int saves;

        public Patient save(Patient patient) {
            saves++;
            rows.put(patient.getId().value(), patient);
            return patient;
        }

        public Optional<Patient> findById(PatientId id, CenterId centerId) {
            return Optional.ofNullable(rows.get(id.value())).filter(p -> p.getCenterId().equals(centerId));
        }

        public Optional<Patient> findByCodePatient(CenterId centerId, String codePatient) {
            return findAllByCenter(centerId).stream().filter(p -> codePatient.equals(p.getCodePatient())).findFirst();
        }

        public Optional<Patient> findByNumeroAssurance(CenterId centerId, String numeroAssurance) {
            return findAllByCenter(centerId).stream()
                    .filter(p -> p.getNumeroAssurance() != null && numeroAssurance.equals(p.getNumeroAssurance().value())).findFirst();
        }

        public List<Patient> findAllByCenter(CenterId centerId) {
            return rows.values().stream().filter(p -> p.getCenterId().equals(centerId)).toList();
        }

        public long countByCenter(CenterId centerId) {
            return findAllByCenter(centerId).size();
        }
    }

    /**
     * Patients du langage publié de Patient et couverture, posés sur les mêmes mémoires que l'ancien port (le migrateur d'affectations,
     * S6b-3, lit encore ces mémoires ; la fusion des valeurs est testée dans le contexte).
     */
    static final class RepriseDesPatientsEnMemoire implements com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients {
        private final Patients patients;
        private final Assignments assignments;

        RepriseDesPatientsEnMemoire(Patients patients, Assignments assignments) {
            this.patients = patients;
            this.assignments = assignments;
        }

        @Override
        public List<PatientDuCentre> duCentre(UUID centre) {
            return patients.findAllByCenter(CenterId.of(centre)).stream().map(p -> new PatientDuCentre(p.getId().value(),
                    p.getNumeroAssurance() == null ? null : p.getNumeroAssurance().value(), p.getCodePatient(), p.getNom(),
                    p.getPrenom(), p.getDateNaissance())).toList();
        }

        @Override
        public UUID reprendre(UUID centre, UUID patient, ReportDuPatient r) {
            CenterId center = CenterId.of(centre);
            Patient p = patient == null ? null : patients.findById(PatientId.of(patient), center).orElse(null);
            if (p == null) {
                p = Patient.creer(center, r.nom(), r.prenom(), r.sexe(), r.dateAdmission(), r.dateNaissance(),
                        new com.hemodialyse.backend.domain.patient.vo.NumeroAssurance(r.numeroAssurance()),
                        com.hemodialyse.backend.domain.patient.model.PatientType.NON_VACANCIER);
                if (patient != null) p.setId(PatientId.of(patient));
            }
            p.setNom(r.nom());
            p.setPrenom(r.prenom());
            p.setSexe(r.sexe());
            p.setDateNaissance(r.dateNaissance());
            p.setDateAdmission(r.dateAdmission());
            p.setNumeroAssurance(new com.hemodialyse.backend.domain.patient.vo.NumeroAssurance(r.numeroAssurance()));
            p.setEtatPatient(r.etat());
            p.setQualiteAssure(r.qualiteAssure());
            if (r.salle() != null) p.setSalleId(r.salle());
            if (r.generateur() != null) p.setGenerateurId(r.generateur());
            if (r.jours() != null) {
                java.util.Set<java.time.DayOfWeek> j = r.jours();
                p.setJoursDialyse(new com.hemodialyse.backend.domain.patient.vo.JoursDialyse(
                        j.contains(java.time.DayOfWeek.SUNDAY), j.contains(java.time.DayOfWeek.MONDAY),
                        j.contains(java.time.DayOfWeek.TUESDAY), j.contains(java.time.DayOfWeek.WEDNESDAY),
                        j.contains(java.time.DayOfWeek.THURSDAY), j.contains(java.time.DayOfWeek.FRIDAY),
                        j.contains(java.time.DayOfWeek.SATURDAY)));
            }
            UUID id = p.getId().value();
            if ("ASSURE_LUI_MEME".equals(r.qualiteAssure())) {
                assignments.clearPrimary(center, id);
                p.setAssureNumeroAssurance(r.numeroAssurance());
            } else {
                Optional<AssurePatientAssignment> primary = assignments.findPrimary(center, id);
                if (primary.isEmpty() || !r.assureNumeroAssurance().equalsIgnoreCase(primary.get().getNumeroAssurance())) {
                    assignments.clearPrimary(center, id);
                    AssurePatientAssignment a = new AssurePatientAssignment();
                    a.setPatientId(id);
                    a.setNumeroAssurance(r.assureNumeroAssurance());
                    a.setCenterId(centre);
                    a.setPrimary(true);
                    a.setDateDebutAffectation(r.dateAdmission());
                    assignments.save(a);
                }
                p.setAssureNumeroAssurance(r.assureNumeroAssurance());
            }
            patients.save(p);
            return id;
        }
    }

    /** Assurés du langage publié de Patient et couverture (la fusion des valeurs est testée dans le contexte). */
    static final class Assures implements RepriseDesAssures {
        final Map<String, AssureConnu> rows = new LinkedHashMap<>();
        final Map<String, ReportDeLAssure> reports = new LinkedHashMap<>();

        void ajouter(String numero, CenterId center) {
            rows.put(numero, new AssureConnu(numero, center.value()));
        }

        @Override
        public Optional<AssureConnu> trouver(String numeroAssurance) {
            return Optional.ofNullable(rows.get(numeroAssurance));
        }

        @Override
        public void reprendre(UUID centre, ReportDeLAssure report) {
            rows.put(report.numeroAssurance(), new AssureConnu(report.numeroAssurance(), centre));
            reports.put(report.numeroAssurance(), report);
        }
    }

    static final class Assignments implements AssurePatientRepositoryPort {
        final List<AssurePatientAssignment> rows = new ArrayList<>();

        public AssurePatientAssignment save(AssurePatientAssignment assignment) {
            if (assignment.getId() == null) {
                assignment.setId(UUID.randomUUID());
                rows.add(assignment);
            }
            return assignment;
        }

        public void clearPrimary(CenterId centerId, UUID patientId) {
            rows.stream().filter(a -> a.getPatientId().equals(patientId)).forEach(a -> a.setPrimary(false));
        }

        public void closePrimary(CenterId centerId, UUID patientId, LocalDate endDate) {
            clearPrimary(centerId, patientId);
        }

        public Optional<AssurePatientAssignment> findPrimary(CenterId centerId, UUID patientId) {
            return rows.stream().filter(a -> a.getPatientId().equals(patientId) && a.isPrimary()).findFirst();
        }

        public List<AssurePatientAssignment> findHistory(CenterId centerId, UUID patientId) {
            return rows.stream().filter(a -> a.getPatientId().equals(patientId)).toList();
        }

        public Optional<AssurePatientAssignment> findById(UUID id) {
            return rows.stream().filter(a -> a.getId().equals(id)).findFirst();
        }
    }

    static final class Referentials implements ReferentialAdminRepositoryPort {
        final Map<ReferentialKind, List<ReferentialEntry>> rows = new HashMap<>();

        UUID add(ReferentialKind kind, Map<String, String> values) {
            UUID id = UUID.randomUUID();
            rows.computeIfAbsent(kind, k -> new ArrayList<>()).add(new ReferentialEntry(id, values, Map.of()));
            return id;
        }

        public PagedResult<ReferentialEntry> findPaged(CenterId c, ReferentialKind k, String s, int p, int size) {
            throw new UnsupportedOperationException();
        }

        public Optional<ReferentialEntry> findById(CenterId c, ReferentialKind k, UUID id) {
            return rows.getOrDefault(k, List.of()).stream().filter(e -> e.id().equals(id)).findFirst();
        }

        public List<ReferentialEntry> findAllForMatching(CenterId c, ReferentialKind k) {
            return rows.getOrDefault(k, List.of());
        }

        public UUID insert(CenterId c, ReferentialKind k, Map<String, String> v) {
            throw new UnsupportedOperationException();
        }

        public void update(CenterId c, ReferentialKind k, UUID id, Map<String, String> v) {
            throw new UnsupportedOperationException();
        }

        public void delete(CenterId c, ReferentialKind k, UUID id) {
            throw new UnsupportedOperationException();
        }

        public long countUsages(CenterId c, ReferentialKind k, UUID id) {
            return 0;
        }
    }

    static final class Generateurs implements com.hemodialyse.backend.infrastructure.migration.port.GenerateursDeReprise {
        final java.util.Map<UUID, List<GenerateurRepris>> parCentre = new java.util.HashMap<>();

        /**
         * Ajoute un générateur de dialyse de test et retourne son id.
         */
        UUID addGenerateur(CenterId centerId, String code, UUID salleId) {
            GenerateurRepris generateur = new GenerateurRepris(UUID.randomUUID(), code, salleId);
            parCentre.computeIfAbsent(centerId.value(), c -> new ArrayList<>()).add(generateur);
            return generateur.id();
        }

        @Override
        public List<GenerateurRepris> duCentre(UUID centreId) {
            return parCentre.getOrDefault(centreId, List.of());
        }
    }
}
