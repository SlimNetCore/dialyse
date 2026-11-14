package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PoidsSecsPrescritsPublies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PoidsSecsPrescritsPublies.PoidsSecPrescrit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de {@link PoidsSecsPrescritsPublies} (LEC-PLN-GMAO c, ADR 0180) : le double en mémoire, l'adapter JDBC sur H2 et sur
 * PostgreSQL 16 passent le MÊME jeu de cas. Départage : date décroissante, mise à jour décroissante (inconnue en dernier), identifiant
 * décroissant (ordre déterministe L8 de l'ancienne sous-requête de la Séance).
 */
abstract class PoidsSecsPrescritsContrat {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000e0a1");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000e0b2");
    static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-00000000e0c3");
    static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-00000000e0c4");
    static final Instant DIX_HEURES = Instant.parse("2026-01-01T10:00:00Z");
    static final Instant MIDI = Instant.parse("2026-01-01T12:00:00Z");

    protected abstract PoidsSecsPrescritsPublies port();

    /** Pose une prescription ({@code poidsSec} nul : sans poids sec cible ; {@code majLe} nul : mise à jour inconnue). */
    protected abstract void prescription(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe);

    private static UUID id(int n) {
        return UUID.fromString("5e7ec800-0000-0000-0000-" + String.format("%012x", n));
    }

    private static LocalDate le(String iso) {
        return LocalDate.parse(iso);
    }

    private static BigDecimal d(String valeur) {
        return new BigDecimal(valeur);
    }

    private List<String> resume(UUID centre, UUID patient) {
        return port().poidsSecsCiblesPrescrits(centre, patient).stream()
                .map(p -> p.datePrescription() + "|" + p.poidsSecCibleKg().stripTrailingZeros().toPlainString()).toList();
    }

    @Test
    void sans_prescription_la_liste_est_vide() {
        assertThat(port().poidsSecsCiblesPrescrits(A, PATIENT)).isEmpty();
    }

    @Test
    void seules_les_prescriptions_du_patient_et_du_centre_qui_portent_un_poids_sec_sont_rendues_de_la_plus_recente_a_la_plus_ancienne() {
        prescription(id(1), A, PATIENT, le("2026-01-01"), d("66.00"), DIX_HEURES);
        prescription(id(2), A, PATIENT, le("2026-03-01"), d("68.50"), DIX_HEURES);
        prescription(id(3), A, PATIENT, le("2026-02-01"), null, DIX_HEURES);
        prescription(id(4), B, PATIENT, le("2026-02-04"), d("99.00"), DIX_HEURES);
        prescription(id(5), A, AUTRE, le("2026-02-04"), d("98.00"), DIX_HEURES);

        assertThat(resume(A, PATIENT)).containsExactly("2026-03-01|68.5", "2026-01-01|66");
        assertThat(resume(B, PATIENT)).containsExactly("2026-02-04|99");
        assertThat(port().poidsSecsCiblesPrescrits(A, PATIENT).get(0)).isEqualTo(new PoidsSecPrescrit(le("2026-03-01"), d("68.50")));
    }

    @Test
    void a_date_egale_la_mise_a_jour_connue_precede_la_mise_a_jour_inconnue_puis_la_plus_recente_precede() {
        prescription(id(0xf1), A, PATIENT, le("2026-02-01"), d("60.00"), null);
        prescription(id(1), A, PATIENT, le("2026-02-01"), d("61.00"), DIX_HEURES);
        prescription(id(2), A, PATIENT, le("2026-02-01"), d("62.00"), MIDI);

        assertThat(resume(A, PATIENT)).containsExactly("2026-02-01|62", "2026-02-01|61", "2026-02-01|60");
    }

    @Test
    void a_date_et_mise_a_jour_egales_l_identifiant_le_plus_eleve_precede_et_deux_mises_a_jour_inconnues_se_departagent_par_identifiant() {
        prescription(id(2), A, PATIENT, le("2026-04-01"), d("70.00"), MIDI);
        prescription(id(3), A, PATIENT, le("2026-04-01"), d("71.00"), MIDI);
        prescription(id(4), A, PATIENT, le("2026-05-01"), d("72.00"), null);
        prescription(id(5), A, PATIENT, le("2026-05-01"), d("73.00"), null);

        assertThat(resume(A, PATIENT)).containsExactly("2026-05-01|73", "2026-05-01|72", "2026-04-01|71", "2026-04-01|70");
    }
}
