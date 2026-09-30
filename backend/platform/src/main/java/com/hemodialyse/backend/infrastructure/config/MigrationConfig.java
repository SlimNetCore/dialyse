package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.migration.port.IdMappingPort;
import com.hemodialyse.backend.domain.migration.port.MigrationBatchRepositoryPort;
import com.hemodialyse.backend.domain.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.domain.migration.port.ValueMappingPort;
import com.hemodialyse.backend.domain.migration.service.AffectationMigrator;
import com.hemodialyse.backend.domain.migration.service.AssureMigrator;
import com.hemodialyse.backend.domain.migration.service.MigrationDomainService;
import com.hemodialyse.backend.domain.migration.service.PatientMigrator;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Câblage du domaine de reprise des données (classes de domaine pures, sans annotation Spring).
 * Ajouter une donnée reprise = ajouter son {@code EntityMigrator} à la liste.
 */
@Configuration
public class MigrationConfig {

    @Bean
    public MigrationDomainService migrationDomainService(MigrationBatchRepositoryPort batches, IdMappingPort ids,
                                                         ValueMappingPort values, MigrationRollbackPort rollback,
                                                         PatientRepositoryPort patients, AssureRepositoryPort assures,
                                                         AssurePatientRepositoryPort assignments,
                                                         ReferentialAdminRepositoryPort referentials) {
        return new MigrationDomainService(batches, ids, values, rollback, List.of(
                new AssureMigrator(assures),
                new PatientMigrator(patients, assures, assignments, referentials),
                new AffectationMigrator(patients, assures, assignments)),
                Clock.systemDefaultZone());
    }
}

