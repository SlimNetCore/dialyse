package com.hemodialyse.backend.characterization.gmao.plans;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Golden master minimal (approval maison, sans dépendance supplémentaire — cf. .ddd/research/approval-golden-master-java.md,
 * alternative « fichiers JSON maison »). Les fichiers {@code *.approved.json} sont versionnés ; un écart écrit un
 * {@code *.received.json} (ignoré par git) et fait échouer le test. Pour approuver une nouvelle valeur après revue
 * humaine : relancer avec {@code -Dcharacterization.approve=true}.
 */
public final class GoldenMaster {

    private static final Path DIR = Path.of("src", "test", "resources", "characterization", "gmao", "plans");

    private GoldenMaster() {
    }

    public static void verify(String name, String actual) {
        verify(DIR, name, actual);
    }

    /** Variante par slice : même méthode, autre dossier de goldens (réutilisée par G2 intervenants). */
    public static void verify(Path dir, String name, String actual) {
        String normalizedActual = normalize(actual);
        Path approved = dir.resolve(name + ".approved.json");
        Path received = dir.resolve(name + ".received.json");
        try {
            Files.createDirectories(dir);
            if (Boolean.getBoolean("characterization.approve")) {
                Files.writeString(approved, normalizedActual, StandardCharsets.UTF_8);
                Files.deleteIfExists(received);
                return;
            }
            if (!Files.exists(approved)) {
                Files.writeString(received, normalizedActual, StandardCharsets.UTF_8);
                fail("Golden master absent : " + approved + " (valeur reçue écrite dans " + received + ")");
            }
            String expected = normalize(Files.readString(approved, StandardCharsets.UTF_8));
            if (!expected.equals(normalizedActual)) {
                Files.writeString(received, normalizedActual, StandardCharsets.UTF_8);
                fail("Écart avec " + approved + " ; voir " + received + "\n--- attendu ---\n" + expected
                        + "\n--- reçu ---\n" + normalizedActual);
            }
            Files.deleteIfExists(received);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").stripTrailing() + "\n";
    }
}
