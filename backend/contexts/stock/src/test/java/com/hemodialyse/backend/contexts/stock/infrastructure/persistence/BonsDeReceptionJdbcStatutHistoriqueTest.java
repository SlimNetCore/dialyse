package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Régression KB-STK-58 : le jeu d'amorçage H2 contient des bons de réception au statut {@code RECU} (vocabulaire des bons de
 * commande) ; leur lecture faisait {@code Statut.valueOf("RECU")} et toute la liste du centre tombait en 400. Ils se lisent
 * désormais {@code VALIDE} (liste et consultation) ; un statut réellement inconnu reste une erreur explicite.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stock-reception-recu;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Sql("classpath:db/h2/stock-schema.sql")
class BonsDeReceptionJdbcStatutHistoriqueTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000a001"));

    @Autowired
    private JdbcTemplate jdbc;

    private UUID bon(String reference, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, date_reception, statut, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'seed-perf', ?)", id, CENTRE.valeur(), reference, LocalDate.of(2022, 1, 5), statut,
                OffsetDateTime.of(2022, 1, 5, 8, 0, 0, 0, ZoneOffset.UTC));
        return id;
    }

    private BonsDeReceptionJdbc bons() {
        return new BonsDeReceptionJdbc(jdbc, null, null);
    }

    @Test
    void un_bon_au_statut_historique_recu_est_lu_valide_en_liste_et_en_consultation() {
        UUID recu = bon("BR-ANN-PF-0001", "RECU");
        bon("BR-00002", "BROUILLON");

        List<BonDeReception> liste = bons().tous(CENTRE);
        assertEquals(2, liste.size());
        assertEquals(BonDeReception.Statut.VALIDE,
                liste.stream().filter(b -> b.id().equals(recu)).findFirst().orElseThrow().statut());
        assertEquals(BonDeReception.Statut.VALIDE, bons().parId(CENTRE, recu).orElseThrow().statut());
        assertEquals("RECU", jdbc.queryForObject("SELECT statut FROM bons_reception WHERE id = ?", String.class, recu));
    }

    @Test
    void un_statut_inconnu_reste_une_erreur_explicite() {
        UUID inconnu = bon("BR-00003", "ANNULE");
        IllegalStateException erreur = assertThrows(IllegalStateException.class, () -> bons().parId(CENTRE, inconnu));
        assertTrue(erreur.getMessage().contains("ANNULE") && erreur.getMessage().contains(inconnu.toString()));
    }
}
