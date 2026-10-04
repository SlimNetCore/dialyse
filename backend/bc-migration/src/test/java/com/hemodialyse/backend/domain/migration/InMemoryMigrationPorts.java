package com.hemodialyse.backend.domain.migration;

import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.migration.model.EntityRun;
import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.IdMappingPort;
import com.hemodialyse.backend.domain.migration.port.MigrationBatchRepositoryPort;
import com.hemodialyse.backend.domain.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.domain.migration.port.ValueMappingPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminRepositoryPort;
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

        @Override
        public List<Patient> findWithAssignment(CenterId centerId) {
            return findAllByCenter(centerId).stream().filter(p -> p.getSalleId() != null).toList();
        }

        @Override
        public List<CenterId> findCentersWithAssignments() {
            return List.of();
        }
    }

    static final class Assures implements AssureRepositoryPort {
        final Map<String, Assure> rows = new LinkedHashMap<>();

        static Assure of(String numero, CenterId center) {
            Assure a = new Assure();
            a.setNumeroAssurance(numero);
            a.setCenterId(center.value());
            a.setNom("ASSURE");
            return a;
        }

        public Assure save(Assure assure) {
            rows.put(assure.getNumeroAssurance(), assure);
            return assure;
        }

        public Optional<Assure> findByNumeroAssurance(String numeroAssurance) {
            return Optional.ofNullable(rows.get(numeroAssurance));
        }

        public List<Assure> searchByCenter(CenterId centerId, String query) {
            return rows.values().stream().filter(a -> centerId.value().equals(a.getCenterId())).toList();
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

    static final class Equipements implements com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort {
        final List<com.hemodialyse.backend.domain.gmao.model.Equipement> rows = new ArrayList<>();

        /**
         * Ajoute un générateur de dialyse de test et retourne son id.
         */
        UUID addGenerateur(CenterId centerId, String code, UUID salleId) {
            com.hemodialyse.backend.domain.gmao.model.Equipement equipement =
                    com.hemodialyse.backend.domain.gmao.model.Equipement.creer(
                            code, "Générateur " + code,
                            com.hemodialyse.backend.domain.gmao.model.TypeEquipement.GENERATEUR_DIALYSE,
                            null, null, null, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC), centerId.value(), null,
                            UUID.randomUUID(), salleId, null);
            rows.add(equipement);
            return equipement.getId();
        }

        @Override
        public void save(com.hemodialyse.backend.domain.gmao.model.Equipement equipement) {
            rows.add(equipement);
        }

        @Override
        public Optional<com.hemodialyse.backend.domain.gmao.model.Equipement> findById(UUID id) {
            return rows.stream().filter(e -> e.getId().equals(id)).findFirst();
        }

        @Override
        public List<com.hemodialyse.backend.domain.gmao.model.Equipement> findByCentreId(UUID centreId) {
            return rows.stream().filter(e -> e.getCentreId().equals(centreId)).toList();
        }

        @Override
        public List<com.hemodialyse.backend.domain.gmao.model.Equipement> findByCentreIdAndStatut(UUID centreId, String statut) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.hemodialyse.backend.domain.shared.PagedResult<com.hemodialyse.backend.domain.gmao.model.Equipement> findPaged(
                UUID centreId, String statut, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByCentreIdAndStatut(UUID centreId, String statut) {
            return 0;
        }

        @Override
        public Optional<com.hemodialyse.backend.domain.gmao.model.Equipement> findByCentreIdAndCode(UUID centreId, String code) {
            return rows.stream().filter(e -> e.getCentreId().equals(centreId) && e.getCode().equals(code)).findFirst();
        }

        @Override
        public List<com.hemodialyse.backend.domain.gmao.model.Equipement> findByCentreIdAndType(UUID centreId, String type) {
            return rows.stream()
                    .filter(e -> e.getCentreId().equals(centreId) && e.getType().name().equals(type))
                    .toList();
        }

        @Override
        public List<com.hemodialyse.backend.domain.gmao.model.Equipement> findByCentreIdAndTypeAndSalleId(
                UUID centreId, String type, UUID salleId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(UUID id) {
            return rows.stream().anyMatch(e -> e.getId().equals(id));
        }

        @Override
        public void delete(UUID id) {
            rows.removeIf(e -> e.getId().equals(id));
        }

        @Override
        public long countByCentreId(UUID centreId) {
            return rows.stream().filter(e -> e.getCentreId().equals(centreId)).count();
        }
    }
}

