package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Ecart;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Inventaire;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Montant;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Reception;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.SortiesDuJour;
import com.hemodialyse.backend.domain.comptabilite.valueobject.ComptesStock;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.OperationComptable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComptabiliteStockServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID AUTRE_CENTRE = UUID.randomUUID();
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 5);
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 3);

    private final ComptabiliteFixtures.Ecritures ecritures = new ComptabiliteFixtures.Ecritures();
    private final ComptabiliteFixtures.Mappings mappings = new ComptabiliteFixtures.Mappings();
    private final ComptabiliteFixtures.Periodes periodes = new ComptabiliteFixtures.Periodes();
    private final ComptabiliteFixtures.Stock stock = new ComptabiliteFixtures.Stock();
    private final ComptabiliteStockService service = new ComptabiliteStockService(ecritures, mappings, periodes, stock);

    private static Montant montant(String compteStock, String compteCharge, String valeur) {
        return new Montant(compteStock, compteCharge, new BigDecimal(valeur));
    }

    private static BigDecimal solde(EcritureComptable e, String compte) {
        return e.getLignes().stream().filter(l -> l.getCompteSCF().equals(compte))
                .map(l -> l.getMontantDebit().subtract(l.getMontantCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ComptabiliteStockService.Synchronisation synchroniser() {
        return service.synchroniser(CENTRE, JOUR.minusDays(2), JOUR.plusDays(2), AUJOURDHUI);
    }

    @Test
    void a_validated_reception_debits_the_stock_and_credits_invoices_not_received_once() {
        UUID bon = UUID.randomUUID();
        stock.receptions.add(new Reception(bon, "BR-0001", JOUR, List.of(montant(null, null, "1200.00"))));

        var premiere = synchroniser();
        var seconde = synchroniser();

        assertEquals(1, premiere.receptions());
        assertEquals(0, seconde.receptions(), "rejouer ne crée pas de doublon");
        EcritureComptable e = ecritures.du(CENTRE).get(0);
        assertEquals(JournalCode.AC, e.getJournalCode());
        assertEquals(bon, e.getSourceId());
        assertEquals(new BigDecimal("1200.00"), solde(e, "322"));
        assertEquals(new BigDecimal("-1200.00"), solde(e, "408"));
        assertEquals(1, ecritures.du(CENTRE).size());
    }

    @Test
    void the_accounts_of_the_article_override_those_of_the_centre() {
        stock.receptions.add(new Reception(UUID.randomUUID(), "BR-0002", JOUR,
                List.of(montant("321", null, "300.00"), montant(null, null, "100.00"))));
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant("321", "6021", "50.00"), montant(null, null, "20.00"))));

        synchroniser();

        EcritureComptable reception = ecritures.du(CENTRE).get(0);
        assertEquals(new BigDecimal("300.00"), solde(reception, "321"));
        assertEquals(new BigDecimal("100.00"), solde(reception, "322"));
        assertEquals(new BigDecimal("-400.00"), solde(reception, "408"));
        EcritureComptable sorties = ecritures.du(CENTRE).get(1);
        assertEquals(new BigDecimal("50.00"), solde(sorties, "6021"));
        assertEquals(new BigDecimal("20.00"), solde(sorties, "602"));
        assertEquals(new BigDecimal("-50.00"), solde(sorties, "321"));
        assertEquals(new BigDecimal("-20.00"), solde(sorties, "322"));
    }

    @Test
    void the_outputs_of_a_day_are_centralised_in_one_entry_in_the_configured_journal_and_accounts() {
        mappings.save(new MappingComptable(CENTRE, "706", "411100", "411500", "512", "530", "44571", new ComptesStock("32", "6022", "408", "757", "657"),
                Map.of(OperationComptable.STOCK_SORTIE, JournalCode.de("OD"))));
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, null, "80.00"), montant(null, null, "20.00"))));

        var resultat = synchroniser();

        assertEquals(1, resultat.joursSorties());
        EcritureComptable e = ecritures.du(CENTRE).get(0);
        assertEquals(JournalCode.de("OD"), e.getJournalCode());
        assertEquals("OD-2026-000001", e.getNumeroPiece());
        assertEquals(JOUR, e.getDatePiece());
        assertEquals(new BigDecimal("100.00"), solde(e, "6022"));
        assertEquals(new BigDecimal("-100.00"), solde(e, "32"));
        assertEquals(2, e.getLignes().size(), "une ligne par compte");
    }

    @Test
    void a_late_movement_updates_the_entry_of_the_day_while_it_is_still_modifiable() {
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, null, "100.00"))));
        synchroniser();
        EcritureComptable avant = ecritures.du(CENTRE).get(0);

        stock.sorties.clear();
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, null, "140.00"))));
        var resultat = synchroniser();

        assertEquals(1, resultat.joursSorties());
        assertEquals(0, resultat.complements());
        assertEquals(1, ecritures.du(CENTRE).size());
        EcritureComptable apres = ecritures.du(CENTRE).get(0);
        assertEquals(avant.getId(), apres.getId());
        assertEquals(avant.getNumeroPiece(), apres.getNumeroPiece(), "le numéro de pièce est conservé");
        assertEquals(new BigDecimal("140.00"), solde(apres, "602"));
    }

    @Test
    void an_exported_entry_is_never_modified_the_difference_goes_to_a_complement() {
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, null, "100.00"))));
        synchroniser();
        EcritureComptable exportee = ecritures.du(CENTRE).get(0);
        exportee.marquerExportee();

        stock.sorties.clear();
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, null, "130.00"))));
        var resultat = synchroniser();

        assertEquals(1, resultat.complements());
        assertEquals(2, ecritures.du(CENTRE).size());
        assertEquals(new BigDecimal("100.00"), solde(ecritures.du(CENTRE).get(0), "602"), "l'écriture exportée est intacte");
        EcritureComptable complement = ecritures.du(CENTRE).get(1);
        assertEquals(new BigDecimal("30.00"), solde(complement, "602"));
        assertEquals(new BigDecimal("-30.00"), solde(complement, "322"));
        assertTrue(complement.getLibelle().startsWith("Complément"));
        assertNotEquals(exportee.getSourceId(), complement.getSourceId());
        // rejouer n'ajoute rien de plus
        assertEquals(0, synchroniser().complements());
        assertEquals(2, ecritures.du(CENTRE).size());
    }

    @Test
    void a_removed_output_in_a_closed_period_is_reversed_by_a_complement_dated_today() {
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, null, "100.00"))));
        synchroniser();
        periodes.cloturees.add(YearMonth.from(JOUR));

        stock.sorties.clear();
        var resultat = synchroniser();

        assertEquals(1, resultat.complements());
        EcritureComptable extourne = ecritures.du(CENTRE).get(1);
        assertEquals(AUJOURDHUI, extourne.getDateEcriture(), "la période d'origine est clôturée");
        assertEquals(JOUR, extourne.getDatePiece());
        assertEquals(new BigDecimal("-100.00"), solde(extourne, "602"));
        assertEquals(new BigDecimal("100.00"), solde(extourne, "322"));
    }

    @Test
    void all_outputs_removed_from_a_modifiable_day_are_reversed_rather_than_leaving_an_empty_entry() {
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, null, "100.00"))));
        synchroniser();

        stock.sorties.clear();
        var resultat = synchroniser();

        assertEquals(1, resultat.complements());
        var total = ecritures.du(CENTRE).stream().map(e -> solde(e, "602")).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.signum(), "la consommation du jour revient à zéro");
    }

    @Test
    void a_reception_or_an_inventory_in_a_closed_period_is_not_posted_and_reported_as_ignored() {
        periodes.cloturees.add(YearMonth.from(JOUR));
        stock.receptions.add(new Reception(UUID.randomUUID(), "BR-0003", JOUR, List.of(montant(null, null, "10.00"))));
        stock.inventaires.add(new Inventaire(UUID.randomUUID(), "INV-1", JOUR,
                List.of(new Ecart(null, new BigDecimal("5.00"), BigDecimal.ZERO))));

        var resultat = synchroniser();

        assertEquals(2, resultat.ignorees());
        assertTrue(ecritures.du(CENTRE).isEmpty());
    }

    @Test
    void inventory_gaps_post_surpluses_as_income_and_shortages_as_expense() {
        UUID inventaire = UUID.randomUUID();
        stock.inventaires.add(new Inventaire(inventaire, "INV-2026-001", JOUR, List.of(
                new Ecart(null, new BigDecimal("40.00"), new BigDecimal("15.00")),
                new Ecart("321", BigDecimal.ZERO, new BigDecimal("10.00")))));

        var resultat = synchroniser();

        assertEquals(1, resultat.inventaires());
        EcritureComptable e = ecritures.du(CENTRE).get(0);
        assertEquals(JournalCode.ST, e.getJournalCode());
        assertEquals(inventaire, e.getSourceId());
        assertEquals(new BigDecimal("25.00"), solde(e, "322"));
        assertEquals(new BigDecimal("-10.00"), solde(e, "321"));
        assertEquals(new BigDecimal("-40.00"), solde(e, "757"));
        assertEquals(new BigDecimal("25.00"), solde(e, "657"));
        assertEquals(0, synchroniser().inventaires());
    }

    @Test
    void nothing_is_posted_for_a_day_without_movement_and_another_centre_is_never_touched() {
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, null, "100.00"))));

        var resultat = service.synchroniser(AUTRE_CENTRE, JOUR.minusDays(10), JOUR.minusDays(5), AUJOURDHUI);

        assertEquals(0, resultat.joursSorties());
        assertTrue(ecritures.parId.isEmpty());
        synchroniser();
        assertTrue(ecritures.du(AUTRE_CENTRE).isEmpty());
        assertEquals(1, ecritures.du(CENTRE).size());
    }

    @Test
    void every_generated_entry_is_balanced() {
        stock.receptions.add(new Reception(UUID.randomUUID(), "BR-9", JOUR,
                List.of(montant(null, null, "33.335"), montant("321", null, "0.005"))));
        stock.sorties.add(new SortiesDuJour(JOUR, List.of(montant(null, "6021", "12.345"), montant("321", null, "7.004"))));

        synchroniser();

        for (EcritureComptable e : ecritures.du(CENTRE)) {
            BigDecimal debit = e.getLignes().stream().map(LigneEcriture::getMontantDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = e.getLignes().stream().map(LigneEcriture::getMontantCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, debit.compareTo(credit), e.getLibelle());
        }
    }
}
