package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.ForfaitsDuCentreParLeLegacy;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ReferentielDeForfaitsFourni;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.EligibiliteParLaCouverture;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommationsDeLaSeanceDuStock;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Traducteurs de l'ACL (tables de correspondance) : modèle publié des autres contextes vers le modèle de la Séance. */
class TraducteursDeLAclTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final PatientId PATIENT = new PatientId(new UUID(1, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    @AfterEach
    void viderLaSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_translate_each_cause_of_the_coverage_in_the_order_of_the_three_stages() {
        var correspondances = Map.of(
                CouvertureDesPatients.CauseDeNonFacturation.AUCUNE_PEC_COUVRANTE, CauseDeNonFacturation.PRISE_EN_CHARGE,
                CouvertureDesPatients.CauseDeNonFacturation.PEC_NON_VALIDEE, CauseDeNonFacturation.PRISE_EN_CHARGE_NON_VALIDEE,
                CouvertureDesPatients.CauseDeNonFacturation.ATTESTATION_ABSENTE, CauseDeNonFacturation.ATTESTATION_DE_DROITS);
        assertThat(correspondances).hasSameSizeAs(CouvertureDesPatients.CauseDeNonFacturation.values());
        correspondances.forEach((publiee, attendue) -> {
            var couverture = Mockito.mock(CouvertureDesPatients.class);
            Mockito.when(couverture.causeDeNonFacturation(CENTRE.valeur(), PATIENT.valeur(), JOUR)).thenReturn(Optional.of(publiee));
            assertThat(new EligibiliteParLaCouverture(couverture).cause(CENTRE, PATIENT, JOUR)).contains(attendue);
        });
    }

    @Test
    void should_report_a_billable_patient_as_no_cause() {
        var couverture = Mockito.mock(CouvertureDesPatients.class);
        Mockito.when(couverture.causeDeNonFacturation(CENTRE.valeur(), PATIENT.valeur(), JOUR)).thenReturn(Optional.empty());
        assertThat(new EligibiliteParLaCouverture(couverture).cause(CENTRE, PATIENT, JOUR)).isEmpty();
    }

    @Test
    void should_translate_the_consumption_history_of_the_stock_keeping_its_order_and_values() {
        UUID article = UUID.randomUUID();
        UUID seance = UUID.randomUUID();
        var stock = Mockito.mock(ConsommationsDeSeance.class);
        Mockito.when(stock.historique(CENTRE.valeur(), seance)).thenReturn(List.of(
                new ConsommationsDeSeance.LigneHistorique(article, "DIAL", "Dialyseur", "u", new BigDecimal("2"), new BigDecimal("10.50"),
                        new BigDecimal("21.00")),
                new ConsommationsDeSeance.LigneHistorique(UUID.randomUUID(), "AIG", "Aiguille", "u", BigDecimal.ONE, null, null)));

        var lignes = new ConsommationsDeLaSeanceDuStock(stock).historique(CENTRE, new SeanceId(seance));

        assertThat(lignes).extracting(l -> l.code()).containsExactly("DIAL", "AIG");
        assertThat(lignes.getFirst().article()).isEqualTo(article);
        assertThat(lignes.getFirst().libelle()).isEqualTo("Dialyseur");
        assertThat(lignes.getFirst().totalValorise()).isEqualByComparingTo("21.00");
        assertThat(lignes.get(1).valeurUnitaire()).isNull();
    }

    @Test
    void should_translate_the_forfaits_of_the_referential_turning_the_libelle_into_the_name_and_keeping_the_price_as_is() {
        UUID forfait = UUID.randomUUID();
        var referentiel = Mockito.mock(ReferentielDeForfaitsFourni.class);
        Mockito.when(referentiel.forfait(CENTRE.valeur(), forfait)).thenReturn(
                Optional.of(new ReferentielDeForfaitsFourni.Forfait(forfait, "F1", "Libelle du forfait", new BigDecimal("12.500"))));
        UUID sansPrix = UUID.randomUUID();
        Mockito.when(referentiel.forfait(CENTRE.valeur(), sansPrix)).thenReturn(
                Optional.of(new ReferentielDeForfaitsFourni.Forfait(sansPrix, null, "Sans prix", null)));
        UUID inconnu = UUID.randomUUID();
        Mockito.when(referentiel.forfait(CENTRE.valeur(), inconnu)).thenReturn(Optional.empty());
        var acl = new ForfaitsDuCentreParLeLegacy(referentiel);

        var traduit = acl.trouver(CENTRE, new ForfaitId(forfait)).orElseThrow();
        assertThat(traduit.id()).isEqualTo(new ForfaitId(forfait));
        assertThat(traduit.code()).isEqualTo("F1");
        assertThat(traduit.nom()).isEqualTo("Libelle du forfait");
        assertThat(traduit.prix()).isEqualTo(new BigDecimal("12.500")); // aucune normalisation d'échelle ni arrondi
        assertThat(acl.trouver(CENTRE, new ForfaitId(sansPrix)).orElseThrow().prix()).isNull();
        assertThat(acl.trouver(CENTRE, new ForfaitId(inconnu))).isEmpty();
    }

    /** Principal de test : le legacy expose {@code getCenterId()} (texte). */
    public static class Principal {
        private final String centre;

        Principal(String centre) {
            this.centre = centre;
        }

        public String getCenterId() {
            return centre;
        }
    }

    private static void connecter(String centre, String... roles) {
        var autorites = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new Principal(centre), "x", autorites) {
            @Override
            public String getName() {
                return "u-test";
            }
        });
    }

    @Test
    void should_take_the_centre_and_the_author_of_the_session() {
        connecter(CENTRE.valeur().toString(), "ROLE_INFIRMIER");
        var session = new SessionLegacy();

        assertThat(session.centre(null)).isEqualTo(CENTRE);
        assertThat(session.centre(CENTRE.valeur())).isEqualTo(CENTRE);
        assertThat(session.utilisateur()).isEqualTo("u-test");
    }

    @Test
    void should_refuse_a_centre_that_is_not_the_one_of_the_session() {
        connecter(CENTRE.valeur().toString(), "ROLE_INFIRMIER");
        assertThatThrownBy(() -> new SessionLegacy().centre(UUID.randomUUID())).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_refuse_a_missing_or_invalid_session() {
        assertThatThrownBy(() -> new SessionLegacy().centre(null)).isInstanceOf(AccesRefuse.class);
        connecter(null, "ROLE_INFIRMIER");
        assertThatThrownBy(() -> new SessionLegacy().centre(null)).isInstanceOf(AccesRefuse.class);
        connecter("pas-un-uuid", "ROLE_INFIRMIER");
        assertThatThrownBy(() -> new SessionLegacy().centre(null)).isInstanceOf(AccesRefuse.class);
        assertThat(new SessionLegacy().utilisateur()).isEqualTo("u-test");
    }

    @Test
    void should_let_the_cross_centre_administrator_choose_the_centre() {
        connecter(null, "ROLE_SUPERADMIN");
        var session = new SessionLegacy();
        assertThat(session.centre(CENTRE.valeur())).isEqualTo(CENTRE);
        assertThatThrownBy(() -> session.centre(null)).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_tell_who_validates_at_the_scan_and_who_is_administrator() {
        var session = new SessionLegacy();
        connecter(CENTRE.valeur().toString(), "ROLE_ADMIN");
        assertThat(session.estAdministrateur()).isTrue();
        assertThat(session.valideAuScan()).isTrue();
        connecter(CENTRE.valeur().toString(), "ROLE_INFIRMIER");
        assertThat(session.estAdministrateur()).isFalse();
        assertThat(session.valideAuScan()).isTrue();
        connecter(CENTRE.valeur().toString(), "ROLE_SECRETAIRE");
        assertThat(session.valideAuScan()).isFalse();
        connecter(CENTRE.valeur().toString(), "ROLE_MEDECIN");
        assertThat(session.valideAuScan()).isFalse();
    }

    @Test
    void should_name_the_author_system_without_a_session() {
        assertThat(new SessionLegacy().utilisateur()).isEqualTo("system");
        assertThat(new SessionLegacy().valideAuScan()).isFalse();
    }
}
