package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.TransporteursJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.UsageDuForfaitParLesPec;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un compte d'usage qui échoue ne vaut jamais « 0 référence » (sinon la suppression d'un élément utilisé serait autorisée) : seule
 * une table absente d'un schéma allégé ({@link BadSqlGrammarException}) est ignorée ; tout autre échec SQL se propage (RF1/RF2,
 * ADR 0132).
 */
class ComptesDUsageEnEchecTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000a"));
    private static final UUID ID = UUID.fromString("99997700-0000-0000-0000-0000000000f2");

    private static JdbcTemplate quiEchoue(Supplier<DataAccessException> echec) {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                throw echec.get();
            }
        };
    }

    private static DataAccessException tableAbsente() {
        return new BadSqlGrammarException("test", "SELECT COUNT(*) FROM absente", new SQLException("table absente"));
    }

    private static DataAccessException delaiDepasse() {
        return new QueryTimeoutException("délai dépassé");
    }

    @Test
    void les_transporteurs_ignorent_une_table_absente_mais_pas_un_autre_echec_sql() {
        assertThat(new TransporteursJdbc(quiEchoue(ComptesDUsageEnEchecTest::tableAbsente), c -> { })
                .nombreDeReferences(CENTRE, ID)).isZero();
        assertThatThrownBy(() -> new TransporteursJdbc(quiEchoue(ComptesDUsageEnEchecTest::delaiDepasse), c -> { })
                .nombreDeReferences(CENTRE, ID)).isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void les_prises_en_charge_ignorent_une_table_absente_mais_pas_un_autre_echec_sql() {
        assertThat(new UsageDuForfaitParLesPec(quiEchoue(ComptesDUsageEnEchecTest::tableAbsente))
                .nombreDeReferences(CENTRE.valeur(), ID)).isZero();
        assertThatThrownBy(() -> new UsageDuForfaitParLesPec(quiEchoue(ComptesDUsageEnEchecTest::delaiDepasse))
                .nombreDeReferences(CENTRE.valeur(), ID)).isInstanceOf(QueryTimeoutException.class);
    }
}
