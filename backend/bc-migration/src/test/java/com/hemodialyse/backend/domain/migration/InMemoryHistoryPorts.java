package com.hemodialyse.backend.domain.migration;

import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.HistoricalRecordPort;
import com.hemodialyse.backend.domain.migration.port.OpeningBalancePort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateurs en mémoire pour l'historique repris et les soldes d'ouverture.
 */
final class InMemoryHistoryPorts {

    private InMemoryHistoryPorts() {
    }

    static final class Records implements HistoricalRecordPort {
        final List<Stored> rows = new ArrayList<>();

        List<Stored> of(MigrationEntity entity) {
            return rows.stream().filter(r -> r.entity() == entity).toList();
        }

        UUID seed(CenterId centerId, MigrationEntity entity, Map<String, Object> values) {
            UUID id = UUID.randomUUID();
            rows.add(new Stored(centerId, entity, id, new LinkedHashMap<>(values)));
            return id;
        }

        public Optional<UUID> findExisting(CenterId centerId, MigrationEntity entity, Map<String, Object> key) {
            return rows.stream()
                    .filter(r -> r.centerId().equals(centerId) && r.entity() == entity)
                    .filter(r -> key.entrySet().stream().allMatch(e -> Objects.equals(r.values().get(e.getKey()), e.getValue())))
                    .map(Stored::id).findFirst();
        }

        public UUID insert(CenterId centerId, MigrationEntity entity, Map<String, Object> values) {
            return seed(centerId, entity, values);
        }

        public void update(CenterId centerId, MigrationEntity entity, UUID id, Map<String, Object> values) {
            rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow().values().putAll(values);
        }

        record Stored(CenterId centerId, MigrationEntity entity, UUID id, Map<String, Object> values) {
        }
    }

    static final class Balances implements OpeningBalancePort {
        final List<Stored> invoices = new ArrayList<>();
        final List<UUID> paidInPlatform = new ArrayList<>();

        public Optional<UUID> findByNumero(CenterId centerId, String numero) {
            return invoices.stream().filter(s -> s.invoice().numero().equals(numero)).map(Stored::id).findFirst();
        }

        public long countPaymentsOutsideMigration(CenterId centerId, UUID factureId) {
            return paidInPlatform.contains(factureId) ? 1 : 0;
        }

        public UUID create(CenterId centerId, OpeningInvoice invoice) {
            UUID id = UUID.randomUUID();
            invoices.add(new Stored(id, invoice));
            return id;
        }

        public void replace(CenterId centerId, UUID factureId, OpeningInvoice invoice) {
            invoices.removeIf(s -> s.id().equals(factureId));
            invoices.add(new Stored(factureId, invoice));
        }

        record Stored(UUID id, OpeningInvoice invoice) {
        }
    }
}

