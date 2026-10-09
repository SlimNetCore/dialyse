package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.SensEcriture;
import com.hemodialyse.backend.domain.comptabilite.valueobject.ComptesStock;
import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.OperationComptable;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournauxServiceTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID AUTRE_CENTRE = UUID.randomUUID();
    private static final JournalCode OD = JournalCode.de("OD");

    private final ComptabiliteFixtures.Monde monde = new ComptabiliteFixtures.Monde();
    private final ComptabiliteFixtures.Ecritures ecritures = monde.ecritures;
    private final ComptabiliteFixtures.Journaux journaux = monde.journaux;
    private final JournauxService service = monde.journauxService;
    private final ComptabiliteService comptabilite = monde.comptabilite;

    private static MappingComptable mapping(UUID centre, Map<OperationComptable, JournalCode> choix) {
        return new MappingComptable(centre, "706", "411100", "411500", "512", "530", "44571",
                ComptesStock.parDefaut(), choix);
    }

    @Test
    void a_journal_used_by_a_piece_model_cannot_be_deactivated_nor_deleted() {
        service.enregistrer(CENTRE, new Journal(OD, "Opérations diverses", true));
        monde.pieces.enregistrerModele(new ModelePiece(UUID.randomUUID(), CENTRE, "LOYER", "Loyer", OD, true, List.of(
                new ModelePiece.Ligne(SensEcriture.DEBIT, "613", null),
                new ModelePiece.Ligne(SensEcriture.CREDIT, "512", null))));

        assertEquals("JOURNAL_UTILISE", code(assertThrows(BusinessException.class, () -> service.supprimer(CENTRE, OD))));
        assertEquals("JOURNAL_UTILISE", code(assertThrows(BusinessException.class,
                () -> service.enregistrer(CENTRE, new Journal(OD, "Opérations diverses", false)))));
    }

    private static String code(BusinessException e) {
        return e.getCode();
    }

    @Test
    void a_centre_that_configured_nothing_sees_the_default_journals() {
        List<Journal> liste = service.lister(CENTRE);

        assertEquals(List.of("VE", "BQ", "CA", "AC", "ST"), liste.stream().map(j -> j.code().valeur()).toList());
        assertTrue(liste.stream().allMatch(Journal::actif));
        assertTrue(journaux.parCentre.isEmpty(), "consulter n'écrit rien");
    }

    @Test
    void the_first_change_materialises_the_defaults_so_the_centre_keeps_a_full_list() {
        service.enregistrer(CENTRE, new Journal(OD, "Opérations diverses", true));

        assertEquals(6, service.lister(CENTRE).size());
        assertTrue(service.lister(AUTRE_CENTRE).stream().noneMatch(j -> j.code().equals(OD)),
                "le journal d'un centre n'apparaît pas dans un autre");
    }

    @Test
    void a_journal_code_is_normalised_and_validated() {
        assertEquals("OD", JournalCode.de(" od ").valeur());
        assertThrows(IllegalArgumentException.class, () -> JournalCode.de("O-D"));
        assertThrows(IllegalArgumentException.class, () -> JournalCode.de("ABCDEFGHIJK"));
        assertThrows(IllegalArgumentException.class, () -> new Journal(OD, " ", true));
    }

    @Test
    void a_journal_chosen_for_an_operation_cannot_be_deactivated_nor_deleted() {
        var desactivation = assertThrows(BusinessException.class,
                () -> service.enregistrer(CENTRE, new Journal(JournalCode.VE, "Ventes", false)));
        var suppression = assertThrows(BusinessException.class, () -> service.supprimer(CENTRE, JournalCode.ST));

        assertEquals("JOURNAL_UTILISE", code(desactivation));
        assertEquals("JOURNAL_UTILISE", code(suppression));
    }

    @Test
    void a_journal_released_from_its_operations_can_be_deleted_unless_it_holds_entries() {
        service.enregistrer(CENTRE, new Journal(OD, "Opérations diverses", true));
        comptabilite.saveMappingComptable(mapping(CENTRE, Map.of(OperationComptable.VENTE, OD)));

        service.supprimer(CENTRE, JournalCode.VE);
        assertTrue(service.lister(CENTRE).stream().noneMatch(j -> j.code().equals(JournalCode.VE)));

        EcritureComptable ecriture = comptabilite.genererEcritureFacturation(
                new ComptabiliteUseCase.GenererEcritureFacturationCommand(CENTRE, UUID.randomUUID(), "FACT-1",
                        UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), BigDecimal.ZERO,
                        new BigDecimal("100.00"), LocalDate.of(2026, 10, 5), "Séance"));
        assertEquals(OD, ecriture.getJournalCode(), "la vente s'écrit dans le journal choisi");
        assertEquals("OD-2026-000001", ecriture.getNumeroPiece());

        comptabilite.saveMappingComptable(mapping(CENTRE, Map.of(OperationComptable.VENTE, JournalCode.BQ)));
        var refus = assertThrows(BusinessException.class, () -> service.supprimer(CENTRE, OD));
        assertEquals("JOURNAL_AVEC_ECRITURES", code(refus));
        // il se désactive à la place
        assertFalse(service.enregistrer(CENTRE, new Journal(OD, "Opérations diverses", false)).actif());
    }

    @Test
    void deleting_an_unknown_journal_is_refused() {
        var refus = assertThrows(BusinessException.class, () -> service.supprimer(CENTRE, JournalCode.de("ZZ")));

        assertEquals("JOURNAL_INTROUVABLE", code(refus));
    }

    @Test
    void an_operation_can_only_be_assigned_to_an_existing_active_journal() {
        var inconnu = assertThrows(BusinessException.class, () -> comptabilite.saveMappingComptable(
                mapping(CENTRE, Map.of(OperationComptable.STOCK_SORTIE, OD))));
        assertEquals("JOURNAL_INCONNU", code(inconnu));

        // désactivé chez ce centre, actif (par défaut) chez l'autre
        comptabilite.saveMappingComptable(mapping(CENTRE, Map.of(OperationComptable.REGLEMENT_CAISSE, JournalCode.BQ)));
        service.enregistrer(CENTRE, new Journal(JournalCode.CA, "Caisse", false));
        var desactive = assertThrows(BusinessException.class, () -> comptabilite.saveMappingComptable(
                mapping(CENTRE, Map.of())));
        assertEquals("JOURNAL_INCONNU", code(desactive));
        comptabilite.saveMappingComptable(mapping(AUTRE_CENTRE, Map.of()));
    }

    @Test
    void changing_the_journal_of_an_operation_never_duplicates_an_entry_already_posted() {
        UUID facture = UUID.randomUUID();
        var commande = new ComptabiliteUseCase.GenererEcritureFacturationCommand(CENTRE, facture, "FACT-2",
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("100.00"), LocalDate.of(2026, 10, 5), "Séance");
        EcritureComptable premiere = comptabilite.genererEcritureFacturation(commande);

        service.enregistrer(CENTRE, new Journal(OD, "Opérations diverses", true));
        comptabilite.saveMappingComptable(mapping(CENTRE, Map.of(OperationComptable.VENTE, OD)));
        EcritureComptable rejouee = comptabilite.genererEcritureFacturation(commande);

        assertEquals(premiere.getId(), rejouee.getId());
        assertEquals(1, ecritures.du(CENTRE).size());
    }
}
