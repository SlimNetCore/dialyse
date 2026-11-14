package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RepriseDeLHistoriqueMedical;
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
 * Écriture JDBC de l'historique médical d'un lot de reprise (RP3b, ADR 0172). Requêtes et descripteurs de tables repris MOT POUR MOT de
 * {@code HistoricalRecordJdbcAdapter} (plateforme) : la liste blanche des champs par nature (traduits en colonnes, camelCase vers snake_case,
 * jamais concaténés tels quels), la date de création posée (et la date de modification sauf pour les abords vasculaires), la recherche
 * {@code WHERE center_id = ? AND <clé>} triée par date de création, la mise à jour bornée au centre qui ne change jamais le patient. La
 * nature est contrôlée sur la liste fermée du langage publié avant d'être placée dans une requête. Aucune transaction propre : participe à
 * celle de l'import (même {@code JdbcTemplate}).
 */
public class RepriseDeLHistoriqueMedicalJdbc implements RepriseDeLHistoriqueMedical {

    /** Table d'une nature : champs acceptés, date de création, date de modification. */
    private record Table(Set<String> champs, boolean creation, boolean modification) {
    }

    private static final Map<String, Table> TABLES = Map.of(
            DOSSIER_DE_BASE, new Table(Set.of("patientId", "dateMiseEnDialyse", "nephropathieInitiale", "hepatiteBStatut", "hepatiteCStatut",
                    "observationGlobale", "conclusionMedicale"), true, true),
            ANTECEDENTS, new Table(Set.of("patientId", "typeAntecedent", "libelleLibre", "diagnosticCodeSystem", "diagnosticCode", "dateDebut",
                    "dateFin", "statutClinique", "severite", "note"), true, true),
            SEROLOGIES, new Table(Set.of("patientId", "marqueur", "resultat", "datePrelevement", "titre", "unite", "laboratoire"), true, true),
            ABORDS_VASCULAIRES, new Table(Set.of("patientId", "typeAbord", "cote", "localisation", "dateCreation", "dateFin", "actif",
                    "complications"), true, false),
            ANALYSES, new Table(Set.of("patientId", "datePrelevement", "plaquettes", "hbGDl", "htPct", "ferritineNgMl", "cstfPct",
                    "ureePreMgDl", "ureePostMgDl", "creatinineMgDl", "ktVMensuel", "phosphoreMgDl", "calciumMgDl", "pthPgMl", "albumineGDl",
                    "proteinesGDl", "crpMgL"), true, true));

    private final JdbcTemplate jdbc;

    public RepriseDeLHistoriqueMedicalJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Table table(String nature) {
        Table table = nature == null ? null : TABLES.get(nature);
        if (table == null) throw new IllegalArgumentException("Nature d'historique médical non reprise : " + nature);
        return table;
    }

    private static String column(String nature, Table table, String champ) {
        if (!table.champs().contains(champ)) {
            throw new IllegalArgumentException("Champ non autorisé pour " + nature + " : " + champ);
        }
        return snake(champ);
    }

    /** patientId → patient_id ; hbGDl → hb_g_dl ; ktVMensuel → kt_v_mensuel. */
    private static String snake(String champ) {
        return champ.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    @Override
    public Optional<UUID> dejaPresente(UUID centre, String nature, Map<String, Object> cle) {
        Table table = table(nature);
        List<Object> params = new ArrayList<>(List.of(centre));
        StringBuilder where = new StringBuilder(" WHERE center_id = ?");
        cle.forEach((champ, valeur) -> {
            String column = column(nature, table, champ);
            if (valeur == null) {
                where.append(" AND ").append(column).append(" IS NULL");
            } else {
                where.append(" AND ").append(column).append(" = ?");
                params.add(valeur);
            }
        });
        return jdbc.queryForList("SELECT id FROM " + nature + where + " ORDER BY created_at", UUID.class, params.toArray()).stream()
                .findFirst();
    }

    @Override
    public UUID reprendre(UUID centre, String nature, Map<String, Object> valeurs) {
        Table table = table(nature);
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        List<String> columns = new ArrayList<>(List.of("id", "center_id"));
        List<Object> params = new ArrayList<>(List.of(id, centre));
        valeurs.forEach((champ, valeur) -> {
            columns.add(column(nature, table, champ));
            params.add(valeur);
        });
        if (table.creation()) {
            columns.add("created_at");
            params.add(now);
        }
        if (table.modification()) {
            columns.add("updated_at");
            params.add(now);
        }
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        jdbc.update("INSERT INTO " + nature + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")", params.toArray());
        return id;
    }

    @Override
    public void completer(UUID centre, String nature, UUID donnee, Map<String, Object> valeurs) {
        Table table = table(nature);
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        valeurs.forEach((champ, valeur) -> {
            if (champ.equals("patientId")) return; // une ligne ne change jamais de patient
            assignments.add(column(nature, table, champ) + " = ?");
            params.add(valeur);
        });
        if (table.modification()) {
            assignments.add("updated_at = ?");
            params.add(OffsetDateTime.now());
        }
        if (assignments.isEmpty()) return;
        params.add(donnee);
        params.add(centre);
        jdbc.update("UPDATE " + nature + " SET " + String.join(", ", assignments) + " WHERE id = ? AND center_id = ?", params.toArray());
    }
}
