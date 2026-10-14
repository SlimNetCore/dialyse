package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervention.IntervenantsDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.TarifIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.ConditionsDeValorisation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du port {@link IntervenantsDuCentre} : ce que l'intervention lit de ses intervenants pour valoriser son temps.
 * Exécuté contre le double in-memory et l'adapter JPA sur H2. Les intervenants sont enregistrés par leur repository.
 */
public abstract class IntervenantsDuCentreContrat {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000b"));
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99997500-0000-0000-0000-0000000000f1"));

    protected abstract IntervenantRepository intervenants();

    protected abstract IntervenantsDuCentre port();

    private IntervenantId enregistrer(CentreId centre, NatureIntervenant nature, String tarif, boolean actif) {
        IntervenantId id = new IntervenantId(UUID.randomUUID());
        Intervenant intervenant = Intervenant.enregistrer(id, centre, "Bernard", nature, null, null,
                tarif == null ? null : new TarifIntervenant(new BigDecimal(tarif)), ADMIN, Instant.parse("2026-03-01T09:00:00Z"));
        if (!actif) {
            intervenant.desactiver(ADMIN, Instant.parse("2026-03-01T10:00:00Z"));
        }
        intervenants().enregistrer(intervenant);
        return id;
    }

    @Test
    void should_give_the_nature_and_the_rate_of_an_intervenant_of_the_centre() {
        IntervenantId interne = enregistrer(CENTRE_A, NatureIntervenant.INTERNE, "35.00", true);
        IntervenantId externe = enregistrer(CENTRE_A, NatureIntervenant.EXTERNE, "120.50", true);

        assertThat(port().conditionsDe(interne, CENTRE_A)).hasValueSatisfying(c -> {
            assertThat(c.nature()).isEqualTo(NatureIntervenant.INTERNE);
            assertThat(c.tarifHoraire()).isEqualByComparingTo("35.00");
        });
        assertThat(port().conditionsDe(externe, CENTRE_A)).map(ConditionsDeValorisation::nature)
                .contains(NatureIntervenant.EXTERNE);
    }

    @Test
    void should_give_no_rate_when_the_intervenant_has_none() {
        IntervenantId sansTarif = enregistrer(CENTRE_A, NatureIntervenant.INTERNE, null, true);

        assertThat(port().conditionsDe(sansTarif, CENTRE_A)).hasValueSatisfying(c -> assertThat(c.tarifHoraire()).isNull());
    }

    @Test
    void should_still_give_a_deactivated_intervenant() {
        IntervenantId desactive = enregistrer(CENTRE_A, NatureIntervenant.EXTERNE, "80.00", false);

        assertThat(port().conditionsDe(desactive, CENTRE_A)).isPresent();
    }

    @Test
    void should_give_nothing_for_an_intervenant_of_another_centre_or_an_unknown_one() {
        IntervenantId autreCentre = enregistrer(CENTRE_B, NatureIntervenant.EXTERNE, "99.99", true);

        assertThat(port().conditionsDe(autreCentre, CENTRE_A)).isEmpty();
        assertThat(port().conditionsDe(new IntervenantId(UUID.randomUUID()), CENTRE_A)).isEmpty();
    }
}
