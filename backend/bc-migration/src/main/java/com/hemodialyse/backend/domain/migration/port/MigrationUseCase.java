package com.hemodialyse.backend.domain.migration.port;

import com.hemodialyse.backend.domain.migration.model.EntityRun;
import com.hemodialyse.backend.domain.migration.model.MigrationBatch;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.referential.admin.model.ImportTable;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port In — reprise des données d'un système existant, par lot et par centre.
 */
public interface MigrationUseCase {

    /**
     * Ouvre le lot du centre (un seul lot en cours par centre).
     */
    MigrationBatch open(CenterId centerId, String libelle, String sourceSystem, LocalDate dateDebutReprise, String user);

    PagedResult<MigrationBatch> list(CenterId centerId, int page, int size);

    BatchDetail get(CenterId centerId, UUID batchId);

    /**
     * Vérifie ({@code dryRun}) ou importe un fichier. Rien n'est écrit tant que le fichier contient une anomalie ;
     * un fichier rejoué met à jour les lignes déjà reprises (identifiant d'origine).
     */
    EntityRun importEntity(CenterId centerId, UUID batchId, MigrationEntity entity, String fileName,
                           ImportTable table, boolean dryRun, String user);

    /**
     * Valide la reprise : le lot est figé.
     */
    MigrationBatch close(CenterId centerId, UUID batchId);

    /**
     * Supprime les données créées par le lot (refusé si elles sont déjà utilisées), puis l'annule.
     */
    MigrationBatch cancel(CenterId centerId, UUID batchId);

    /**
     * Correspondances de valeurs du centre : colonne → (valeur d'origine normalisée → valeur cible).
     */
    Map<String, Map<String, String>> valueMappings(CenterId centerId);

    void saveValueMapping(CenterId centerId, String column, String sourceValue, String targetValue);

    void deleteValueMapping(CenterId centerId, String column, String sourceValue);

    /**
     * Lot et dernier compte rendu de chaque donnée reprise.
     */
    record BatchDetail(MigrationBatch batch, List<EntityRun> runs) {
    }
}

