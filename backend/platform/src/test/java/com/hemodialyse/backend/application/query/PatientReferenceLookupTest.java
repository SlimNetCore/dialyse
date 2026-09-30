package com.hemodialyse.backend.application.query;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Libellés et recherche par libellé des référentiels affichés dans la liste des patients.
 */
class PatientReferenceLookupTest {

    private static final UUID CENTER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER_CENTER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID MEDECIN = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEDECIN_OTHER_CENTER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID POSITION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TRANSPORTEUR = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID FORFAIT = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private EmbeddedDatabase db;
    private PatientReferenceLookup lookup;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE medecin (id UUID PRIMARY KEY, center_id UUID, nom VARCHAR(255), prenom VARCHAR(255), specialite VARCHAR(255))");
        jdbc.execute("CREATE TABLE position_creneau (id UUID PRIMARY KEY, center_id UUID, code VARCHAR(50), libelle VARCHAR(255))");
        jdbc.execute("CREATE TABLE transporteur (id UUID PRIMARY KEY, center_id UUID, nom VARCHAR(255), telephone VARCHAR(50))");
        jdbc.execute("CREATE TABLE forfait (id UUID PRIMARY KEY, center_id UUID, code VARCHAR(50), libelle VARCHAR(255), prix NUMERIC(12,2))");
        jdbc.update("INSERT INTO medecin VALUES (?, ?, 'OUARET', 'Mustapha', 'Néphrologue')", MEDECIN, CENTER);
        jdbc.update("INSERT INTO medecin VALUES (?, ?, 'OUARET', 'Autre', 'Néphrologue')", MEDECIN_OTHER_CENTER, OTHER_CENTER);
        jdbc.update("INSERT INTO position_creneau VALUES (?, ?, 'CR1', 'Matin (06h30 – 10h30)')", POSITION, CENTER);
        jdbc.update("INSERT INTO transporteur VALUES (?, ?, 'Ambulances Rouiba', '0795006136')", TRANSPORTEUR, CENTER);
        jdbc.update("INSERT INTO forfait VALUES (?, ?, 'HD-CONV', 'Hémodialyse conventionnelle', 5600)", FORFAIT, CENTER);
        lookup = new PatientReferenceLookup(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void resolvesLabelsForTheCenterOnly() {
        assertThat(lookup.medecinLabels(CENTER, Arrays.asList(MEDECIN, MEDECIN_OTHER_CENTER, null)))
                .containsExactlyEntriesOf(java.util.Map.of(MEDECIN, "OUARET Mustapha"));
        assertThat(lookup.positions(CENTER, List.of(POSITION)).get(POSITION))
                .isEqualTo(new PatientReferenceLookup.CodeLibelle("CR1", "Matin (06h30 – 10h30)"));
        assertThat(lookup.transporteurLabels(CENTER, List.of(TRANSPORTEUR, TRANSPORTEUR)))
                .containsExactlyEntriesOf(java.util.Map.of(TRANSPORTEUR, "Ambulances Rouiba"));
        assertThat(lookup.forfaits(CENTER, List.of(FORFAIT)).get(FORFAIT).libelle())
                .isEqualTo("Hémodialyse conventionnelle");
    }

    @Test
    void emptyIdsDoNotQuery() {
        assertThat(lookup.medecinLabels(CENTER, List.of())).isEmpty();
        assertThat(lookup.forfaits(CENTER, Collections.singletonList((UUID) null))).isEmpty();
    }

    @Test
    void matchesByLabelCaseInsensitivelyAndInBothNameOrders() {
        assertThat(lookup.matchMedecins(CENTER, "ouaret must")).containsExactly(MEDECIN);
        assertThat(lookup.matchMedecins(CENTER, "Mustapha Ouaret")).containsExactly(MEDECIN);
        assertThat(lookup.matchPositions(CENTER, "matin")).containsExactly(POSITION);
        assertThat(lookup.matchPositions(CENTER, "cr1")).containsExactly(POSITION);
        assertThat(lookup.matchTransporteurs(CENTER, "rouiba")).containsExactly(TRANSPORTEUR);
        assertThat(lookup.matchForfaits(CENTER, "conventionnelle")).containsExactly(FORFAIT);
        assertThat(lookup.matchForfaits(CENTER, "hd-conv")).containsExactly(FORFAIT);
    }

    @Test
    void matchIsCenterScopedAndReturnsEmptyWhenNothingMatches() {
        assertThat(lookup.matchMedecins(CENTER, "autre")).isEmpty();
        assertThat(lookup.matchTransporteurs(CENTER, "inconnu")).isEmpty();
    }

    @Test
    void stillMatchesUuidFragmentsForPreviouslySavedFilters() {
        assertThat(lookup.matchMedecins(CENTER, "10000000-0000")).containsExactly(MEDECIN);
    }
}

