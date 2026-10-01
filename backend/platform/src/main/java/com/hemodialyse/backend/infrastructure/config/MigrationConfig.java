package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.migration.port.HistoricalRecordPort;
import com.hemodialyse.backend.domain.migration.port.IdMappingPort;
import com.hemodialyse.backend.domain.migration.port.MigrationBatchRepositoryPort;
import com.hemodialyse.backend.domain.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.domain.migration.port.OpeningBalancePort;
import com.hemodialyse.backend.domain.migration.port.ValueMappingPort;
import com.hemodialyse.backend.domain.migration.service.AffectationMigrator;
import com.hemodialyse.backend.domain.migration.service.AssureMigrator;
import com.hemodialyse.backend.domain.migration.service.EntityMigrator;
import com.hemodialyse.backend.domain.migration.service.HistoryRules;
import com.hemodialyse.backend.domain.migration.service.MigrationDomainService;
import com.hemodialyse.backend.domain.migration.service.OpeningBalanceMigrator;
import com.hemodialyse.backend.domain.migration.service.PatientMigrator;
import com.hemodialyse.backend.domain.migration.service.PatientRecordMigrator;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Câblage du domaine de reprise des données (classes de domaine pures, sans annotation Spring).
 * Ajouter une donnée reprise = ajouter son {@code EntityMigrator} (ou ses {@code RecordRules}) à la liste.
 */
@Configuration
public class MigrationConfig {

    @Bean
    public MigrationDomainService migrationDomainService(MigrationBatchRepositoryPort batches, IdMappingPort ids,
                                                         ValueMappingPort values, MigrationRollbackPort rollback,
                                                         PatientRepositoryPort patients, AssureRepositoryPort assures,
                                                         AssurePatientRepositoryPort assignments,
                                                         ReferentialAdminRepositoryPort referentials,
                                                         EquipementRepositoryPort equipements,
                                                         HistoricalRecordPort records, OpeningBalancePort balances) {
        List<EntityMigrator> migrators = new ArrayList<>(List.of(
                new AssureMigrator(assures),
                new PatientMigrator(patients, assures, assignments, referentials, equipements),
                new AffectationMigrator(patients, assures, assignments),
                new OpeningBalanceMigrator(balances, patients)));
        HistoryRules.all(referentials).forEach(rules -> migrators.add(new PatientRecordMigrator(rules, records)));
        return new MigrationDomainService(batches, ids, values, rollback, migrators, Clock.systemDefaultZone());
    }
}

