package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des absences sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V43) : mêmes tests que {@link AbsencesContrat}, plus l'index
 * unique partiel du rattrapage (V43) et l'échelle des montants. Base JETABLE uniquement : {@code -Dabsences.pg16.url=jdbc:postgresql://…}
 * (script {@code absences-a2-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${absences.pg16.url}",
        "spring.datasource.username=${absences.pg16.user:absences_test}",
        "spring.datasource.password=${absences.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "absences.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AbsencesPostgresContractIT extends AbsencesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderLesLignes() {
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Test
    void la_base_est_bien_postgresql_16_avec_l_index_unique_partiel_du_rattrapage() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_absence_patient_rattrapage'",
                String.class)).contains("UNIQUE").contains("RATTRAPEE");
    }

    @Test
    void une_seance_ne_rattrape_qu_une_absence_rattrapee_du_patient_mais_une_annulee_ne_reserve_rien() {
        absences().creer(dansLEtat(1, CENTRE, AMRANI, JOUR, StatutAbsence.RATTRAPEE, JOUR.plusDays(3)));
        absences().creer(dansLEtat(2, CENTRE, AMRANI, JOUR.plusDays(1), StatutAbsence.ANNULEE, JOUR.plusDays(3)));
        absences().creer(aQualifier(3, CENTRE, AMRANI, JOUR.plusDays(2)));
        absences().creer(dansLEtat(4, CENTRE, BELKACEM, JOUR, StatutAbsence.RATTRAPEE, JOUR.plusDays(3)));
        var troisieme = absences().verrouiller(CENTRE, id(3)).orElseThrow();
        troisieme.rattraper(JOUR.plusDays(3), MEDECIN, DroitDeCorrection.ACCORDE, (c, p, j) -> false,
                new com.hemodialyse.backend.contexts.absencespatients.domain.FaitsDuRattrapage() {
                    @Override
                    public boolean seanceRealisee(java.time.LocalDate jour) {
                        return true;
                    }

                    @Override
                    public boolean dejaUtilisee(java.time.LocalDate jour) {
                        return false;
                    }
                }, JOUR.plusDays(10), INSTANT);

        assertThatThrownBy(() -> absences().enregistrer(troisieme)).isInstanceOf(EcritureConcurrente.class);
    }

    @Test
    void les_montants_gardent_leur_echelle() {
        absences().creer(aQualifier(1, CENTRE, AMRANI, JOUR));

        var lue = absences().verrouiller(CENTRE, id(1)).orElseThrow();

        assertThat(lue.valorisation().montantHt().scale()).isEqualTo(2);
        assertThat(lue.valorisation().tauxDeTva().scale()).isEqualTo(2);
        assertThat(lue.valorisation().montantTtc().scale()).isEqualTo(2);
    }
}
