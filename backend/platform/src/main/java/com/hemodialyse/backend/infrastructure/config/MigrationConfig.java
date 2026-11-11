package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.infrastructure.acl.gmao.GenerateursDeRepriseGmao;
import com.hemodialyse.backend.infrastructure.migration.port.HistoricalRecordPort;
import com.hemodialyse.backend.infrastructure.migration.port.IdMappingPort;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationBatchRepositoryPort;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.infrastructure.migration.port.OpeningBalancePort;
import com.hemodialyse.backend.infrastructure.migration.port.ValueMappingPort;
import com.hemodialyse.backend.infrastructure.migration.service.AffectationMigrator;
import com.hemodialyse.backend.infrastructure.migration.service.AssureMigrator;
import com.hemodialyse.backend.infrastructure.migration.service.EntityMigrator;
import com.hemodialyse.backend.infrastructure.migration.service.HistoryRules;
import com.hemodialyse.backend.infrastructure.migration.service.MigrationDomainService;
import com.hemodialyse.backend.infrastructure.migration.service.OpeningBalanceMigrator;
import com.hemodialyse.backend.infrastructure.migration.service.PatientMigrator;
import com.hemodialyse.backend.infrastructure.migration.service.PatientRecordMigrator;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminRepositoryPort;
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
                                                         PatientRepositoryPort patients, RepriseDesAssures assures,
                                                         AssurePatientRepositoryPort assignments,
                                                         ReferentialAdminRepositoryPort referentials,
                                                         GenerateursDuCentre generateurs,
                                                         HistoricalRecordPort records, OpeningBalancePort balances) {
        List<EntityMigrator> migrators = new ArrayList<>(List.of(
                new AssureMigrator(assures),
                new PatientMigrator(patients, assures, assignments, referentials,
                        new GenerateursDeRepriseGmao(generateurs)),
                new AffectationMigrator(patients, assures, assignments),
                new OpeningBalanceMigrator(balances, patients)));
        HistoryRules.all(referentials).forEach(rules -> migrators.add(new PatientRecordMigrator(rules, records)));
        return new MigrationDomainService(batches, ids, values, rollback, migrators, Clock.systemUTC());
    }
}

