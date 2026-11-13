package com.hemodialyse.backend.infrastructure.migration;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAffectations;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients;
import com.hemodialyse.backend.infrastructure.migration.model.EntityRun;
import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationBatch;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.migration.port.IdMappingPort;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationBatchRepositoryPort;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.infrastructure.migration.port.ValueMappingPort;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

        public Optional<MigrationBatch> findById(CentreId centerId, UUID batchId) {
            return Optional.ofNullable(batches.get(batchId)).filter(b -> b.getCenterId().equals(centerId));
        }

        public Optional<MigrationBatch> findActive(CentreId centerId) {
            return batches.values().stream()
                    .filter(b -> b.getCenterId().equals(centerId) && b.getStatus() == MigrationBatch.Status.EN_COURS).findFirst();
        }

        public PagedResult<MigrationBatch> findPaged(CentreId centerId, int page, int size) {
            List<MigrationBatch> all = batches.values().stream().filter(b -> b.getCenterId().equals(centerId)).toList();
            return PagedResult.of(all, all.size(), page, size);
        }

        public void saveRun(CentreId centerId, UUID batchId, EntityRun run) {
            runs.add(run);
        }

        public List<EntityRun> findLatestRuns(CentreId centerId, UUID batchId) {
            return runs;
        }
    }

    static final class Ids implements IdMappingPort {
        final List<Stored> rows = new ArrayList<>();

        public Optional<String> findTarget(CentreId centerId, MigrationEntity entity, String legacyId) {
            return Optional.ofNullable(findAll(centerId, entity).get(legacyId));
        }

        public Map<String, String> findAll(CentreId centerId, MigrationEntity entity) {
            Map<String, String> out = new HashMap<>();
            rows.stream().filter(r -> r.centerId().equals(centerId) && r.mapping().entity() == entity)
                    .forEach(r -> out.put(r.mapping().legacyId(), r.mapping().targetId()));
            return out;
        }

        public void save(CentreId centerId, UUID batchId, IdMapping mapping) {
            Optional<Stored> existing = rows.stream().filter(r -> r.centerId().equals(centerId)
                    && r.mapping().entity() == mapping.entity() && r.mapping().legacyId().equals(mapping.legacyId())).findFirst();
            if (existing.isPresent()) {
                rows.remove(existing.get());
                rows.add(new Stored(centerId, existing.get().batchId(), new IdMapping(mapping.entity(), mapping.legacyId(),
                        mapping.targetId(), existing.get().mapping().operation(), existing.get().mapping().etatOrigine())));
            } else {
                rows.add(new Stored(centerId, batchId, mapping));
            }
        }

        public List<IdMapping> findByBatch(CentreId centerId, UUID batchId) {
            return rows.stream().filter(r -> r.centerId().equals(centerId) && r.batchId().equals(batchId)).map(Stored::mapping).toList();
        }

        public void deleteByBatch(CentreId centerId, UUID batchId) {
            rows.removeIf(r -> r.centerId().equals(centerId) && r.batchId().equals(batchId));
        }

        record Stored(CentreId centerId, UUID batchId, IdMapping mapping) {
        }
    }

    static final class Values implements ValueMappingPort {
        final Map<CentreId, Map<String, Map<String, String>>> maps = new HashMap<>();

        public Map<String, Map<String, String>> findAll(CentreId centerId) {
            return maps.getOrDefault(centerId, Map.of());
        }

        public void save(CentreId centerId, String column, String normalizedSource, String target) {
            maps.computeIfAbsent(centerId, c -> new HashMap<>()).computeIfAbsent(column, c -> new HashMap<>()).put(normalizedSource, target);
        }

        public void delete(CentreId centerId, String column, String normalizedSource) {
            maps.getOrDefault(centerId, Map.of()).getOrDefault(column, new HashMap<>()).remove(normalizedSource);
        }
    }

    static final class Rollback implements MigrationRollbackPort {
        final List<String> blockers = new ArrayList<>();
        final List<IdMapping> deleted = new ArrayList<>();

        public List<String> blockers(CentreId centerId, List<IdMapping> created) {
            return blockers;
        }

        public void delete(CentreId centerId, List<IdMapping> created) {
            deleted.addAll(created);
        }

        final List<IdMapping> restored = new ArrayList<>();

        public void restore(CentreId centerId, List<IdMapping> updated) {
            restored.addAll(updated);
        }
    }

    /**
     * Patients et affectations de Patient et couverture, posés sur une même mémoire : langages publiés {@code RepriseDesPatients} et
     * {@code RepriseDesAffectations} (la fusion des valeurs et les règles d'affectation sont testées dans le contexte).
     */
    static final class Patients implements RepriseDesPatients, RepriseDesAffectations {

        /** Fiche telle que la reprise la laisse. */
        static final class Fiche {
            UUID id;
            UUID centre;
            String code;
            String numeroAssurance;
            String nom;
            String prenom;
            String sexe;
            LocalDate dateNaissance;
            LocalDate dateAdmission;
            String etat;
            String qualiteAssure;
            String assureNumeroAssurance;
            UUID centrePayeur;
            UUID salle;
            UUID generateur;
            Set<DayOfWeek> jours = Set.of();
        }

        /** Ligne d'affectation. */
        record Lien(UUID id, UUID patient, UUID centre, String numeroAssurance, boolean principale, LocalDate debut, LocalDate fin) {
        }

        final Map<UUID, Fiche> rows = new LinkedHashMap<>();
        final List<Lien> liens = new ArrayList<>();
        int saves;

        /** Un patient déjà présent dans le centre (saisi hors de la reprise). */
        Fiche ajouter(CentreId centre, String nom, String prenom, String numeroAssurance, LocalDate dateNaissance) {
            Fiche f = new Fiche();
            f.id = UUID.randomUUID();
            f.centre = centre.valeur();
            f.code = "PAT-" + f.id.toString().substring(0, 8).toUpperCase();
            f.numeroAssurance = numeroAssurance;
            f.nom = nom;
            f.prenom = prenom;
            f.dateNaissance = dateNaissance;
            f.etat = "PERMANENT";
            rows.put(f.id, f);
            return f;
        }

        Optional<Fiche> findByNumeroAssurance(CentreId centre, String numeroAssurance) {
            return rows.values().stream()
                    .filter(f -> f.centre.equals(centre.valeur()) && numeroAssurance.equals(f.numeroAssurance)).findFirst();
        }

        List<Lien> historique(CentreId centre, UUID patient) {
            return liens.stream().filter(l -> l.centre().equals(centre.valeur()) && l.patient().equals(patient)).toList();
        }

        Optional<Lien> principal(CentreId centre, UUID patient) {
            return historique(centre, patient).stream().filter(Lien::principale).findFirst();
        }

        private void retirerLePrincipal(UUID centre, UUID patient) {
            liens.replaceAll(l -> l.patient().equals(patient) && l.centre().equals(centre)
                    ? new Lien(l.id(), l.patient(), l.centre(), l.numeroAssurance(), false, l.debut(), l.fin()) : l);
        }

        @Override
        public List<PatientDuCentre> duCentre(UUID centre) {
            return rows.values().stream().filter(f -> f.centre.equals(centre)).map(f -> new PatientDuCentre(f.id, f.numeroAssurance,
                    f.code, f.nom, f.prenom, f.dateNaissance)).toList();
        }

        @Override
        public Optional<PatientRepris> trouver(UUID centre, UUID patient) {
            return Optional.ofNullable(rows.get(patient)).filter(f -> f.centre.equals(centre)).map(f -> new PatientRepris(f.id, f.code,
                    f.nom, f.prenom, f.etat, f.numeroAssurance, f.centrePayeur));
        }

        @Override
        public UUID reprendre(UUID centre, UUID patient, ReportDuPatient r) {
            saves++;
            Fiche f = patient == null ? null : rows.get(patient);
            if (f == null || !f.centre.equals(centre)) {
                f = ajouter(new CentreId(centre), r.nom(), r.prenom(), r.numeroAssurance(), r.dateNaissance());
                if (patient != null && !rows.containsKey(patient)) {
                    rows.remove(f.id);
                    f.id = patient;
                    rows.put(patient, f);
                }
            }
            f.nom = r.nom();
            f.prenom = r.prenom();
            f.sexe = r.sexe();
            f.dateNaissance = r.dateNaissance();
            f.dateAdmission = r.dateAdmission();
            f.numeroAssurance = r.numeroAssurance();
            f.etat = r.etat();
            f.qualiteAssure = r.qualiteAssure();
            if (r.salle() != null) f.salle = r.salle();
            if (r.generateur() != null) f.generateur = r.generateur();
            if (r.jours() != null) f.jours = r.jours();
            if ("ASSURE_LUI_MEME".equals(r.qualiteAssure())) {
                retirerLePrincipal(centre, f.id);
                f.assureNumeroAssurance = r.numeroAssurance();
            } else {
                Optional<Lien> principal = principal(new CentreId(centre), f.id);
                if (principal.isEmpty() || !r.assureNumeroAssurance().equalsIgnoreCase(principal.get().numeroAssurance())) {
                    retirerLePrincipal(centre, f.id);
                    liens.add(new Lien(UUID.randomUUID(), f.id, centre, r.assureNumeroAssurance(), true, r.dateAdmission(), null));
                }
                f.assureNumeroAssurance = r.assureNumeroAssurance();
            }
            return f.id;
        }

        @Override
        public List<AffectationConnue> historique(UUID centre, UUID patient) {
            return historique(new CentreId(centre), patient).stream()
                    .map(l -> new AffectationConnue(l.id(), l.numeroAssurance(), l.debut())).toList();
        }

        @Override
        public UUID reprendre(UUID centre, UUID patient, UUID affectation, ReportDeLAffectation r) {
            if (r.principale()) retirerLePrincipal(centre, patient);
            Lien existant = liens.stream().filter(l -> l.id().equals(affectation) && l.centre().equals(centre)).findFirst().orElse(null);
            UUID id = existant == null ? UUID.randomUUID() : existant.id();
            Lien nouveau = new Lien(id, patient, centre, r.numeroAssurance(), r.principale(), r.dateDebut(), r.dateFin());
            if (existant == null) liens.add(nouveau);
            else liens.set(liens.indexOf(existant), nouveau);
            if (r.principale()) rows.get(patient).assureNumeroAssurance = r.numeroAssurance();
            return id;
        }
    }

    /** Assurés du langage publié de Patient et couverture (la fusion des valeurs est testée dans le contexte). */
    static final class Assures implements RepriseDesAssures {
        final Map<String, AssureConnu> rows = new LinkedHashMap<>();
        final Map<String, ReportDeLAssure> reports = new LinkedHashMap<>();

        void ajouter(String numero, CentreId center) {
            rows.put(numero, new AssureConnu(numero, center.valeur()));
        }

        @Override
        public Optional<AssureConnu> trouver(String numeroAssurance) {
            return Optional.ofNullable(rows.get(numeroAssurance));
        }

        @Override
        public Optional<AssureConnu> trouverSansCasse(String numeroAssurance) {
            return trouver(numeroAssurance).or(() -> rows.values().stream()
                    .filter(a -> a.numeroAssurance().equalsIgnoreCase(numeroAssurance)).findFirst());
        }

        @Override
        public Optional<String> memoriser(String numeroAssurance) {
            return rows.containsKey(numeroAssurance) ? Optional.of("etat-de-" + numeroAssurance) : Optional.empty();
        }

        @Override
        public void restaurer(UUID centre, String numeroAssurance, String memento) {
            // La restauration est testée dans le contexte (ReprendreLesAssuresTest).
        }

        @Override
        public void reprendre(UUID centre, ReportDeLAssure report) {
            rows.put(report.numeroAssurance(), new AssureConnu(report.numeroAssurance(), centre));
            reports.put(report.numeroAssurance(), report);
        }
    }

    static final class Referentials implements com.hemodialyse.backend.infrastructure.migration.port.AppariementDeReferentiels {
        final Map<ReferentialKind, List<ReferentialEntry>> rows = new HashMap<>();

        UUID add(ReferentialKind kind, Map<String, String> values) {
            UUID id = UUID.randomUUID();
            rows.computeIfAbsent(kind, k -> new ArrayList<>()).add(new ReferentialEntry(id, values, Map.of()));
            return id;
        }

        @Override
        public List<ReferentialEntry> proposes(CentreId c, ReferentialKind k) {
            return rows.getOrDefault(k, List.of());
        }
    }

    static final class Generateurs implements com.hemodialyse.backend.infrastructure.migration.port.GenerateursDeReprise {
        final java.util.Map<UUID, List<GenerateurRepris>> parCentre = new java.util.HashMap<>();

        /**
         * Ajoute un générateur de dialyse de test et retourne son id.
         */
        UUID addGenerateur(CentreId centerId, String code, UUID salleId) {
            GenerateurRepris generateur = new GenerateurRepris(UUID.randomUUID(), code, salleId);
            parCentre.computeIfAbsent(centerId.valeur(), c -> new ArrayList<>()).add(generateur);
            return generateur.id();
        }

        @Override
        public List<GenerateurRepris> duCentre(UUID centreId) {
            return parCentre.getOrDefault(centreId, List.of());
        }
    }
}
