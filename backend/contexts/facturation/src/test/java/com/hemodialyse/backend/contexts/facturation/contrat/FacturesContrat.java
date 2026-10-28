package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Facture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Factures;
import com.hemodialyse.backend.contexts.facturation.domain.facture.NumeroDeFactureDejaPris;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.FactureSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.NumerotationPrevisionnelle;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PatientFacture;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Regroupement;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat partagé des factures émises : double en mémoire, JDBC sur H2 et PostgreSQL 16. Les environnements JDBC posent le patient
 * de la facture (clé étrangère logique) ; chaque environnement relit la facture enregistrée sous la forme {@link Enregistree}.
 */
public abstract class FacturesContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996900-0000-0000-0000-0000000000a1"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996900-0000-0000-0000-0000000000b1"));
    protected static final UUID PATIENT = UUID.fromString("99996900-0000-0000-0000-00000000a001");
    protected static final UUID FORFAIT_HD = UUID.fromString("99996900-0000-0000-0000-0000000f0001");
    protected static final UUID FORFAIT_PREMIUM = UUID.fromString("99996900-0000-0000-0000-0000000f0002");
    protected static final UUID PAYEUR = UUID.fromString("99996900-0000-0000-0000-000000000c01");
    protected static final UUID AGENCE = UUID.fromString("99996900-0000-0000-0000-000000000a61");
    protected static final UUID AUTEUR = UUID.fromString("99996900-0000-0000-0000-00000000c001");
    private static final PeriodeDeFacturation AOUT = new PeriodeDeFacturation(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 20);

    private static Facture uneFacture(CentreId centre, String numero, UUID auteur) {
        PatientFacture patient = new PatientFacture(PATIENT, "P-001", "Amrani", "Lina", "PERMANENT", "ASS-001", PAYEUR, AGENCE);
        ForfaitApplique hd = new ForfaitApplique(FORFAIT_HD, "Forfait HD", new BigDecimal("3500.00"));
        ForfaitApplique premium = new ForfaitApplique(FORFAIT_PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00"));
        List<SeanceFacturable> seances = List.of(seance(patient, 2, hd), seance(patient, 4, hd), seance(patient, 6, premium));
        TauxDeTva taux = new TauxDeTva(new BigDecimal("9"));
        FactureSimulee groupe = Simulation.etablir(centre, Instant.parse("2026-10-20T08:00:00Z"), AOUT, Regroupement.PAR_PATIENT,
                seances, taux, new NumerotationPrevisionnelle(ModeleDeNumerotation.PAR_DEFAUT, JOUR, 0)).factures().getFirst();
        return Facture.emettre(UUID.randomUUID(), centre, groupe, numero, AOUT, JOUR, taux, auteur);
    }

    private static SeanceFacturable seance(PatientFacture patient, int jour, ForfaitApplique forfait) {
        return new SeanceFacturable(UUID.randomUUID(), patient, LocalDate.of(2026, 8, jour), StatutDeSeance.VALIDEE, forfait);
    }

    protected abstract Factures factures();

    protected abstract Enregistree relire(UUID facture);

    @Test
    void should_record_the_invoice_with_its_frozen_snapshots_totals_and_author() {
        Facture facture = uneFacture(CENTRE_A, "FAC-2026-0042", AUTEUR);

        factures().enregistrer(facture);

        Enregistree lue = relire(facture.id());
        assertThat(lue.numero()).isEqualTo("FAC-2026-0042");
        assertThat(lue.patient()).isEqualTo(PATIENT);
        assertThat(lue.codePatient()).isEqualTo("P-001");
        assertThat(lue.nomComplet()).isEqualTo("Amrani Lina");
        assertThat(lue.etatPatient()).isEqualTo("PERMANENT");
        assertThat(lue.immatriculation()).isEqualTo("ASS-001");
        assertThat(lue.centrePayeur()).isEqualTo(PAYEUR);
        assertThat(lue.agence()).isEqualTo(AGENCE);
        assertThat(lue.debut()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(lue.fin()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(lue.date()).isEqualTo(JOUR);
        assertThat(lue.taux()).isEqualByComparingTo("9.00");
        assertThat(lue.totalHt()).isEqualByComparingTo("12200.00");
        assertThat(lue.totalTva()).isEqualByComparingTo("1098.00");
        assertThat(lue.totalTtc()).isEqualByComparingTo("13298.00");
        assertThat(lue.emisePar()).isEqualTo(AUTEUR);
    }

    @Test
    void should_record_the_lines_with_forfait_label_price_quantity_and_amount() {
        Facture facture = uneFacture(CENTRE_A, "FAC-2026-0042", AUTEUR);

        factures().enregistrer(facture);

        assertThat(relire(facture.id()).lignes()).containsExactlyInAnyOrder(
                new LigneEnregistree(FORFAIT_HD, "Forfait HD", new BigDecimal("3500.00"), 2, new BigDecimal("7000.00")),
                new LigneEnregistree(FORFAIT_PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00"), 1, new BigDecimal("5200.00")));
    }

    @Test
    void should_record_an_invoice_without_known_author() {
        Facture facture = uneFacture(CENTRE_A, "FAC-2026-0042", null);

        factures().enregistrer(facture);

        assertThat(relire(facture.id()).emisePar()).isNull();
    }

    @Test
    void should_know_the_numbers_already_taken_by_the_centre() {
        factures().enregistrer(uneFacture(CENTRE_A, "FAC-2026-0042", AUTEUR));

        assertThat(factures().numeroDejaPris(CENTRE_A, "FAC-2026-0042")).isTrue();
        assertThat(factures().numeroDejaPris(CENTRE_A, "FAC-2026-0043")).isFalse();
        assertThat(factures().numeroDejaPris(CENTRE_B, "FAC-2026-0042")).isFalse();
    }

    @Test
    void should_refuse_a_second_invoice_under_the_same_number_in_the_same_centre() {
        factures().enregistrer(uneFacture(CENTRE_A, "FAC-2026-0042", AUTEUR));

        assertThatThrownBy(() -> factures().enregistrer(uneFacture(CENTRE_A, "FAC-2026-0042", AUTEUR)))
                .isInstanceOf(NumeroDeFactureDejaPris.class).hasMessageContaining("FAC-2026-0042");
    }

    @Test
    void should_allow_the_same_number_in_another_centre() {
        factures().enregistrer(uneFacture(CENTRE_A, "FAC-2026-0042", AUTEUR));

        factures().enregistrer(uneFacture(CENTRE_B, "FAC-2026-0042", AUTEUR));

        assertThat(factures().numeroDejaPris(CENTRE_B, "FAC-2026-0042")).isTrue();
    }

    /**
     * La facture telle que l'environnement la relit.
     */
    protected record Enregistree(String numero, UUID patient, String codePatient, String nomComplet, String etatPatient,
                                 String immatriculation, UUID centrePayeur, UUID agence, LocalDate debut, LocalDate fin,
                                 LocalDate date, BigDecimal taux, BigDecimal totalHt, BigDecimal totalTva,
                                 BigDecimal totalTtc,
                                 UUID emisePar, List<LigneEnregistree> lignes) {
    }

    protected record LigneEnregistree(UUID forfait, String libelle, BigDecimal prix, int nombre, BigDecimal montant) {
        @Override
        public boolean equals(Object autre) {
            return autre instanceof LigneEnregistree(
                    UUID forfait1, String libelle1, BigDecimal prix1, int nombre1, BigDecimal montant1
            ) && java.util.Objects.equals(forfait, forfait1)
                    && java.util.Objects.equals(libelle, libelle1) && prix.compareTo(prix1) == 0 && nombre == nombre1
                    && montant.compareTo(montant1) == 0;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(forfait, libelle, nombre);
        }
    }
}
