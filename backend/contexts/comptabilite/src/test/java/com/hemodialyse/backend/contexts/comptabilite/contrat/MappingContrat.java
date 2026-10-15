package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.ComptesDuStock;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.ComptesDuStockSaisis;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé du mapping d'un centre : exécuté contre le double en mémoire, l'adaptateur JPA sur H2 et l'adaptateur sur
 * PostgreSQL 16.
 */
public abstract class MappingContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996400-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996400-0000-0000-0000-00000000000b"));

    private static MappingComptable personnalise(CentreId centre, String ventes, String tva, Map<OperationComptable, CodeJournal> journaux) {
        return MappingComptable.parDefaut(centre).redefini(ventes, "411101", "411501", "5121", "5301", tva,
                new ComptesDuStockSaisis("3221", "6021", "4081", "7571", "6571"), journaux);
    }

    protected abstract MappingComptableRepository repository();

    /**
     * Une ligne telle qu'une version antérieure l'a écrite : sans comptes du stock ni journaux, TVA collectée vide.
     */
    protected abstract void ligneHeritee(CentreId centre);

    @Test
    void should_return_nothing_for_a_centre_that_saved_no_mapping() {
        assertThat(repository().duCentre(CENTRE_A)).isEmpty();
    }

    @Test
    void should_read_back_every_account_and_every_journal_of_a_saved_mapping() {
        repository().enregistrer(personnalise(CENTRE_A, "7061", "44572", Map.of(OperationComptable.VENTE, CodeJournal.de("OD"),
                OperationComptable.REGLEMENT_CAISSE, CodeJournal.BQ, OperationComptable.STOCK_INVENTAIRE, CodeJournal.AC)));

        MappingComptable relu = repository().duCentre(CENTRE_A).orElseThrow();

        assertThat(relu.centre()).isEqualTo(CENTRE_A);
        assertThat(relu.compteVentes()).isEqualTo("7061");
        assertThat(relu.compteClientPatient()).isEqualTo("411101");
        assertThat(relu.compteClientDefaut()).isEqualTo("411501");
        assertThat(relu.compteBanque()).isEqualTo("5121");
        assertThat(relu.compteCaisse()).isEqualTo("5301");
        assertThat(relu.compteTvaCollectee()).contains("44572");
        assertThat(relu.stock()).isEqualTo(new ComptesDuStock("3221", "6021", "4081", "7571", "6571"));
        assertThat(relu.journalDe(OperationComptable.VENTE)).isEqualTo(CodeJournal.de("OD"));
        assertThat(relu.journalDe(OperationComptable.REGLEMENT_BANQUE)).isEqualTo(CodeJournal.BQ);
        assertThat(relu.journalDe(OperationComptable.REGLEMENT_CAISSE)).isEqualTo(CodeJournal.BQ);
        assertThat(relu.journalDe(OperationComptable.STOCK_RECEPTION)).isEqualTo(CodeJournal.AC);
        assertThat(relu.journalDe(OperationComptable.STOCK_SORTIE)).isEqualTo(CodeJournal.ST);
        assertThat(relu.journalDe(OperationComptable.STOCK_INVENTAIRE)).isEqualTo(CodeJournal.AC);
    }

    @Test
    void should_keep_a_single_line_per_centre_when_saving_again() {
        repository().enregistrer(personnalise(CENTRE_A, "7061", "44572", Map.of()));
        repository().enregistrer(personnalise(CENTRE_A, "7062", "44572", Map.of()));

        assertThat(repository().duCentre(CENTRE_A).orElseThrow().compteVentes()).isEqualTo("7062");
    }

    @Test
    void should_keep_the_mappings_of_two_centres_apart() {
        repository().enregistrer(personnalise(CENTRE_A, "7061", "44572", Map.of()));
        repository().enregistrer(personnalise(CENTRE_B, "7062", "44572", Map.of()));

        assertThat(repository().duCentre(CENTRE_A).orElseThrow().compteVentes()).isEqualTo("7061");
        assertThat(repository().duCentre(CENTRE_B).orElseThrow().compteVentes()).isEqualTo("7062");
    }

    @Test
    void should_read_a_blank_vat_account_as_44571_whatever_was_saved() {
        repository().enregistrer(personnalise(CENTRE_A, "7061", null, Map.of()));

        assertThat(repository().duCentre(CENTRE_A).orElseThrow().compteTvaCollectee()).contains("44571");
    }

    @Test
    void should_complete_a_legacy_line_with_the_default_stock_accounts_and_journals() {
        ligneHeritee(CENTRE_A);

        MappingComptable relu = repository().duCentre(CENTRE_A).orElseThrow();

        assertThat(relu.compteVentes()).isEqualTo("7060");
        assertThat(relu.stock()).isEqualTo(ComptesDuStock.parDefaut());
        assertThat(relu.compteTvaCollectee()).contains("44571");
        for (OperationComptable operation : OperationComptable.values()) {
            assertThat(relu.journalDe(operation)).isEqualTo(operation.journalParDefaut());
        }
    }
}
