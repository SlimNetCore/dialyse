package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUneAttestation;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUnePriseEnCharge;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la reprise de l'historique de couverture (RP3a, ADR 0171) : le même jeu de tests passe sur le double en mémoire et sur les
 * tables réelles ({@code attestation_droit}, {@code prise_en_charge}) : recherche par patient, centre et début, ajout, complément des
 * seules valeurs fournies, jamais d'écriture dans un autre centre.
 */
abstract class RepriseDeLHistoriqueDeCouvertureContrat {

    protected static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    protected static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    protected static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    protected static final UUID FORFAIT = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    protected static final UUID AUTRE_FORFAIT = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    private static final LocalDate JANVIER = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUIN = LocalDate.of(2026, 6, 30);
    private static final LocalDate DECEMBRE = LocalDate.of(2026, 12, 31);

    protected abstract RepriseDeLHistoriqueDeCouverture reprise();

    /** Valeurs lues dans la ligne, ou {@code null} si elle n'existe pas. */
    protected abstract ReportDUneAttestation attestationEnBase(UUID id);

    protected abstract ReportDUnePriseEnCharge priseEnChargeEnBase(UUID id);

    protected abstract boolean dateDeCreationPosee(UUID id);

    @Test
    void une_attestation_reprise_est_retrouvee_par_son_debut_dans_le_centre_et_pour_le_patient_seulement() {
        UUID id = reprise().reprendreUneAttestation(CENTRE, P1, new ReportDUneAttestation(JANVIER, DECEMBRE));

        assertThat(reprise().attestationDebutee(CENTRE, P1, JANVIER)).contains(id);
        assertThat(reprise().attestationDebutee(CENTRE, P1, JANVIER.plusDays(1))).isEmpty();
        assertThat(reprise().attestationDebutee(CENTRE, P2, JANVIER)).isEmpty();
        assertThat(reprise().attestationDebutee(AUTRE, P1, JANVIER)).isEmpty();
        assertThat(attestationEnBase(id)).isEqualTo(new ReportDUneAttestation(JANVIER, DECEMBRE));
        assertThat(dateDeCreationPosee(id)).isTrue();
    }

    @Test
    void completer_une_attestation_n_ecrit_que_les_valeurs_fournies_et_jamais_dans_un_autre_centre() {
        UUID id = reprise().reprendreUneAttestation(CENTRE, P1, new ReportDUneAttestation(JANVIER, JUIN));

        reprise().completerUneAttestation(AUTRE, id, new ReportDUneAttestation(null, DECEMBRE));
        assertThat(attestationEnBase(id)).isEqualTo(new ReportDUneAttestation(JANVIER, JUIN));

        reprise().completerUneAttestation(CENTRE, id, new ReportDUneAttestation(null, DECEMBRE));
        assertThat(attestationEnBase(id)).isEqualTo(new ReportDUneAttestation(JANVIER, DECEMBRE));

        reprise().completerUneAttestation(CENTRE, id, new ReportDUneAttestation(null, null));
        assertThat(attestationEnBase(id)).isEqualTo(new ReportDUneAttestation(JANVIER, DECEMBRE));
    }

    @Test
    void une_prise_en_charge_reprise_est_retrouvee_par_le_debut_de_sa_demande_dans_le_centre_et_pour_le_patient_seulement() {
        ReportDUnePriseEnCharge report = new ReportDUnePriseEnCharge(JANVIER, JUIN, FORFAIT, "VALIDEE", JANVIER, JUIN, FORFAIT);
        UUID id = reprise().reprendreUnePriseEnCharge(CENTRE, P1, report);

        assertThat(reprise().priseEnChargeDemandee(CENTRE, P1, JANVIER)).contains(id);
        assertThat(reprise().priseEnChargeDemandee(CENTRE, P1, JUIN)).isEmpty();
        assertThat(reprise().priseEnChargeDemandee(CENTRE, P2, JANVIER)).isEmpty();
        assertThat(reprise().priseEnChargeDemandee(AUTRE, P1, JANVIER)).isEmpty();
        assertThat(priseEnChargeEnBase(id)).isEqualTo(report);
        assertThat(dateDeCreationPosee(id)).isTrue();
    }

    @Test
    void une_prise_en_charge_sans_valeurs_effectives_est_ecrite_sans_elles() {
        UUID id = reprise().reprendreUnePriseEnCharge(CENTRE, P1,
                new ReportDUnePriseEnCharge(JANVIER, JUIN, FORFAIT, "CREE", null, null, null));

        assertThat(priseEnChargeEnBase(id)).isEqualTo(new ReportDUnePriseEnCharge(JANVIER, JUIN, FORFAIT, "CREE", null, null, null));
    }

    @Test
    void completer_une_prise_en_charge_n_ecrit_que_les_valeurs_fournies_et_jamais_dans_un_autre_centre() {
        UUID id = reprise().reprendreUnePriseEnCharge(CENTRE, P1,
                new ReportDUnePriseEnCharge(JANVIER, JUIN, FORFAIT, "CREE", null, null, null));

        reprise().completerUnePriseEnCharge(AUTRE, id, new ReportDUnePriseEnCharge(null, null, null, "VALIDEE", null, null, null));
        assertThat(priseEnChargeEnBase(id).statut()).isEqualTo("CREE");

        reprise().completerUnePriseEnCharge(CENTRE, id,
                new ReportDUnePriseEnCharge(null, DECEMBRE, AUTRE_FORFAIT, "VALIDEE", JANVIER, null, AUTRE_FORFAIT));
        assertThat(priseEnChargeEnBase(id)).isEqualTo(
                new ReportDUnePriseEnCharge(JANVIER, DECEMBRE, AUTRE_FORFAIT, "VALIDEE", JANVIER, null, AUTRE_FORFAIT));

        reprise().completerUnePriseEnCharge(CENTRE, id, new ReportDUnePriseEnCharge(null, null, null, null, null, null, null));
        assertThat(priseEnChargeEnBase(id).statut()).isEqualTo("VALIDEE");
    }
}
