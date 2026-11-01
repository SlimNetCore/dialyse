package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice PD5 : R4 — le pilotage ne produit aucune écriture dans les autres contextes et ne lit
 * que ses propres tables. Le pilotage consomme les langages publiés des autres contextes
 * (lecture seule) ; tout accès SQL direct à une table d'un autre contexte est interdit.
 * <p>
 * La garde est elle-même testée : {@link #la_garde_detecte_un_acces_direct_a_une_table_externe()}
 * prouve qu'un accès direct est bien détecté.
 */
class LectureSeuleDuPilotageTest {

    /**
     * Tables possédées par le pilotage (schéma
     * {@code backend/contexts/pilotage-direction/src/main/resources/db/schema.sql}).
     */
    private static final Set<String> TABLES_DU_PILOTAGE = Set.of(
            "tableaux_de_bord",
            "instantanes_mensuels",
            "alertes_direction",
            "exports_synthese");

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static List<String> accesExternes(Path racine) {
        try (Stream<Path> sources = Files.walk(racine)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().replace('\\', '/').contains("/entity/"))
                .filter(p -> !p.toString().replace('\\', '/').contains("/migration/"))) {
            Set<String> externes = new TreeSet<>();
            for (Path source : sources.toList()) {
                Matcher m = LECTURE_SQL.matcher(Files.readString(source));
                while (m.find()) {
                    String table = m.group(2).toLowerCase();
                    if (!TABLES_DU_PILOTAGE.contains(table)) {
                        externes.add(source + " -> " + table);
                    }
                }
            }
            return List.copyOf(externes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void le_pilotage_ne_lit_que_ses_propres_tables() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Path contexte = backend.resolve("contexts/pilotage-direction/src/main/java");

        assertThat(accesExternes(contexte))
                .as("le pilotage ne doit lire aucune table hors de ses 4 tables (R4)")
                .isEmpty();
    }

    @Test
    void la_garde_detecte_un_acces_direct_a_une_table_externe() throws IOException {
        Path bac = Files.createTempDirectory("garde-pilotage-pd5");
        Files.writeString(bac.resolve("AccesDirect.java"), """
                class AccesDirect {
                    String lecture = "SELECT a.id FROM seances a WHERE a.center_id = ?";
                    String legitime = "SELECT t.center_id FROM tableaux_de_bord t WHERE t.center_id = ?";
                }
                """);

        assertThat(accesExternes(bac))
                .as("la garde doit détecter une lecture de seances depuis le pilotage")
                .hasSize(1);

        Path bacPropre = Files.createTempDirectory("garde-pilotage-propre");
        Files.writeString(bacPropre.resolve("AccesLegitime.java"), """
                class AccesLegitime {
                    String legitime = "SELECT t.center_id FROM alertes_direction t WHERE t.center_id = ?";
                }
                """);

        assertThat(accesExternes(bacPropre))
                .as("une table du pilotage ne doit pas être signalée")
                .isEmpty();
    }
}
