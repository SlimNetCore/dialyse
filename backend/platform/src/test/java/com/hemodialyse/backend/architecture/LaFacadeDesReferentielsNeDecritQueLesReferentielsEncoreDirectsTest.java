package com.hemodialyse.backend.architecture;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PL11-1 (ADR 0157), garde anti-retour : la façade d'administration des référentiels ne décrit plus en direct que les créneaux, les
 * salles et les médecins (migrés par PL11-2 à PL11-4). Les descripteurs des transporteurs, forfaits, caisses, agences et centres
 * payeurs sont publiés par Patient et couverture ({@code DescripteursDeReferentiels}) ; la plateforme ne fait que les recevoir (SPI
 * {@code ReferentialAdminDelegate#descripteurs()}) et les référencer par {@code ReferentielsDePatientCouverture}. Ce test remplace
 * la liste de butoirs « descripteurs résiduels » du 2027-03-31 (soldée, sans report).
 */
class LaFacadeDesReferentielsNeDecritQueLesReferentielsEncoreDirectsTest {

    private static final Set<String> ENCORE_DIRECTS = Set.of("CRENEAU", "SALLE", "MEDECIN");
    private static final Pattern RETOUR = Pattern.compile("ReferentialKind\\.(TRANSPORTEUR|FORFAIT|CAISSE|AGENCE|CENTRE_PAYEUR)\\b");

    @Test
    void la_facade_ne_connait_en_direct_que_creneau_salle_et_medecin() {
        Set<String> constantes = Arrays.stream(ReferentialKind.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers())
                        && f.getType() == ReferentialKind.class)
                .map(java.lang.reflect.Field::getName).collect(Collectors.toCollection(TreeSet::new));
        assertThat(constantes).isEqualTo(new TreeSet<>(ENCORE_DIRECTS));
        assertThat(ReferentialKind.directs()).extracting(ReferentialKind::slug)
                .containsExactly("creneaux", "salles", "medecins");
    }

    @Test
    void aucun_code_de_production_ne_redeclare_ni_ne_reference_les_descripteurs_migres_dans_la_facade() throws IOException {
        Path racine = Path.of("src", "main", "java").toAbsolutePath().normalize();
        List<String> retours;
        try (Stream<Path> fichiers = Files.walk(racine)) {
            retours = fichiers.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                try {
                    return RETOUR.matcher(Files.readString(p)).find();
                } catch (IOException e) {
                    throw new IllegalStateException(p.toString(), e);
                }
            }).map(p -> racine.relativize(p).toString()).sorted().toList();
        }
        assertThat(retours).as("ReferentialKind.TRANSPORTEUR/FORFAIT/CAISSE/AGENCE/CENTRE_PAYEUR n'existent plus : utiliser "
                + "ReferentielsDePatientCouverture (ACL) alimenté par les descripteurs publiés").isEmpty();
    }

    @Test
    void les_descripteurs_publies_par_patient_et_couverture_sont_ceux_que_la_facade_distribue() {
        assertThat(com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteursDeReferentiels.tous())
                .extracting(d -> d.slug()).containsExactlyInAnyOrder("transporteurs", "forfaits", "caisses", "agences",
                        "centres-payeurs");
    }
}
