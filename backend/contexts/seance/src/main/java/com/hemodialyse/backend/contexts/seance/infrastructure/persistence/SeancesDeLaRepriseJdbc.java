package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lectures et écritures publiées des séances reprises (ADR 0099, SE7e) : le SEUL écrivain de {@code seances} hors des cas d'usage de la
 * Séance, pour la reprise de données. Requêtes reprises MOT POUR MOT des adapters de la plateforme, la table n'y étant plus « calculée » :
 * E12 ({@code HistoricalRecordJdbcAdapter.findExisting}), E13 ({@code insert}), {@code update} (inatteignable), E14 et E15
 * ({@code MigrationRollbackJdbcAdapter.blockers}), E16 ({@code delete}). Mêmes gardes : liste blanche des champs (descripteur de table
 * {@code seances} : {@code patientId}, {@code dateSeance}, {@code statut} ; date de création posée, pas de date de mise à jour), centre du
 * lot sur la recherche, la modification et la suppression, AUCUN centre sur les séances du patient et la facture (KB-SEA-90, reproduit :
 * prudence voulue). KB-SEA-89 corrigé le 2026-11-10 (ADR 0117) : la recherche d'une séance déjà présente ignore les séances
 * {@code ANNULEE}, tous les autres statuts continuent de bloquer. Aucune transaction propre : participe à celle de l'import ou de l'annulation (même {@code JdbcTemplate}).
 */
public class SeancesDeLaRepriseJdbc implements SeancesDeLaReprisePubliees {

    private static final String TABLE = "seances";
    private static final Set<String> CHAMPS = Set.of("patientId", "dateSeance", "statut");

    private final JdbcTemplate jdbc;

    public SeancesDeLaRepriseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String column(String field) {
        if (!CHAMPS.contains(field)) {
            throw new IllegalArgumentException("Champ non autorisé pour " + TABLE + " : " + field);
        }
        return snake(field);
    }

    /** patientId → patient_id ; dateSeance → date_seance. */
    private static String snake(String field) {
        return field.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    @Override
    public Optional<UUID> seanceDejaPresente(UUID centre, Map<String, Object> cle) {
        List<Object> params = new ArrayList<>(List.of(centre));
        // KB-SEA-89 corrigé (ADR 0117) : une séance annulée n'est pas une séance tenue, elle ne bloque plus la reprise
        StringBuilder where = new StringBuilder(" WHERE center_id = ? AND (statut IS NULL OR statut <> 'ANNULEE')");
        cle.forEach((field, value) -> {
            String column = column(field);
            if (value == null) {
                where.append(" AND ").append(column).append(" IS NULL");
            } else {
                where.append(" AND ").append(column).append(" = ?");
                params.add(value);
            }
        });
        return jdbc.queryForList("SELECT id FROM seances" + where + " ORDER BY created_at", UUID.class,
                params.toArray()).stream().findFirst();
    }

    @Override
    public UUID reprendre(UUID centre, Map<String, Object> valeurs) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        List<String> columns = new ArrayList<>(List.of("id", "center_id"));
        List<Object> params = new ArrayList<>(List.of(id, centre));
        valeurs.forEach((field, value) -> {
            columns.add(column(field));
            params.add(value);
        });
        columns.add("created_at");
        params.add(now);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        jdbc.update("INSERT INTO seances (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")", params.toArray());
        return id;
    }

    @Override
    public void modifier(UUID centre, UUID seance, Map<String, Object> valeurs) {
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        valeurs.forEach((field, value) -> {
            if (field.equals("patientId")) return; // une ligne ne change jamais de patient
            assignments.add(column(field) + " = ?");
            params.add(value);
        });
        if (assignments.isEmpty()) return;
        params.add(seance);
        params.add(centre);
        jdbc.update("UPDATE seances SET " + String.join(", ", assignments) + " WHERE id = ? AND center_id = ?", params.toArray());
    }

    @Override
    public List<String> seancesDuPatient(UUID patient) {
        return jdbc.queryForList("SELECT CAST(id AS VARCHAR(36)) FROM seances WHERE patient_id = ?", String.class, patient);
    }

    @Override
    public List<String> factureDeLaSeance(UUID seance) {
        return jdbc.queryForList("SELECT CAST(facture_id AS VARCHAR(36)) FROM seances WHERE id = ? AND facture_id IS NOT NULL", String.class,
                seance);
    }

    @Override
    public void retirer(UUID centre, UUID seance) {
        jdbc.update("DELETE FROM seances WHERE id = ? AND center_id = ?", seance, centre);
    }
}
