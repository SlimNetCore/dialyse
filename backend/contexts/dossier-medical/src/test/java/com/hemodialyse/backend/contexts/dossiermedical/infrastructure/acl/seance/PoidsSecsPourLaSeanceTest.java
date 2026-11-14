package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PoidsSecsPrescritsPublies.PoidsSecPrescrit;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PoidsSecDesPrescriptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Traduction de l'ACL fournie à la Séance (LEC-PLN-GMAO c, ADR 0180) : terme à terme, ordre et centre conservés. */
class PoidsSecsPourLaSeanceTest {

    @Test
    void les_poids_secs_prescrits_sont_traduits_dans_le_meme_ordre_pour_le_centre_et_le_patient_demandes() {
        UUID centre = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        var acl = new PoidsSecsPourLaSeance((c, p) -> c.equals(centre) && p.equals(patient)
                ? List.of(new PoidsSecPrescrit(LocalDate.parse("2026-03-01"), new BigDecimal("68.50")),
                new PoidsSecPrescrit(LocalDate.parse("2026-01-01"), new BigDecimal("66.00")))
                : List.of());

        assertThat(acl.poidsSecsCiblesPrescrits(centre, patient)).containsExactly(
                new PoidsSecDesPrescriptions.PoidsSecPrescrit(LocalDate.parse("2026-03-01"), new BigDecimal("68.50")),
                new PoidsSecDesPrescriptions.PoidsSecPrescrit(LocalDate.parse("2026-01-01"), new BigDecimal("66.00")));
        assertThat(acl.poidsSecsCiblesPrescrits(UUID.randomUUID(), patient)).isEmpty();
    }
}
