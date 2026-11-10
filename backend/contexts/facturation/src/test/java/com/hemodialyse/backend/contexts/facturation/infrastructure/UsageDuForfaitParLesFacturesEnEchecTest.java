package com.hemodialyse.backend.contexts.facturation.infrastructure;

import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture.UsageDuForfaitParLesFactures;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un compte d'usage qui échoue ne vaut jamais « 0 référence » : seule une table absente est ignorée (ADR 0132). */
class UsageDuForfaitParLesFacturesEnEchecTest {

    private static final UUID CENTRE = UUID.fromString("99997700-0000-0000-0000-00000000000a");
    private static final UUID FORFAIT = UUID.fromString("99997700-0000-0000-0000-0000000000f2");

    private static JdbcTemplate quiEchoue(DataAccessException echec) {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                throw echec;
            }
        };
    }

    @Test
    void une_table_absente_d_un_schema_allege_vaut_zero_reference() {
        DataAccessException absente = new BadSqlGrammarException("test", "SELECT 1", new SQLException("table absente"));

        assertThat(new UsageDuForfaitParLesFactures(quiEchoue(absente)).nombreDeReferences(CENTRE, FORFAIT)).isZero();
    }

    @Test
    void un_autre_echec_sql_se_propage_au_lieu_de_valoir_zero_reference() {
        assertThatThrownBy(() -> new UsageDuForfaitParLesFactures(quiEchoue(new QueryTimeoutException("délai dépassé")))
                .nombreDeReferences(CENTRE, FORFAIT)).isInstanceOf(QueryTimeoutException.class);
    }
}
