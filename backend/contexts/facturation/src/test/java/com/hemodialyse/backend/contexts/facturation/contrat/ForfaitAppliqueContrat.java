package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du forfait appliqué par la Facturation (RG-FAC-021) : les forfaits du centre ({@link ForfaitsDuCentre}) et la politique de forfait
 * de bout en bout (forfait imposé, puis prise en charge du patient lue par {@code CouvertureDesPatients}). Ces 5 cas ne touchent pas la
 * table des séances de la Facturation : les séances sont lues par le port {@code SeancesAFacturerFournies}, que la plateforme implémente
 * (ADR 0097). Exécuté contre le double en mémoire (via {@link SeancesContrat}, qui l'étend) et contre les adapters JDBC réels du forfait et
 * de la prise en charge sur H2 et PostgreSQL 16 ({@link ForfaitAppliqueJdbcContrat}, revue SE7c2, constat m1).
 */
public abstract class ForfaitAppliqueContrat {

    protected static final CentreId CENTRE_A =
            new CentreId(UUID.fromString("99996800-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B =
            new CentreId(UUID.fromString("99996800-0000-0000-0000-00000000000b"));
    protected static final UUID AMRANI = UUID.fromString("99996800-0000-0000-0000-00000000a001");
    protected static final UUID BELKACEM = UUID.fromString("99996800-0000-0000-0000-00000000a002");
    protected static final UUID PATIENT_B = UUID.fromString("99996800-0000-0000-0000-00000000b001");
    protected static final UUID HD = UUID.fromString("99996800-0000-0000-0000-0000000f0001");
    protected static final UUID PREMIUM = UUID.fromString("99996800-0000-0000-0000-0000000f0002");
    protected static final UUID FORFAIT_B = UUID.fromString("99996800-0000-0000-0000-0000000f00b1");
    protected static final PeriodeDeFacturation AOUT = new PeriodeDeFacturation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

    protected abstract SeancesFacturables facturables();

    protected abstract ForfaitsDuCentre forfaits();

    protected abstract void patient(UUID id, CentreId centre, String code, String nom,
                                    String prenom, String etat);

    protected abstract void forfait(UUID id, CentreId centre, String libelle, String prix);

    protected abstract void priseEnCharge(UUID patient, CentreId centre,
                                          PriseEnChargeTarifee pec);

    protected abstract void seance(UUID id, CentreId centre, UUID patient, LocalDate date,
                                   String statut, boolean facturee);

    protected abstract void surcharge(UUID seance, ForfaitApplique forfait);

    /**
     * Décor commun : deux forfaits et deux patients au centre A, un forfait et un patient au centre B.
     */
    protected void decor() {
        forfait(HD, CENTRE_A, "Forfait HD", "3500.00");
        forfait(PREMIUM, CENTRE_A, "Forfait HD Premium", "5200.00");
        forfait(FORFAIT_B, CENTRE_B, "Forfait du centre B", "1000.00");
        patient(AMRANI, CENTRE_A, "P-001", "Amrani", "Lina", "PERMANENT");
        patient(BELKACEM, CENTRE_A, "P-002", "Belkacem", "Omar", null);
        patient(PATIENT_B, CENTRE_B, "PB-01", "Zerrouki", "Rania", "PERMANENT");
        priseEnCharge(AMRANI, CENTRE_A, pecEffective(HD, "Forfait HD", "3500.00", "2026-01-01", "2026-12-31"));
    }

    protected static PriseEnChargeTarifee pecEffective(UUID forfait, String libelle, String prix, String debut, String fin) {
        return new PriseEnChargeTarifee(new ForfaitApplique(forfait, libelle, new BigDecimal(prix)), true, LocalDate.parse(debut),
                LocalDate.parse(fin), null, null, Instant.parse("2026-01-01T08:00:00Z"));
    }

    protected UUID uneSeance(CentreId centre, UUID patient, String jour, String statut,
                             boolean facturee) {
        UUID id = UUID.randomUUID();
        seance(id, centre, patient, LocalDate.parse(jour), statut, facturee);
        return id;
    }

    // ---- politique de forfait de bout en bout (prise en charge réelle)

    @Test
    void should_apply_the_forfait_of_the_prise_en_charge_covering_the_date() {
        decor();
        uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);

        ForfaitApplique forfait = facturables().dansLaPeriode(CENTRE_A, AOUT).getFirst().forfait();

        assertThat(forfait.id()).isEqualTo(HD);
        assertThat(forfait.libelle()).isEqualTo("Forfait HD");
        assertThat(forfait.prixHt()).isEqualByComparingTo("3500.00");
    }

    @Test
    void should_apply_the_forfait_imposed_on_the_seance_before_the_prise_en_charge() {
        decor();
        UUID seance = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        surcharge(seance, new ForfaitApplique(PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00")));

        ForfaitApplique forfait = facturables().dansLaPeriode(CENTRE_A, AOUT).getFirst().forfait();

        assertThat(forfait.id()).isEqualTo(PREMIUM);
        assertThat(forfait.prixHt()).isEqualByComparingTo("5200.00");
    }

    @Test
    void should_apply_the_most_recent_prise_en_charge_even_when_it_has_ended_then_the_undefined_forfait() {
        decor();
        priseEnCharge(BELKACEM, CENTRE_A, pecEffective(PREMIUM, "Forfait HD Premium", "5200.00", "2026-01-01", "2026-06-30"));
        uneSeance(CENTRE_A, BELKACEM, "2026-08-02", "VALIDEE", false);
        UUID sansPriseEnCharge = UUID.fromString("99996800-0000-0000-0000-00000000a003");
        patient(sansPriseEnCharge, CENTRE_A, "P-003", "Chikhi", "Nadia", "PERMANENT");
        uneSeance(CENTRE_A, sansPriseEnCharge, "2026-08-02", "VALIDEE", false);

        List<SeanceFacturable> lues = facturables().dansLaPeriode(CENTRE_A, AOUT);

        assertThat(lues).extracting(s -> s.patient().nom()).containsExactly("Belkacem", "Chikhi");
        assertThat(lues.get(0).forfait().id()).isEqualTo(PREMIUM);
        assertThat(lues.get(1).forfait()).isEqualTo(ForfaitApplique.NON_DEFINI);
    }

    // ---- forfaits du centre

    @Test
    void should_find_a_forfait_of_the_centre_with_its_label_and_price() {
        decor();

        ForfaitApplique forfait = forfaits().forfait(CENTRE_A, PREMIUM).orElseThrow();

        assertThat(forfait.id()).isEqualTo(PREMIUM);
        assertThat(forfait.libelle()).isEqualTo("Forfait HD Premium");
        assertThat(forfait.prixHt()).isEqualByComparingTo("5200.00");
    }

    @Test
    void should_not_find_an_unknown_forfait_nor_one_of_another_centre() {
        decor();

        assertThat(forfaits().forfait(CENTRE_A, UUID.randomUUID())).isEmpty();
        assertThat(forfaits().forfait(CENTRE_A, FORFAIT_B)).isEmpty();
    }
}
