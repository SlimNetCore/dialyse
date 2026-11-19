package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.bons.ConsulterLesBonsParPage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonDeCommande;
import com.hemodialyse.backend.contexts.stock.domain.commandes.StatutDuBon;
import com.hemodialyse.backend.contexts.stock.domain.reception.BonDeReception;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pages des bons en SQL sur H2 (mode PostgreSQL, schéma réel du contexte) : total filtré, {@code LIMIT/OFFSET}, tri par
 * table fixe, filtres référence/statut, isolation du centre, lignes rattachées ; statut historique {@code RECU} d'un bon de
 * réception lu, filtré et trié comme {@code VALIDE} (KB-STK-58).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stock-pages-bons;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Sql("classpath:db/h2/stock-schema.sql")
class BonsParPageJdbcTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000a001"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000b002"));
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbc;
    private ConsulterLesBonsParPage consulter;

    @BeforeEach
    void avant() {
        consulter = new ConsulterLesBonsParPage(new BonsParPageJdbc(jdbc));
    }

    private UUID commande(CentreId centre, String reference, String statut, int heure, int lignes) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_commande (id, center_id, reference, statut, created_by, created_at) VALUES (?, ?, ?, ?, 'u', ?)",
                id, centre.valeur(), reference, statut, T0.plusHours(heure));
        for (int i = 0; i < lignes; i++) {
            jdbc.update("INSERT INTO bons_commande_lignes (id, bon_commande_id, article_id, quantite, prix_unitaire) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), id, UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN);
        }
        return id;
    }

    private UUID reception(CentreId centre, String reference, String statut, int heure, int lignes) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, date_reception, statut, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, 'u', ?)", id, centre.valeur(), reference, LocalDate.of(2026, 1, 1).plusDays(heure),
                statut, T0.plusHours(heure));
        for (int i = 0; i < lignes; i++) {
            jdbc.update("INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire, numero_lot) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), id, UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN, "L" + i);
        }
        return id;
    }

    private UUID sortie(CentreId centre, String reference, int heure) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_sortie (id, center_id, reference, seance_id, poste, date_sortie, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, 'P1', ?, 'u', ?)", id, centre.valeur(), reference, UUID.randomUUID(),
                LocalDate.of(2026, 3, 1).minusDays(heure), T0.plusHours(heure));
        jdbc.update("INSERT INTO bons_sortie_lignes (id, bon_sortie_id, article_id, quantite) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), id, UUID.randomUUID(), BigDecimal.ONE);
        return id;
    }

    private static <T> List<String> references(PageDeResultats<T> page, java.util.function.Function<T, String> reference) {
        return page.items().stream().map(reference).toList();
    }

    @Test
    void bons_de_commande_pagines_en_sql_les_plus_recents_d_abord_avec_leurs_lignes() {
        commande(CENTRE, "BC-00001", "BROUILLON", 1, 2);
        commande(CENTRE, "BC-00002", "VALIDE", 2, 1);
        commande(CENTRE, "BC-00003", "RECU", 3, 3);
        commande(AUTRE, "BC-00009", "VALIDE", 9, 1);

        PageDeResultats<BonDeCommande> premiere = consulter.bonsDeCommande(CENTRE, new ParametresDePage(0, 2, null), null, null);
        assertEquals(3, premiere.total());
        assertEquals(List.of("BC-00003", "BC-00002"), references(premiere, BonDeCommande::reference));
        assertEquals(3, premiere.items().get(0).lignes().size());

        PageDeResultats<BonDeCommande> seconde = consulter.bonsDeCommande(CENTRE, new ParametresDePage(1, 2, null), null, null);
        assertEquals(List.of("BC-00001"), references(seconde, BonDeCommande::reference));
        assertEquals(List.of(), consulter.bonsDeCommande(CENTRE, new ParametresDePage(5, 2, null), null, null).items());
    }

    @Test
    void bons_de_commande_tries_et_filtres() {
        commande(CENTRE, "BC-00001", "RECU", 1, 2);
        commande(CENTRE, "BC-00002", "BROUILLON", 2, 1);
        commande(CENTRE, "XY-00003", "VALIDE", 3, 3);

        assertEquals(List.of("BC-00002", "XY-00003", "BC-00001"), references(consulter.bonsDeCommande(CENTRE,
                new ParametresDePage(0, 20, "statut"), null, null), BonDeCommande::reference));
        assertEquals(List.of("XY-00003", "BC-00001", "BC-00002"), references(consulter.bonsDeCommande(CENTRE,
                new ParametresDePage(0, 20, "lignes,desc"), null, null), BonDeCommande::reference));
        PageDeResultats<BonDeCommande> filtree = consulter.bonsDeCommande(CENTRE, new ParametresDePage(0, 20, "reference"),
                "bc-", "RECU,BROUILLON");
        assertEquals(2, filtree.total());
        assertEquals(List.of(StatutDuBon.RECU, StatutDuBon.BROUILLON), filtree.items().stream().map(BonDeCommande::statut).toList());
    }

    @Test
    void la_reference_filtree_echappe_les_jokers() {
        commande(CENTRE, "BC_1", "VALIDE", 1, 0);
        commande(CENTRE, "BCX1", "VALIDE", 2, 0);
        assertEquals(List.of("BC_1"), references(consulter.bonsDeCommande(CENTRE, new ParametresDePage(0, 20, null), "c_", null),
                BonDeCommande::reference));
    }

    @Test
    void reception_au_statut_historique_recu_lue_filtree_et_triee_comme_validee() {
        reception(CENTRE, "BR-00001", "BROUILLON", 1, 1);
        UUID recu = reception(CENTRE, "BR-ANN-0001", "RECU", 2, 2);
        reception(CENTRE, "BR-00003", "VALIDE", 3, 0);
        reception(AUTRE, "BR-00009", "RECU", 4, 0);

        PageDeResultats<BonDeReception> toutes = consulter.bonsDeReception(CENTRE, new ParametresDePage(0, 20, null), null, null);
        assertEquals(3, toutes.total());
        BonDeReception lu = toutes.items().stream().filter(b -> b.id().equals(recu)).findFirst().orElseThrow();
        assertEquals(BonDeReception.Statut.VALIDE, lu.statut());
        assertEquals(2, lu.lignes().size());

        PageDeResultats<BonDeReception> validees = consulter.bonsDeReception(CENTRE, new ParametresDePage(0, 20, "dateReception"),
                null, "VALIDE");
        assertEquals(List.of("BR-ANN-0001", "BR-00003"), references(validees, BonDeReception::reference));
        assertEquals(List.of("BR-00001", "BR-ANN-0001", "BR-00003"), references(consulter.bonsDeReception(CENTRE,
                new ParametresDePage(0, 20, "statut"), null, null), BonDeReception::reference));
        assertEquals(List.of("BR-ANN-0001"), references(consulter.bonsDeReception(CENTRE, new ParametresDePage(0, 20, null),
                "ann", null), BonDeReception::reference));
    }

    @Test
    void bons_de_sortie_pagines_tries_par_date_et_filtres_par_reference() {
        sortie(CENTRE, "BS-00001", 1);
        sortie(CENTRE, "BS-00002", 2);
        sortie(CENTRE, "BS-00010", 3);
        sortie(AUTRE, "BS-00099", 4);

        PageDeResultats<BonDeSortie> parDate = consulter.bonsDeSortie(CENTRE, new ParametresDePage(0, 2, "dateSortie"), null);
        assertEquals(3, parDate.total());
        assertEquals(List.of("BS-00010", "BS-00002"), references(parDate, BonDeSortie::reference));
        assertEquals(1, parDate.items().get(0).lignes().size());
        assertEquals(List.of("BS-00010"), references(consulter.bonsDeSortie(CENTRE, new ParametresDePage(0, 20, null), "0010"),
                BonDeSortie::reference));
    }
}
