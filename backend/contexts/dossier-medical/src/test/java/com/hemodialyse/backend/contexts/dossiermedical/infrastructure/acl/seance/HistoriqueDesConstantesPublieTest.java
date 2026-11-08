package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Constantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConstantesDeSeance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Pagination;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees.ConstantesDeSeancePubliees;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction des constantes publiées par la Séance vers l'historique du Dossier médical (ADR 0098, SE7d) : total lu AVANT la page, taille et
 * rang de la pagination transmis tels quels, sept mesures recopiées, ordre des lignes inchangé.
 */
class HistoriqueDesConstantesPublieTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0xD9, 1));
    private static final PatientId PATIENT = new PatientId(new UUID(0xD9, 2));
    private static final UUID S1 = new UUID(0xD9, 0x51);
    private static final UUID S2 = new UUID(0xD9, 0x52);
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 6);

    private static final class Publiees implements ConstantesDesSeancesPubliees {
        final List<String> appels = new ArrayList<>();

        @Override
        public long nombreDeSeances(UUID centre, UUID patient) {
            appels.add("total " + centre + " " + patient);
            return 7;
        }

        @Override
        public List<ConstantesDeSeancePubliees> page(UUID centre, UUID patient, int taille, long rang) {
            appels.add("page " + centre + " " + patient + " " + taille + " " + rang);
            return List.of(new ConstantesDeSeancePubliees(S2, JOUR, new BigDecimal("70.50"), new BigDecimal("68.00"), "130/80", "120/70", 300,
                            new BigDecimal("2000.00"), 240),
                    new ConstantesDeSeancePubliees(S1, JOUR.minusDays(1), null, null, null, null, null, null, null));
        }
    }

    @ParameterizedTest(name = "page {0} taille {1} : rang {2}")
    @CsvSource({"0, 20, 0", "1, 2, 2", "3, 100, 300"})
    void le_total_est_lu_avant_la_page_avec_la_taille_et_le_rang_de_la_pagination(int page, int taille, long rang) {
        Publiees publiees = new Publiees();

        PageDeConstantes lue = new HistoriqueDesConstantesPublie(publiees).dePatient(CENTRE, PATIENT, new Pagination(page, taille));

        assertThat(publiees.appels).containsExactly("total " + CENTRE.valeur() + " " + PATIENT.valeur(),
                "page " + CENTRE.valeur() + " " + PATIENT.valeur() + " " + taille + " " + rang);
        assertThat(lue.total()).isEqualTo(7);
        assertThat(lue.page()).isEqualTo(page);
        assertThat(lue.size()).isEqualTo(taille);
    }

    @Test
    void les_sept_mesures_sont_recopiees_et_une_seance_sans_volet_a_des_constantes_vides() {
        PageDeConstantes lue = new HistoriqueDesConstantesPublie(new Publiees()).dePatient(CENTRE, PATIENT, new Pagination(0, 20));

        assertThat(lue.items()).containsExactly(
                new ConstantesDeSeance(new SeanceId(S2), JOUR, new Constantes(new BigDecimal("70.50"), new BigDecimal("68.00"), "130/80",
                        "120/70", 300, new BigDecimal("2000.00"), 240)),
                ConstantesDeSeance.sansSaisie(new SeanceId(S1), JOUR.minusDays(1)));
        assertThat(lue.items().get(1).constantes().sontVides()).isTrue();
    }
}
