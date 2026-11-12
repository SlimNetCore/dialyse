package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.doubles.PilotageEnMemoire.Facture;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.LectureDeLaSyntheseJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.LectureDuTableauDeBordJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SyntheseFinanciereDesCentresJdbc;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contrat des lectures de pilotage exécuté contre les adaptateurs JDBC. Les variantes H2 et PostgreSQL 16 ne diffèrent que
 * par leur base ; les lignes de test sont semées dans les tables du schéma figé et supprimées avant chaque test.
 */
public abstract class PilotageJdbcContrat extends PilotageContrat {

    private int rang;

    protected abstract JdbcTemplate jdbc();

    protected void viderLesLignesDeTest() {
        rang = 0;
        for (String table : new String[]{"facture_reglements", "seances", "factures", "agence", "caisse_assurance"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
        }
    }

    @Override
    protected LectureDuTableauDeBord tableau() {
        return new LectureDuTableauDeBordJdbc(jdbc(), new SeancesDesFacturesSurLaTable(jdbc()));
    }

    @Override
    protected LectureDeLaSynthese synthese() {
        // l'annuaire des payeurs (PL8) : la chaîne agence, caisse du centre, lue sur les tables de test, bornée au centre
        return new LectureDeLaSyntheseJdbc(jdbc(), new SeancesDesFacturesSurLaTable(jdbc()), centre -> {
            java.util.Map<UUID, com.hemodialyse.backend.contexts.facturation.application.pilotage.CaissesDesAgences.Caisse> caisses =
                    new java.util.HashMap<>();
            jdbc().query("SELECT a.id, ca.code, ca.nom FROM agence a JOIN caisse_assurance ca ON ca.id = a.caisse_id "
                    + "AND ca.center_id = a.center_id WHERE a.center_id = ?", rs -> {
                caisses.put(rs.getObject("id", UUID.class), new com.hemodialyse.backend.contexts.facturation.application.pilotage
                        .CaissesDesAgences.Caisse(rs.getString("code"), rs.getString("nom")));
            }, centre);
            return caisses;
        });
    }

    @Override
    protected SyntheseFinanciereDesCentres finance() {
        return new SyntheseFinanciereDesCentresJdbc(jdbc());
    }

    private static UUID derive(String cle) {
        return UUID.nameUUIDFromBytes(cle.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void poserLaFacture(Facture f) {
        UUID agence = null;
        if (f.codeCaisse() != null) {
            UUID caisse = derive("caisse:" + f.centre() + ":" + f.codeCaisse());
            agence = derive("agence:" + f.centre() + ":" + f.codeCaisse());
            if (jdbc().queryForObject("SELECT COUNT(*) FROM caisse_assurance WHERE id = ?", Integer.class, caisse) == 0) {
                jdbc().update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", caisse, f.centre(),
                        f.codeCaisse(), f.nomCaisse());
                jdbc().update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", agence, f.centre(),
                        caisse, "AG-" + f.codeCaisse(), "Agence " + f.codeCaisse());
            }
        }
        rang++;
        Date jour = Date.valueOf(f.date());
        jdbc().update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_status_snapshot, agence_id_snapshot, "
                        + "period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                f.id(), f.centre(), f.patient(), "FAC-F5-" + rang + "-" + f.id(), f.situation(), agence, jour, jour, jour, f.ht(),
                f.tva(), f.ttc());
    }

    @Override
    protected void poserLaSeance(UUID centre, UUID facture, LocalDate date, String statut) {
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, facture_id) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre, PATIENT_1, Date.valueOf(date), statut, facture);
    }

    private void ligne(UUID facture, String forfait, String ht) {
        jdbc().update("INSERT INTO facture_lignes (id, facture_id, center_id, forfait_label, unit_price_ht, seance_count, line_ht) "
                + "VALUES (?, ?, ?, ?, ?, 1, ?)", UUID.randomUUID(), facture, CENTRE_A.valeur(), forfait, new BigDecimal(ht), new BigDecimal(ht));
    }

    /** PL8 : le tableau forfait x caisse est trié en Java par ordre des caractères (même ordre sous H2 et PostgreSQL, quelle que soit la collation). */
    @Test
    void should_build_the_forfait_by_fund_table_in_character_order_with_the_share_of_the_recorded_ttc() {
        UUID f1 = facture(CENTRE_A, PATIENT_1, "2026-02-10", "100.00", "19.00", "119.00", "cnas ", "CNAS Centre", null);
        UUID f2 = facture(CENTRE_A, PATIENT_2, "2026-02-11", "0.00", "0.00", "5.00", null, null, null);
        ligne(f1, "forfait 01", "60.00");
        ligne(f1, "Forfait 01", "40.00");
        ligne(f1, "FORFAIT 01", "0.00");
        ligne(f2, "Gratuit", "0.00");
        ligne(f2, "Gratuit 2", "0.00");

        var lignes = synthese().de(CENTRE_A, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).lignes();

        assertThat(lignes).extracting(l -> l.forfait() + "|" + l.caisse())
                .containsExactly("FORFAIT 01|CNAS", "Forfait 01|CNAS", "Gratuit|INCONNU", "Gratuit 2|INCONNU", "forfait 01|CNAS");
        assertThat(lignes).filteredOn(l -> l.forfait().equals("forfait 01")).singleElement()
                .satisfies(l -> assertThat(l.ttc()).isEqualByComparingTo("71.40"));
        assertThat(lignes).filteredOn(l -> l.forfait().startsWith("Gratuit")).allSatisfy(l -> assertThat(l.ttc()).isEqualByComparingTo("2.50"));
    }

    @Test
    void should_put_in_the_empty_fund_an_invoice_whose_agency_snapshot_belongs_to_another_centre_kb_sea_84() {
        facture(CENTRE_B, PATIENT_3, "2026-02-05", "10.00", "0.00", "10.00", "CNAS", "CNAS du centre B", null);
        UUID agenceDuCentreB = derive("agence:" + CENTRE_B.valeur() + ":CNAS");
        facture(CENTRE_A, PATIENT_1, "2026-02-10", "100.00", "0.00", "100.00", "CNAS", "CNAS du centre A", null);
        UUID etrangere = UUID.randomUUID();
        jdbc().update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_status_snapshot, agence_id_snapshot, "
                        + "period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0, ?)",
                etrangere, CENTRE_A.valeur(), PATIENT_2, "FAC-F5-X-" + etrangere, null, agenceDuCentreB, Date.valueOf("2026-02-11"),
                Date.valueOf("2026-02-11"), Date.valueOf("2026-02-11"), new BigDecimal("40.00"), new BigDecimal("40.00"));

        var caisses = finance().chiffreDAffairesParCaisse(java.util.List.of(CENTRE_A.valeur()), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28));

        assertThat(caisses).hasSize(2);
        assertThat(caisses).filteredOn(c -> c.codeCaisse().equals("CNAS")).singleElement()
                .satisfies(c -> assertThat(c.chiffreDAffairesHt()).isEqualByComparingTo("100.00"));
        assertThat(caisses).filteredOn(c -> c.codeCaisse().isEmpty()).singleElement()
                .satisfies(c -> assertThat(c.chiffreDAffairesHt()).isEqualByComparingTo("40.00"));
    }

    @Override
    protected void poserLeReglement(UUID centre, UUID facture, String montant) {
        jdbc().update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), facture, centre, new BigDecimal(montant), Date.valueOf(LocalDate.of(2026, 4, 1)));
    }
}
