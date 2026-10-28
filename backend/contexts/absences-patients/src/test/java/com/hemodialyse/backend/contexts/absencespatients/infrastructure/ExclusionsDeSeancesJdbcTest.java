package com.hemodialyse.backend.contexts.absencespatients.infrastructure;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacyseance.ExclusionsDeSeancesJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Origine d'une absence lue chez la Facturation : une séance exclue garde le lien vers l'absence de l'exclusion, dans son centre.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:absences-acl-exclusions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExclusionsDeSeancesJdbcTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0xa000, 1));
    private static final CentreId AUTRE = new CentreId(new UUID(0xa000, 2));
    private static final AbsenceId LIEE = new AbsenceId(new UUID(0xa001, 1));
    private static final AbsenceId LIBRE = new AbsenceId(new UUID(0xa001, 2));

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparer() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, exclusion_absence_id UUID)");
        jdbc.update("DELETE FROM seances");
        jdbc.update("INSERT INTO seances (id, center_id, exclusion_absence_id) VALUES (?, ?, ?)", UUID.randomUUID(), CENTRE.valeur(),
                LIEE.valeur());
        jdbc.update("INSERT INTO seances (id, center_id, exclusion_absence_id) VALUES (?, ?, NULL)", UUID.randomUUID(), CENTRE.valeur());
    }

    @Test
    void une_absence_liee_a_une_seance_exclue_du_centre_vient_d_une_exclusion() {
        var exclusions = new ExclusionsDeSeancesJdbc(jdbc);

        assertThat(exclusions.absenceIssueDUneExclusion(CENTRE, LIEE)).isTrue();
        assertThat(exclusions.absenceIssueDUneExclusion(CENTRE, LIBRE)).isFalse();
        assertThat(exclusions.absenceIssueDUneExclusion(AUTRE, LIEE)).isFalse();
    }
}
