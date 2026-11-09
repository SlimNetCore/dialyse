package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VoletsParamedicauxJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrat des volets paramédicaux sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code VoletsParamedicauxPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:seance-volets-paramedicaux;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VoletsParamedicauxJdbcContratTest extends VoletsParamedicauxContrat {
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        TableDesVolets.creer(jdbc);
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM volet_paramedical WHERE center_id IN (?, ?)", A.valeur(), B.valeur());
    }

    @Override
    protected VoletsParamedicaux volets() {
        return new VoletsParamedicauxJdbc(jdbc);
    }

    @Override
    protected Class<? extends RuntimeException> violationDUnicite() {
        return DataIntegrityViolationException.class;
    }

    /** DM1 (golden {@code autres_mesures}) : un texte au-delà de la colonne (100) est refusé par la base, rien n'est écrit. */
    @Test
    void should_refuse_a_text_longer_than_the_column_with_a_data_access_error_and_write_nothing() {
        var v = volets();
        assertThatThrownBy(() -> v.enregistrer(com.hemodialyse.backend.contexts.seance.domain.VoletParamedical.ouvrir(new UUID(8, 1), A, SEANCE,
                com.hemodialyse.backend.contexts.seance.domain.MesuresDeSeance.saisies(null, null, null, null, null, null, null, "X".repeat(101),
                        null, null), T0))).isInstanceOf(DataAccessException.class);
        org.assertj.core.api.Assertions.assertThat(v.deLaSeance(A, SEANCE)).isEmpty();
    }
}
