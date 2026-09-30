package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.migration.port.HistoricalRecordPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Écriture JDBC de l'historique repris dans les tables métier (état final, sans passer par les services).
 * Les champs sont traduits en colonnes (camelCase → snake_case) après contrôle sur une liste blanche par table :
 * aucune valeur venant d'un fichier n'est jamais concaténée à une requête.
 */
@Component
public class HistoricalRecordJdbcAdapter implements HistoricalRecordPort {

    static final Map<MigrationEntity, Table> TABLES = new EnumMap<>(MigrationEntity.class);

    static {
        TABLES.put(MigrationEntity.ATTESTATIONS, new Table("attestation_droit",
                Set.of("patientId", "dateDebut", "dateFin"), true, false));
        TABLES.put(MigrationEntity.PRISES_EN_CHARGE, new Table("prise_en_charge",
                Set.of("patientId", "dateDebutDemande", "dateFinDemande", "forfaitDemandeId", "statut",
                        "dateDebutEffectif", "dateFinEffectif", "forfaitEffectifId"), true, false));
        TABLES.put(MigrationEntity.DOSSIERS_MEDICAUX, new Table("dossier_medical_patient",
                Set.of("patientId", "dateMiseEnDialyse", "nephropathieInitiale", "hepatiteBStatut", "hepatiteCStatut",
                        "observationGlobale", "conclusionMedicale"), true, true));
        TABLES.put(MigrationEntity.ANTECEDENTS, new Table("antecedents_medicaux",
                Set.of("patientId", "typeAntecedent", "libelleLibre", "diagnosticCodeSystem", "diagnosticCode", "dateDebut",
                        "dateFin", "statutClinique", "severite", "note"), true, true));
        TABLES.put(MigrationEntity.SEROLOGIES, new Table("serologies_patient",
                Set.of("patientId", "marqueur", "resultat", "datePrelevement", "titre", "unite", "laboratoire"), true, true));
        TABLES.put(MigrationEntity.ABORDS_VASCULAIRES, new Table("abords_vasculaires",
                Set.of("patientId", "typeAbord", "cote", "localisation", "dateCreation", "dateFin", "actif", "complications"),
                true, false));
        TABLES.put(MigrationEntity.ANALYSES, new Table("resultats_analyses",
                Set.of("patientId", "datePrelevement", "plaquettes", "hbGDl", "htPct", "ferritineNgMl", "cstfPct",
                        "ureePreMgDl", "ureePostMgDl", "creatinineMgDl", "ktVMensuel", "phosphoreMgDl", "calciumMgDl",
                        "pthPgMl", "albumineGDl", "proteinesGDl", "crpMgL"), true, true));
        TABLES.put(MigrationEntity.SEANCES, new Table("seances",
                Set.of("patientId", "dateSeance", "statut"), true, false));
    }

    private final JdbcTemplate jdbc;

    public HistoricalRecordJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Table table(MigrationEntity entity) {
        Table table = TABLES.get(entity);
        if (table == null) throw new IllegalArgumentException("Donnée non historisable : " + entity);
        return table;
    }

    private static String column(Table table, String field) {
        if (!table.fields().contains(field)) {
            throw new IllegalArgumentException("Champ non autorisé pour " + table.name() + " : " + field);
        }
        return snake(field);
    }

    /**
     * patientId → patient_id ; hbGDl → hb_g_dl ; ktVMensuel → kt_v_mensuel.
     */
    static String snake(String field) {
        return field.replaceAll("([A-Z])", "_$1").toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public Optional<UUID> findExisting(CenterId centerId, MigrationEntity entity, Map<String, Object> key) {
        Table table = table(entity);
        List<Object> params = new ArrayList<>(List.of(centerId.value()));
        StringBuilder where = new StringBuilder(" WHERE center_id = ?");
        key.forEach((field, value) -> {
            String column = column(table, field);
            if (value == null) {
                where.append(" AND ").append(column).append(" IS NULL");
            } else {
                where.append(" AND ").append(column).append(" = ?");
                params.add(value);
            }
        });
        return jdbc.queryForList("SELECT id FROM " + table.name() + where + " ORDER BY created_at", UUID.class,
                params.toArray()).stream().findFirst();
    }

    @Override
    public UUID insert(CenterId centerId, MigrationEntity entity, Map<String, Object> values) {
        Table table = table(entity);
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        List<String> columns = new ArrayList<>(List.of("id", "center_id"));
        List<Object> params = new ArrayList<>(List.of(id, centerId.value()));
        values.forEach((field, value) -> {
            columns.add(column(table, field));
            params.add(value);
        });
        if (table.createdAt()) {
            columns.add("created_at");
            params.add(now);
        }
        if (table.updatedAt()) {
            columns.add("updated_at");
            params.add(now);
        }
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        jdbc.update("INSERT INTO " + table.name() + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")",
                params.toArray());
        return id;
    }

    @Override
    public void update(CenterId centerId, MigrationEntity entity, UUID id, Map<String, Object> values) {
        Table table = table(entity);
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        values.forEach((field, value) -> {
            if (field.equals("patientId")) return; // une ligne ne change jamais de patient
            assignments.add(column(table, field) + " = ?");
            params.add(value);
        });
        if (table.updatedAt()) {
            assignments.add("updated_at = ?");
            params.add(OffsetDateTime.now());
        }
        if (assignments.isEmpty()) return;
        params.add(id);
        params.add(centerId.value());
        jdbc.update("UPDATE " + table.name() + " SET " + String.join(", ", assignments) + " WHERE id = ? AND center_id = ?",
                params.toArray());
    }

    /**
     * Table cible d'une donnée reprise.
     */
    record Table(String name, Set<String> fields, boolean createdAt, boolean updatedAt) {
    }
}

