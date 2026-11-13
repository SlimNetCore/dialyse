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

    private static final Set<String> ENCORE_DIRECTS = Set.of();
    private static final Pattern RETOUR = Pattern.compile(
            "ReferentialKind\\.(TRANSPORTEUR|FORFAIT|CAISSE|AGENCE|CENTRE_PAYEUR|CRENEAU|MEDECIN|SALLE)\\b");

    @Test
    void les_creneaux_sont_publies_par_l_organisation_et_distribues_par_la_facade() {
        assertThat(com.hemodialyse.backend.contexts.organisation.publishedlanguage.DescripteursDeLOrganisation.CRENEAU.slug())
                .isEqualTo("creneaux");
        assertThat(com.hemodialyse.backend.infrastructure.acl.organisation.ReferentielsDeLOrganisation.CRENEAU.slug())
                .isEqualTo("creneaux");
    }

    @Test
    void les_medecins_sont_publies_par_l_organisation_et_distribues_par_la_facade() {
        assertThat(com.hemodialyse.backend.contexts.organisation.publishedlanguage.DescripteursDeLOrganisation.MEDECIN.slug())
                .isEqualTo("medecins");
        assertThat(com.hemodialyse.backend.infrastructure.acl.organisation.ReferentielsDeLOrganisation.MEDECIN.slug())
                .isEqualTo("medecins");
    }

    @Test
    void les_salles_sont_publiees_par_l_organisation_et_distribuees_par_la_facade() {
        assertThat(com.hemodialyse.backend.contexts.organisation.publishedlanguage.DescripteursDeLOrganisation.SALLE.slug())
                .isEqualTo("salles");
        assertThat(com.hemodialyse.backend.infrastructure.acl.organisation.ReferentielsDeLOrganisation.SALLE.slug())
                .isEqualTo("salles");
    }

    /**
     * PL11-4, garde anti-retour : la table {@code salle} n'est plus écrite ni lue en SQL dynamique par l'administration générique (aucune
     * table, aucune référence {@code SALLE} de la façade dans ses tables, aucun {@code ReferentialKind.SALLE}).
     */
    @Test
    void l_administration_generique_n_ecrit_ni_ne_lit_plus_la_table_salle() throws IOException {
        Path base = Path.of("src", "main", "java", "com", "hemodialyse", "backend", "infrastructure", "persistence", "adapter",
                "referential").toAbsolutePath().normalize();
        Pattern sqlSalle = Pattern.compile("(?i)\\b(from|update|into|join)\\s+salle\\b|\"salle\"|ReferentialKind\\.SALLE|salle_id");
        for (String fichier : List.of("ReferentialAdminTables.java", "ReferentialAdminJdbcAdapter.java")) {
            String source = Files.readString(base.resolve(fichier)).replaceAll("(?m)^\\s*(//|\\*|/\\*).*$", "");
            assertThat(sqlSalle.matcher(source).find()).as(fichier).isFalse();
        }
        assertThat(Files.readString(base.resolve("ReferentialAdminTables.java")).replaceAll("(?m)^\\s*(//|\\*|/\\*).*$", ""))
                .as("ReferentialAdminTables").doesNotContain("SALLE").doesNotContain("salle");
    }

    @Test
    void la_facade_ne_connait_plus_aucun_referentiel_en_direct() {
        Set<String> constantes = Arrays.stream(ReferentialKind.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers())
                        && f.getType() == ReferentialKind.class)
                .map(java.lang.reflect.Field::getName).collect(Collectors.toCollection(TreeSet::new));
        assertThat(constantes).isEqualTo(new TreeSet<>(ENCORE_DIRECTS));
        assertThat(ReferentialKind.directs()).isEmpty();
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
        assertThat(retours).as("ReferentialKind.TRANSPORTEUR/FORFAIT/CAISSE/AGENCE/CENTRE_PAYEUR/CRENEAU n'existent plus : utiliser "
                + "ReferentielsDePatientCouverture / ReferentielsDeLOrganisation (ACL) alimentés par les descripteurs publiés")
                .isEmpty();
    }

    /** PL11-2, garde anti-retour : la table des créneaux n'est plus écrite ni lue en SQL dynamique par l'administration générique. */
    @Test
    void l_administration_generique_n_ecrit_ni_ne_lit_plus_position_creneau() throws IOException {
        Path base = Path.of("src", "main", "java", "com", "hemodialyse", "backend", "infrastructure", "persistence", "adapter",
                "referential").toAbsolutePath().normalize();
        for (String fichier : List.of("ReferentialAdminTables.java", "ReferentialAdminJdbcAdapter.java")) {
            String source = Files.readString(base.resolve(fichier));
            assertThat(source.replaceAll("(?m)^\\s*(//|\\*|/\\*).*$", "")).as(fichier).doesNotContain("position_creneau");
        }
    }

    /**
     * PL11-3, garde anti-retour : la table {@code medecin} n'est plus écrite ni lue en SQL dynamique par l'administration générique
     * (aucune table, aucune référence {@code MEDECIN} de la façade, aucun usage {@code medecin_*} dans ses fichiers de persistance).
     */
    @Test
    void l_administration_generique_n_ecrit_ni_ne_lit_plus_la_table_medecin() throws IOException {
        Path base = Path.of("src", "main", "java", "com", "hemodialyse", "backend", "infrastructure", "persistence", "adapter",
                "referential").toAbsolutePath().normalize();
        Pattern sqlMedecin = Pattern.compile("(?i)\\b(from|update|into|join)\\s+medecin\\b|\"medecin\"|ReferentialKind\\.MEDECIN|medecin_");
        for (String fichier : List.of("ReferentialAdminTables.java", "ReferentialAdminJdbcAdapter.java")) {
            String source = Files.readString(base.resolve(fichier)).replaceAll("(?m)^\\s*(//|\\*|/\\*).*$", "");
            assertThat(sqlMedecin.matcher(source).find()).as(fichier).isFalse();
        }
        assertThat(Files.readString(base.resolve("ReferentialAdminTables.java")).replaceAll("(?m)^\\s*(//|\\*|/\\*).*$", ""))
                .as("ReferentialAdminTables").doesNotContain("MEDECIN");
    }

    @Test
    void les_descripteurs_publies_par_patient_et_couverture_sont_ceux_que_la_facade_distribue() {
        assertThat(com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.DescripteursDeReferentiels.tous())
                .extracting(d -> d.slug()).containsExactlyInAnyOrder("transporteurs", "forfaits", "caisses", "agences",
                        "centres-payeurs");
    }
}
