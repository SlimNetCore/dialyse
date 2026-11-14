package com.hemodialyse.backend.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S-DDL1 : {@code db/schema.sql} décrit à lui seul les tables de l'Organisation (societes, centers), de la licence et de l'identité
 * (app_user, app_role, rattachements), dans l'ordre exigé par les clés étrangères, avec les colonnes de Flyway V1 + V12 + V46 + V56.
 * Le script est rejoué sur une base H2 VIERGE (sans Hibernate) : ce qu'il crée est comparé à la signature attendue (colonne, type,
 * largeur, nullité, valeur par défaut) puis aux contraintes de la production.
 */
class SchemaH2DeLIdentiteEtDeLOrganisationTest {

    private static final List<String> TABLES = List.of(
            "societes", "centers", "license", "app_role", "app_user", "app_user_role", "app_user_center");

    /** Colonnes attendues (Flyway V1 + V12 + V46 + V56), une ligne par colonne : nom|type|largeur|nullable|défaut. */
    private static final List<String> ATTENDU = List.of(
            "societes|id|UUID||NO|", "societes|actif|BOOLEAN||NO|", "societes|adresse|CHARACTER VARYING|250|YES|",
            "societes|code|CHARACTER VARYING|30|NO|", "societes|created_at|TIMESTAMP WITH TIME ZONE||NO|",
            "societes|email|CHARACTER VARYING|150|YES|", "societes|logo|BINARY VARYING|1000000000|YES|",
            "societes|logo_content_type|CHARACTER VARYING|30|YES|", "societes|nif|CHARACTER VARYING|40|YES|",
            "societes|nis|CHARACTER VARYING|40|YES|", "societes|pied_page|CHARACTER VARYING|500|YES|",
            "societes|raison_sociale|CHARACTER VARYING|200|NO|", "societes|rc|CHARACTER VARYING|40|YES|",
            "societes|site_web|CHARACTER VARYING|200|YES|", "societes|telephone|CHARACTER VARYING|30|YES|",
            "societes|ville|CHARACTER VARYING|100|YES|", "societes|wilaya|CHARACTER VARYING|100|YES|",
            "centers|id|UUID||NO|", "centers|actif|BOOLEAN||NO|TRUE", "centers|adresse|CHARACTER VARYING|250|YES|",
            "centers|code|CHARACTER VARYING|255|YES|", "centers|email|CHARACTER VARYING|150|YES|",
            "centers|fuseau_horaire|CHARACTER VARYING|50|YES|", "centers|name|CHARACTER VARYING|255|YES|",
            "centers|site_web|CHARACTER VARYING|200|YES|", "centers|societe_id|UUID||NO|",
            "centers|telephone|CHARACTER VARYING|30|YES|", "centers|ville|CHARACTER VARYING|100|YES|",
            "centers|wilaya|CHARACTER VARYING|100|YES|",
            "license|id|UUID||NO|", "license|activated_at|TIMESTAMP WITH TIME ZONE||YES|", "license|center_id|UUID||NO|",
            "license|created_at|TIMESTAMP WITH TIME ZONE||NO|", "license|created_by|UUID||YES|",
            "license|jti|CHARACTER VARYING|255|NO|", "license|last_online_check_at|TIMESTAMP WITH TIME ZONE||YES|",
            "license|license_key|CHARACTER VARYING|1000000000|NO|", "license|max_users|INTEGER||NO|",
            "license|revoked_reason|CHARACTER VARYING|255|YES|", "license|status|CHARACTER VARYING|255|NO|",
            "license|type|CHARACTER VARYING|255|NO|", "license|valid_from|TIMESTAMP WITH TIME ZONE||NO|",
            "license|valid_until|TIMESTAMP WITH TIME ZONE||NO|",
            "app_role|id|UUID||NO|", "app_role|code|CHARACTER VARYING|255|NO|",
            "app_role|description|CHARACTER VARYING|255|YES|", "app_role|name|CHARACTER VARYING|255|NO|",
            "app_user|id|UUID||NO|", "app_user|active|BOOLEAN||NO|", "app_user|created_at|TIMESTAMP WITH TIME ZONE||YES|",
            "app_user|email|CHARACTER VARYING|255|YES|", "app_user|full_name|CHARACTER VARYING|255|YES|",
            "app_user|must_change_password|BOOLEAN||NO|FALSE", "app_user|password_hash|CHARACTER VARYING|255|NO|",
            "app_user|username|CHARACTER VARYING|255|NO|",
            "app_user_role|user_id|UUID||NO|", "app_user_role|role_id|UUID||NO|",
            "app_user_center|user_id|UUID||NO|", "app_user_center|center_id|UUID||NO|");

    private static Connection baseViergeAvecLeSchema() throws Exception {
        Connection base = DriverManager.getConnection(
                "jdbc:h2:mem:ddl_idn_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        ScriptUtils.executeSqlScript(base, new ClassPathResource("db/schema.sql"));
        return base;
    }

    private static List<String> signature(Connection base) throws SQLException {
        List<String> lignes = new ArrayList<>();
        try (var st = base.prepareStatement(
                "SELECT LOWER(TABLE_NAME), LOWER(COLUMN_NAME), DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT "
                        + "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND LOWER(TABLE_NAME) IN ("
                        + String.join(",", TABLES.stream().map(t -> "'" + t + "'").toList()) + ") ORDER BY 1, ORDINAL_POSITION");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lignes.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|"
                        + (rs.getString(4) == null || "UUID".equals(rs.getString(3)) ? "" : rs.getString(4)) + "|"
                        + rs.getString(5) + "|" + (rs.getString(6) == null ? "" : rs.getString(6)));
            }
        }
        return lignes;
    }

    @Test
    void le_schema_h2_cree_seul_les_tables_de_l_identite_et_de_l_organisation_comme_flyway() throws Exception {
        try (Connection base = baseViergeAvecLeSchema()) {
            assertThat(signature(base)).containsExactlyInAnyOrderElementsOf(ATTENDU);
        }
    }

    @Test
    void l_ordre_des_cles_etrangeres_et_les_contraintes_sont_ceux_de_la_production() throws Exception {
        try (Connection base = baseViergeAvecLeSchema()) {
            var st = base.createStatement();
            st.execute("INSERT INTO societes (id, actif, code, created_at, raison_sociale) VALUES "
                    + "('00000000-0000-0000-0000-000000000001', TRUE, 'S1', CURRENT_TIMESTAMP, 'Societe')");
            st.execute("INSERT INTO centers (id, code, name, societe_id) VALUES "
                    + "('00000000-0000-0000-0000-000000000011', 'C1', 'Centre', '00000000-0000-0000-0000-000000000001')");
            st.execute("INSERT INTO app_role (id, code, name) VALUES ('00000000-0000-0000-0000-000000000021', 'R', 'Role')");
            st.execute("INSERT INTO app_user (id, active, password_hash, username) VALUES "
                    + "('00000000-0000-0000-0000-000000000031', TRUE, 'h', 'doublon')");
            // Aucune unicité sur app_user.username ni sur app_role.code (KB-PLN-24).
            st.execute("INSERT INTO app_user (id, active, password_hash, username) VALUES "
                    + "('00000000-0000-0000-0000-000000000032', TRUE, 'h', 'doublon')");
            st.execute("INSERT INTO app_role (id, code, name) VALUES ('00000000-0000-0000-0000-000000000022', 'R', 'Role')");
            st.execute("INSERT INTO app_user_center (user_id, center_id) VALUES "
                    + "('00000000-0000-0000-0000-000000000031', '00000000-0000-0000-0000-000000000011')");
            st.execute("INSERT INTO app_user_role (user_id, role_id) VALUES "
                    + "('00000000-0000-0000-0000-000000000031', '00000000-0000-0000-0000-000000000021')");
            // Valeurs par défaut de la production : centre actif, mot de passe non temporaire.
            try (ResultSet rs = st.executeQuery("SELECT actif FROM centers")) {
                assertThat(rs.next() && rs.getBoolean(1)).isTrue();
            }
            try (ResultSet rs = st.executeQuery("SELECT must_change_password FROM app_user LIMIT 1")) {
                assertThat(rs.next() && !rs.getBoolean(1)).isTrue();
            }
            // Clés étrangères des rattachements.
            assertThatThrownBy(() -> st.execute("INSERT INTO app_user_center (user_id, center_id) VALUES "
                    + "('00000000-0000-0000-0000-000000000031', '00000000-0000-0000-0000-0000000000ff')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.execute("INSERT INTO app_user_role (user_id, role_id) VALUES "
                    + "('00000000-0000-0000-0000-0000000000ff', '00000000-0000-0000-0000-000000000021')"))
                    .isInstanceOf(SQLException.class);
            // Unicité du code de société et du jti de licence.
            assertThatThrownBy(() -> st.execute("INSERT INTO societes (id, actif, code, created_at, raison_sociale) VALUES "
                    + "('00000000-0000-0000-0000-000000000002', TRUE, 'S1', CURRENT_TIMESTAMP, 'Autre')"))
                    .isInstanceOf(SQLException.class);
            String licence = "INSERT INTO license (id, center_id, created_at, jti, license_key, max_users, status, type, valid_from, "
                    + "valid_until) VALUES ('%s', '00000000-0000-0000-0000-000000000011', CURRENT_TIMESTAMP, 'J', 'k', 1, 'ACTIVE', "
                    + "'STANDARD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            st.execute(licence.formatted("00000000-0000-0000-0000-000000000041"));
            assertThatThrownBy(() -> st.execute(licence.formatted("00000000-0000-0000-0000-000000000042")))
                    .isInstanceOf(SQLException.class);
        }
    }

}
