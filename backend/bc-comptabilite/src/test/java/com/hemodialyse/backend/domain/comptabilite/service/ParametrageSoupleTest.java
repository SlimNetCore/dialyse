package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.GenererEcritureFacturationCommand;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase.GenererEcritureReglementCommand;
import com.hemodialyse.backend.domain.comptabilite.port.PayeursPort.Payeur;
import com.hemodialyse.backend.domain.comptabilite.port.PiecesComptablesUseCase.SaisirPieceCommand;
import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.ComptesStock;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.SensEcriture;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paramétrage sans développement : plan comptable du centre, compte client de chaque payeur, modèles de pièces et
 * pièces saisies. Un nouveau client ou un nouveau type de pièce ne demande qu'un paramétrage.
 */
class ParametrageSoupleTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID AUTRE_CENTRE = UUID.randomUUID();
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 5);
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 9);

    private final ComptabiliteFixtures.Monde monde = new ComptabiliteFixtures.Monde();

    private static String refus(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(BusinessException.class, action).getCode();
    }

    private static BigDecimal solde(EcritureComptable e, String compte) {
        return e.getLignes().stream().filter(l -> l.getCompteSCF().equals(compte))
                .map(l -> l.getMontantDebit().subtract(l.getMontantCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private GenererEcritureFacturationCommand facture(UUID factureId, UUID payeurId) {
        return new GenererEcritureFacturationCommand(CENTRE, factureId, "FACT-1", UUID.randomUUID(), payeurId,
                new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"), JOUR, "Séances");
    }

    private ModelePiece loyer() {
        return new ModelePiece(UUID.randomUUID(), CENTRE, "loyer", "Loyer du centre", JournalCode.AC, true, List.of(
                new ModelePiece.Ligne(SensEcriture.DEBIT, "613", "Loyer"),
                new ModelePiece.Ligne(SensEcriture.DEBIT, "4456", null),
                new ModelePiece.Ligne(SensEcriture.CREDIT, "401", null)));
    }

    // ─── Plan comptable ──────────────────────────────────────────────────────

    @Test
    void a_centre_that_configured_nothing_gets_the_starting_chart_paged_and_searchable() {
        var page = monde.plan.lister(CENTRE, null, false, 0, 5);

        assertEquals(CompteComptable.parDefaut().size(), page.total());
        assertEquals(5, page.items().size());
        assertEquals("322", page.items().get(0).numero());
        assertEquals(List.of("512"), monde.plan.lister(CENTRE, "banq", true, 0, 20).items().stream()
                .map(CompteComptable::numero).toList());
        assertEquals(0, monde.comptes.count(CENTRE), "consulter n'écrit rien");
    }

    @Test
    void accounts_already_used_by_the_centre_parametrisation_join_its_starting_chart() {
        monde.mappings.save(new MappingComptable(CENTRE, "7061", "411100", "411500", "512", "530", "44571",
                ComptesStock.parDefaut(), Map.of()));

        assertEquals(1, monde.plan.lister(CENTRE, "7061", false, 0, 20).total());
        monde.plan.exigerActifs(CENTRE, List.of("7061"));
        assertEquals(0, monde.plan.lister(AUTRE_CENTRE, "7061", false, 0, 20).total());
    }

    @Test
    void the_first_change_records_the_chart_and_a_new_account_is_usable_at_once() {
        monde.plan.enregistrer(CENTRE, new CompteComptable(" 411210 ", "Clients — CNAS", true));

        assertEquals(CompteComptable.parDefaut().size() + 1, monde.comptes.count(CENTRE));
        monde.plan.exigerActifs(CENTRE, List.of("411210", " ", "512"));
        assertEquals("COMPTE_INCONNU", refus(() -> monde.plan.exigerActifs(AUTRE_CENTRE, List.of("411210"))));
        assertThrows(IllegalArgumentException.class, () -> new CompteComptable("41-1", "x", true));
        assertThrows(IllegalArgumentException.class, () -> new CompteComptable("411", " ", true));
    }

    @Test
    void an_account_in_use_cannot_be_deactivated_nor_deleted_and_one_with_entries_is_only_deactivated() {
        // utilisé par le paramétrage
        assertEquals("COMPTE_UTILISE", refus(() -> monde.plan.supprimer(CENTRE, "706")));
        assertEquals("COMPTE_UTILISE", refus(() -> monde.plan.enregistrer(CENTRE,
                new CompteComptable("512", "Banque", false))));
        assertEquals("COMPTE_INTROUVABLE", refus(() -> monde.plan.supprimer(CENTRE, "999")));

        // porteur d'écritures : une pièce de loyer passe par 613
        ModelePiece modele = monde.pieces.enregistrerModele(loyer());
        assertEquals("COMPTE_UTILISE", refus(() -> monde.plan.supprimer(CENTRE, "613")));
        monde.pieces.saisir(new SaisirPieceCommand(CENTRE, modele.id(), JOUR, null,
                List.of(new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"))));
        monde.pieces.supprimerModele(CENTRE, modele.id());
        assertEquals("COMPTE_AVEC_ECRITURES", refus(() -> monde.plan.supprimer(CENTRE, "613")));
        assertFalse(monde.plan.enregistrer(CENTRE, new CompteComptable("613", "Locations", false)).actif());

        // libre : supprimé
        monde.plan.supprimer(CENTRE, "626");
        assertEquals("COMPTE_INCONNU", refus(() -> monde.plan.exigerActifs(CENTRE, List.of("626"))));
        assertEquals("COMPTE_INCONNU", refus(() -> monde.plan.exigerActifs(CENTRE, List.of("613"))), "désactivé");
    }

    @Test
    void the_parametrisation_only_accepts_accounts_of_the_chart() {
        MappingComptable inconnu = new MappingComptable(CENTRE, "7069", "411100", "411500", "512", "530", "44571",
                ComptesStock.parDefaut(), Map.of());

        assertEquals("COMPTE_INCONNU", refus(() -> monde.comptabilite.saveMappingComptable(inconnu)));
        monde.plan.enregistrer(CENTRE, new CompteComptable("7069", "Prestations de dialyse", true));
        assertEquals("7069", monde.comptabilite.saveMappingComptable(inconnu).compteVentes());
    }

    // ─── Compte client par payeur ────────────────────────────────────────────

    @Test
    void a_new_client_only_needs_an_account_on_its_payer_no_code() {
        UUID cnas = UUID.randomUUID();
        UUID mutuelle = UUID.randomUUID();
        monde.payeurs.ajouter(CENTRE, new Payeur(cnas, "CNAS-16", "CNAS Alger"));
        monde.payeurs.ajouter(CENTRE, new Payeur(mutuelle, "MUT-1", "Mutuelle X"));
        monde.plan.enregistrer(CENTRE, new CompteComptable("411210", "Clients — CNAS", true));

        var affecte = monde.comptesPayeursService.definir(CENTRE, cnas, " 411210 ");

        assertEquals("411210", affecte.compte());
        assertEquals("CNAS Alger", affecte.nom());
        var liste = monde.comptesPayeursService.lister(CENTRE, null, 0, 20);
        assertEquals(2, liste.total());
        assertEquals("411210", liste.items().get(0).compte());
        assertNull(liste.items().get(1).compte(), "sans compte propre : compte client par défaut");

        // facture du payeur paramétré, d'un payeur sans compte, d'un patient qui paie lui-même
        assertEquals(new BigDecimal("1000.00"),
                solde(monde.comptabilite.genererEcritureFacturation(facture(UUID.randomUUID(), cnas)), "411210"));
        assertEquals(new BigDecimal("1000.00"),
                solde(monde.comptabilite.genererEcritureFacturation(facture(UUID.randomUUID(), mutuelle)), "411500"));
        assertEquals(new BigDecimal("1000.00"),
                solde(monde.comptabilite.genererEcritureFacturation(facture(UUID.randomUUID(), null)), "411100"));
    }

    @Test
    void a_payer_account_must_exist_in_the_chart_and_the_payer_in_the_centre() {
        UUID payeur = UUID.randomUUID();
        monde.payeurs.ajouter(CENTRE, new Payeur(payeur, "P", "Payeur"));

        assertEquals("COMPTE_INCONNU", refus(() -> monde.comptesPayeursService.definir(CENTRE, payeur, "411999")));
        assertEquals("PAYEUR_INTROUVABLE", refus(() -> monde.comptesPayeursService.definir(AUTRE_CENTRE, payeur, "411500")));

        monde.comptesPayeursService.definir(CENTRE, payeur, "411100");
        assertEquals("COMPTE_UTILISE", refus(() -> monde.plan.enregistrer(CENTRE,
                new CompteComptable("411100", "Clients", false))));
        assertNull(monde.comptesPayeursService.definir(CENTRE, payeur, " ").compte(), "vide = retour au compte par défaut");
        assertTrue(monde.comptesPayeurs.find(CENTRE, payeur).isEmpty());
    }

    @Test
    void a_payment_clears_the_account_the_invoice_debited_even_if_the_payer_account_changed_since() {
        UUID payeur = UUID.randomUUID();
        UUID factureId = UUID.randomUUID();
        monde.payeurs.ajouter(CENTRE, new Payeur(payeur, "P", "Payeur"));
        monde.comptabilite.genererEcritureFacturation(facture(factureId, payeur));
        monde.plan.enregistrer(CENTRE, new CompteComptable("411210", "Clients — CNAS", true));
        monde.comptesPayeursService.definir(CENTRE, payeur, "411210");

        EcritureComptable reglement = monde.comptabilite.genererEcritureReglement(new GenererEcritureReglementCommand(
                CENTRE, factureId, UUID.randomUUID(), new BigDecimal("400.00"), JOUR, "BANQUE", "Payeur", payeur));

        assertEquals(new BigDecimal("-400.00"), solde(reglement, "411500"), "le compte débité par la facture");
        assertEquals(new BigDecimal("400.00"), solde(reglement, "512"));

        // sans écriture de facture : compte actuel du payeur
        EcritureComptable orphelin = monde.comptabilite.genererEcritureReglement(new GenererEcritureReglementCommand(
                CENTRE, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), JOUR, "CAISSE", "Payeur", payeur));
        assertEquals(new BigDecimal("-50.00"), solde(orphelin, "411210"));
        assertEquals(new BigDecimal("50.00"), solde(orphelin, "530"));
    }

    // ─── Modèles de pièces et pièces saisies ─────────────────────────────────

    @Test
    void a_new_kind_of_piece_is_a_model_then_pieces_are_entered_with_amounts_only() {
        ModelePiece modele = monde.pieces.enregistrerModele(loyer());
        assertEquals("LOYER", modele.code());

        EcritureComptable piece = monde.pieces.saisir(new SaisirPieceCommand(CENTRE, modele.id(), JOUR, " Loyer octobre ",
                List.of(new BigDecimal("100000"), new BigDecimal("19000"), new BigDecimal("119000"))));

        assertEquals(JournalCode.AC, piece.getJournalCode());
        assertEquals("AC-2026-000001", piece.getNumeroPiece());
        assertEquals("Loyer octobre", piece.getLibelle());
        assertTrue(piece.estSaisie());
        assertEquals(modele.id(), piece.getModeleId());
        assertEquals(new BigDecimal("100000.00"), solde(piece, "613"));
        assertEquals(new BigDecimal("19000.00"), solde(piece, "4456"));
        assertEquals(new BigDecimal("-119000.00"), solde(piece, "401"));
        assertEquals("Loyer", piece.getLignes().get(0).getLibelleLigne(), "libellé de la ligne du modèle");
        assertEquals("Loyer octobre", piece.getLignes().get(2).getLibelleLigne(), "à défaut celui de la pièce");

        // une ligne à zéro n'est pas écrite ; libellé de la pièce = celui du modèle à défaut
        EcritureComptable sansTva = monde.pieces.saisir(new SaisirPieceCommand(CENTRE, modele.id(), JOUR, null,
                List.of(new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("500"))));
        assertEquals(2, sansTva.getLignes().size());
        assertEquals("Loyer du centre", sansTva.getLibelle());
        assertEquals(1, monde.pieces.listerModeles(CENTRE, true, 0, 20).total());
        assertEquals(0, monde.pieces.listerModeles(AUTRE_CENTRE, false, 0, 20).total());
    }

    @Test
    void an_entered_piece_must_be_balanced_complete_and_in_an_open_period() {
        ModelePiece modele = monde.pieces.enregistrerModele(loyer());

        assertEquals("ECRITURE_DESEQUILIBREE", refus(() -> monde.pieces.saisir(new SaisirPieceCommand(CENTRE,
                modele.id(), JOUR, null, List.of(new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("90"))))));
        assertEquals("PIECE_MONTANTS_INVALIDES", refus(() -> monde.pieces.saisir(
                new SaisirPieceCommand(CENTRE, modele.id(), JOUR, null, List.of(new BigDecimal("100"))))));
        assertEquals("PIECE_MONTANTS_INVALIDES", refus(() -> monde.pieces.saisir(new SaisirPieceCommand(CENTRE,
                modele.id(), JOUR, null, List.of(new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ONE)))));
        assertEquals("PIECE_MONTANTS_INVALIDES", refus(() -> monde.pieces.saisir(new SaisirPieceCommand(CENTRE,
                modele.id(), JOUR, null, List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)))));
        assertEquals("MODELE_INTROUVABLE", refus(() -> monde.pieces.saisir(new SaisirPieceCommand(AUTRE_CENTRE,
                modele.id(), JOUR, null, List.of(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)))));

        monde.periodes.cloturees.add(YearMonth.from(JOUR));
        assertEquals("PERIODE_CLOTUREE", refus(() -> monde.pieces.saisir(new SaisirPieceCommand(CENTRE, modele.id(),
                JOUR, null, List.of(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)))));
        assertTrue(monde.ecritures.du(CENTRE).isEmpty());
    }

    @Test
    void a_model_needs_a_unique_code_an_active_journal_known_accounts_and_both_sides() {
        monde.pieces.enregistrerModele(loyer());

        assertEquals("MODELE_CODE_EXISTANT", refus(() -> monde.pieces.enregistrerModele(loyer())));
        assertEquals("JOURNAL_INCONNU", refus(() -> monde.pieces.enregistrerModele(new ModelePiece(UUID.randomUUID(),
                CENTRE, "PAIE", "Salaires", JournalCode.de("OD"), true, List.of(
                new ModelePiece.Ligne(SensEcriture.DEBIT, "631", null),
                new ModelePiece.Ligne(SensEcriture.CREDIT, "421", null))))));
        assertEquals("COMPTE_INCONNU", refus(() -> monde.pieces.enregistrerModele(new ModelePiece(UUID.randomUUID(),
                CENTRE, "PAIE", "Salaires", JournalCode.BQ, true, List.of(
                new ModelePiece.Ligne(SensEcriture.DEBIT, "6319", null),
                new ModelePiece.Ligne(SensEcriture.CREDIT, "421", null))))));
        assertEquals("MODELE_LIGNES_INVALIDES", refus(() -> new ModelePiece(UUID.randomUUID(), CENTRE, "X", "X",
                JournalCode.BQ, true, List.of(new ModelePiece.Ligne(SensEcriture.DEBIT, "631", null),
                new ModelePiece.Ligne(SensEcriture.DEBIT, "421", null)))));
        assertThrows(IllegalArgumentException.class, () -> new ModelePiece(UUID.randomUUID(), CENTRE, "a b", "X",
                JournalCode.BQ, true, List.of()));
        // le même code reste libre dans un autre centre
        monde.pieces.enregistrerModele(new ModelePiece(UUID.randomUUID(), AUTRE_CENTRE, "LOYER", "Loyer",
                JournalCode.AC, true, loyer().lignes()));
        assertEquals("MODELE_INTROUVABLE", refus(() -> monde.pieces.supprimerModele(CENTRE, UUID.randomUUID())));
    }

    @Test
    void a_disabled_model_no_longer_takes_pieces() {
        ModelePiece modele = loyer();
        monde.pieces.enregistrerModele(new ModelePiece(modele.id(), CENTRE, modele.code(), modele.libelle(),
                modele.journal(), false, modele.lignes()));

        assertEquals("MODELE_INACTIF", refus(() -> monde.pieces.saisir(new SaisirPieceCommand(CENTRE, modele.id(), JOUR,
                null, List.of(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE)))));
        assertEquals(0, monde.pieces.listerModeles(CENTRE, true, 0, 20).total());
        assertEquals(1, monde.pieces.listerModeles(CENTRE, false, 0, 20).total());
    }

    @Test
    void an_entered_piece_is_never_deleted_it_is_reversed_once() {
        ModelePiece modele = monde.pieces.enregistrerModele(loyer());
        EcritureComptable piece = monde.pieces.saisir(new SaisirPieceCommand(CENTRE, modele.id(), JOUR, "Loyer",
                List.of(new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"))));

        EcritureComptable extourne = monde.pieces.extourner(CENTRE, piece.getId(), AUJOURDHUI);

        assertEquals(AUJOURDHUI, extourne.getDateEcriture());
        assertEquals("AC-2026-000002", extourne.getNumeroPiece());
        assertTrue(extourne.getLibelle().startsWith("Extourne AC-2026-000001"));
        assertEquals(new BigDecimal("-100.00"), solde(extourne, "613"));
        assertEquals(new BigDecimal("100.00"), solde(extourne, "401"));
        BigDecimal net = monde.ecritures.du(CENTRE).stream().flatMap(e -> e.getLignes().stream())
                .filter(l -> l.getCompteSCF().equals("613")).map(l -> l.getMontantDebit().subtract(l.getMontantCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, net.signum(), "la pièce et son extourne s'annulent");

        assertEquals("PIECE_DEJA_EXTOURNEE", refus(() -> monde.pieces.extourner(CENTRE, piece.getId(), AUJOURDHUI)));
        assertEquals("PIECE_DEJA_EXTOURNEE", refus(() -> monde.pieces.extourner(CENTRE, extourne.getId(), AUJOURDHUI)));
        assertEquals("ECRITURE_INTROUVABLE", refus(() -> monde.pieces.extourner(AUTRE_CENTRE, piece.getId(), AUJOURDHUI)));
    }

    @Test
    void a_generated_entry_cannot_be_reversed_by_hand_and_a_reversal_needs_an_open_period() {
        EcritureComptable vente = monde.comptabilite.genererEcritureFacturation(facture(UUID.randomUUID(), null));
        assertEquals("PIECE_NON_SAISIE", refus(() -> monde.pieces.extourner(CENTRE, vente.getId(), AUJOURDHUI)));

        ModelePiece modele = monde.pieces.enregistrerModele(loyer());
        EcritureComptable piece = monde.pieces.saisir(new SaisirPieceCommand(CENTRE, modele.id(), JOUR, null,
                List.of(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN)));
        monde.periodes.cloturees.add(YearMonth.from(AUJOURDHUI));
        assertEquals("PERIODE_CLOTUREE", refus(() -> monde.pieces.extourner(CENTRE, piece.getId(), AUJOURDHUI)));
        for (EcritureComptable e : monde.ecritures.du(CENTRE)) {
            BigDecimal debit = e.getLignes().stream().map(LigneEcriture::getMontantDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = e.getLignes().stream().map(LigneEcriture::getMontantCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, debit.compareTo(credit));
        }
    }
}
