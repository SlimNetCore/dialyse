package com.hemodialyse.backend.contexts.comptabilite.domain.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationComptableTest {

    @Test
    void les_six_operations_ont_leur_journal_par_defaut() {
        assertThat(OperationComptable.values()).extracting(o -> o.name() + "=" + o.journalParDefaut())
                .containsExactly("VENTE=VE", "REGLEMENT_BANQUE=BQ", "REGLEMENT_CAISSE=CA", "STOCK_RECEPTION=AC",
                        "STOCK_SORTIE=ST", "STOCK_INVENTAIRE=ST");
        assertThat(OperationComptable.STOCK_INVENTAIRE.journalParDefaut()).isEqualTo(CodeJournal.ST);
    }

    @Test
    void une_operation_se_designe_par_son_nom_exact() {
        assertThat(OperationComptable.de("REGLEMENT_CAISSE")).isEqualTo(OperationComptable.REGLEMENT_CAISSE);
    }

    @Test
    void une_operation_inconnue_ou_en_minuscules_est_refusee() {
        assertThatThrownBy(() -> OperationComptable.de("vente")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Opération comptable inconnue : vente");
        assertThatThrownBy(() -> OperationComptable.de(null)).hasMessage("Opération comptable inconnue : null");
        assertThatThrownBy(() -> OperationComptable.de("REMBOURSEMENT")).hasMessageContaining("REMBOURSEMENT");
    }
}
