package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revue globale du 2026-11-11 (constat 2) : l'état d'origine d'un assuré (mémento URL-encodé de neuf champs, ADR 0134) doit tenir dans
 * {@code migration_id_map.etat_origine} même au pire cas du schéma {@code assure} : huit champs texte de 255 caractères, chacun de
 * trois octets en UTF-8 (donc neuf caractères une fois encodé). Une colonne bornée à 4000 ferait échouer l'import en écriture.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class IdMappingEtatOrigineMaximalIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private static String pireMemento() {
        String champ = "€".repeat(255);
        StringBuilder memento = new StringBuilder("v=1&dateNaissance=1970-01-01");
        for (String cle : List.of("nom", "prenom", "sexe", "telPersonnel", "telMobile", "telBureau", "adresse", "groupeSanguin")) {
            memento.append('&').append(cle).append('=').append(URLEncoder.encode(champ, StandardCharsets.UTF_8));
        }
        return memento.toString();
    }

    @Test
    void le_memento_du_pire_cas_du_schema_est_memorise_puis_relu_a_l_identique() {
        IdMappingJdbcAdapter adapter = new IdMappingJdbcAdapter(jdbc);
        CentreId centre = new CentreId(UUID.randomUUID());
        UUID lot = UUID.randomUUID();
        String memento = pireMemento();
        assertThat(memento.length()).as("le pire cas dépasse l'ancienne borne de 4000").isGreaterThan(4000);

        adapter.save(centre, lot, new IdMapping(MigrationEntity.ASSURES, "A1", "ZR1", IdMapping.Operation.UPDATED, memento));

        assertThat(adapter.findByBatch(centre, lot)).singleElement().satisfies(m -> assertThat(m.etatOrigine()).isEqualTo(memento));
    }
}
