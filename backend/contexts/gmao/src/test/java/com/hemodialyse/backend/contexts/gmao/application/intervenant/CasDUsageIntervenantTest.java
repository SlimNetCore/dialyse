package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.gmao.doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.gmao.doubles.IntervenantsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PublicateurEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.CoordonneesIntervenantCorrigees;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantDesactive;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantEnregistre;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureInconnue;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NomRequis;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.TarifIntervenantRevise;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.TarifNegatif;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage du référentiel des intervenants (G2) sur adapters in-memory : règle R24 et comportements reproduits du legacy. */
class CasDUsageIntervenantTest {

    private static final Instant T0 = Instant.parse("2026-10-10T09:00:00Z");
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));

    private IntervenantsEnMemoire intervenants;
    private PublicateurEnMemoire publicateur;
    private UniteDeTravailDirecte unite;
    private EnregistrerIntervenant enregistrer;
    private CorrigerIntervenant corriger;
    private DesactiverIntervenant desactiver;
    private ListerIntervenants lister;
    private ConsulterIntervenant consulter;

    @BeforeEach
    void setUp() {
        intervenants = new IntervenantsEnMemoire();
        publicateur = new PublicateurEnMemoire();
        unite = new UniteDeTravailDirecte();
        HorlogeFixe horloge = new HorlogeFixe(T0);
        enregistrer = new EnregistrerIntervenant(intervenants, publicateur, horloge, new IdentifiantsSequentiels(), unite);
        corriger = new CorrigerIntervenant(intervenants, publicateur, horloge, unite);
        desactiver = new DesactiverIntervenant(intervenants, publicateur, horloge, unite);
        lister = new ListerIntervenants(intervenants);
        consulter = new ConsulterIntervenant(intervenants);
    }

    private IntervenantVue unIntervenant(CentreId centre, String nom, String tarif) {
        return enregistrer.executer(new EnregistrerIntervenantCommande(centre, nom, "INTERNE", "0555", "a@b.test",
                tarif == null ? null : new BigDecimal(tarif), ADMIN));
    }

    // ------------------------------------------------------------------ EnregistrerIntervenant

    @Test
    void should_register_an_active_intervenant_and_publish_IntervenantEnregistre() {
        IntervenantVue vue = unIntervenant(CENTRE, "Bernard", "45.50");

        assertThat(vue.centreId()).isEqualTo(CENTRE.valeur());
        assertThat(vue.nature()).isEqualTo(NatureIntervenant.INTERNE);
        assertThat(vue.actif()).isTrue();
        assertThat(vue.tarifHoraire()).isEqualTo(new BigDecimal("45.50"));
        assertThat(intervenants.trouverParId(new IntervenantId(vue.id()))).isPresent();
        assertThat(publicateur.publies).singleElement().isInstanceOf(IntervenantEnregistre.class);
        assertThat(unite.executions).isEqualTo(1);
    }

    @Test
    void should_answer_the_scale_received_for_the_tarif_while_storing_two_decimals() {
        IntervenantVue vue = unIntervenant(CENTRE, "Echelle", "12.345");

        assertThat(vue.tarifHoraire()).as("réponse : l'objet tel que reçu (KB-GMAO-26)").isEqualTo(new BigDecimal("12.345"));
        assertThat(consulter.executer(new IntervenantId(vue.id()), CENTRE).orElseThrow().tarifHoraire())
                .as("lecture : échelle de la base").isEqualTo(new BigDecimal("12.35"));
    }

    @Test
    void should_recognise_the_nature_before_the_domain_rules() {
        assertThatThrownBy(() -> enregistrer.executer(new EnregistrerIntervenantCommande(CENTRE, " ", "SOUS_TRAITANT", null, null,
                null, ADMIN))).isInstanceOf(NatureInconnue.class);
    }

    @Test
    void should_reject_a_blank_name_and_a_negative_tarif_without_persisting_nor_publishing() {
        assertThatThrownBy(() -> unIntervenant(CENTRE, " ", null)).isInstanceOf(NomRequis.class);
        assertThatThrownBy(() -> unIntervenant(CENTRE, "Négatif", "-0.01")).isInstanceOf(TarifNegatif.class);

        assertThat(lister.executer(CENTRE, 0, 20).total()).isZero();
        assertThat(publicateur.publies).isEmpty();
    }

    @Test
    void should_accept_duplicate_names_in_the_same_centre() {
        unIntervenant(CENTRE, "Doublon", null);
        unIntervenant(CENTRE, "Doublon", null);

        assertThat(lister.executer(CENTRE, 0, 20).total()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ CorrigerIntervenant

    private CorrigerIntervenantCommande correction(IntervenantVue vue, CentreId centre, String nom, String nature, String tarif) {
        return new CorrigerIntervenantCommande(new IntervenantId(vue.id()), centre, nom, nature, null, null,
                tarif == null ? null : new BigDecimal(tarif), ADMIN);
    }

    @Test
    void should_replace_everything_and_erase_the_omitted_phone_email_and_tarif() {
        IntervenantVue vue = unIntervenant(CENTRE, "Avant", "30.00");
        publicateur.publies.clear();

        IntervenantVue corrige = corriger.executer(correction(vue, CENTRE, "Après", "EXTERNE", null));

        assertThat(corrige.nom()).isEqualTo("Après");
        assertThat(corrige.nature()).isEqualTo(NatureIntervenant.EXTERNE);
        assertThat(corrige.telephone()).isNull();
        assertThat(corrige.email()).isNull();
        assertThat(corrige.tarifHoraire()).as("KB-GMAO-30 : tarif omis effacé").isNull();
        assertThat(publicateur.publies).extracting(Object::getClass)
                .containsExactlyInAnyOrder(CoordonneesIntervenantCorrigees.class, TarifIntervenantRevise.class);
    }

    @Test
    void should_refuse_an_unknown_intervenant_and_one_of_another_centre_the_same_way_as_a_bad_request() {
        IntervenantVue autre = unIntervenant(AUTRE_CENTRE, "Autre", null);

        assertThatThrownBy(() -> corriger.executer(correction(autre, CENTRE, "X", "INTERNE", null)))
                .isInstanceOf(IntervenantIntrouvable.class).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Intervenant non trouvé");
        assertThatThrownBy(() -> corriger.executer(new CorrigerIntervenantCommande(new IntervenantId(UUID.randomUUID()), CENTRE,
                "X", "INTERNE", null, null, null, ADMIN))).isInstanceOf(IntervenantIntrouvable.class).hasMessage("Intervenant non trouvé");
        assertThat(consulter.executer(new IntervenantId(autre.id()), AUTRE_CENTRE).orElseThrow().nom()).isEqualTo("Autre");
    }

    @Test
    void should_find_the_intervenant_before_recognising_the_nature_on_a_correction() {
        assertThatThrownBy(() -> corriger.executer(new CorrigerIntervenantCommande(new IntervenantId(UUID.randomUUID()), CENTRE,
                "X", "SOUS_TRAITANT", null, null, null, ADMIN))).isInstanceOf(IntervenantIntrouvable.class);
    }

    @Test
    void should_keep_a_deactivated_intervenant_deactivated_when_corrected() {
        IntervenantVue vue = unIntervenant(CENTRE, "Ancien", null);
        desactiver.executer(new IntervenantId(vue.id()), CENTRE, ADMIN);

        IntervenantVue corrige = corriger.executer(correction(vue, CENTRE, "Ancien modifié", "INTERNE", "10"));

        assertThat(corrige.actif()).isFalse();
    }

    // ------------------------------------------------------------------ DesactiverIntervenant

    @Test
    void should_deactivate_without_ever_deleting_and_answer_the_same_view_when_repeated() {
        IntervenantVue vue = unIntervenant(CENTRE, "A désactiver", "20");
        publicateur.publies.clear();

        IntervenantVue premiere = desactiver.executer(new IntervenantId(vue.id()), CENTRE, ADMIN);
        IntervenantVue seconde = desactiver.executer(new IntervenantId(vue.id()), CENTRE, ADMIN);

        assertThat(premiere.actif()).isFalse();
        assertThat(seconde).isEqualTo(premiere);
        assertThat(publicateur.publies).as("KB-GMAO-25 : idempotent, un seul événement").singleElement()
                .isInstanceOf(IntervenantDesactive.class);
        assertThat(lister.executer(CENTRE, 0, 20).items()).extracting(IntervenantVue::id).containsExactly(vue.id());
    }

    @Test
    void should_refuse_to_deactivate_an_intervenant_of_another_centre() {
        IntervenantVue autre = unIntervenant(AUTRE_CENTRE, "Autre", null);

        assertThatThrownBy(() -> desactiver.executer(new IntervenantId(autre.id()), CENTRE, ADMIN))
                .isInstanceOf(IntervenantIntrouvable.class);
    }

    // ------------------------------------------------------------------ requêtes

    @Test
    void should_consult_an_intervenant_of_the_centre_but_treat_another_centre_as_unknown() {
        IntervenantVue vue = unIntervenant(CENTRE, "Consultable", "10");

        assertThat(consulter.executer(new IntervenantId(vue.id()), CENTRE)).hasValueSatisfying(c -> assertThat(c.nom()).isEqualTo("Consultable"));
        assertThat(consulter.executer(new IntervenantId(vue.id()), AUTRE_CENTRE)).isEmpty();
        assertThat(consulter.executer(new IntervenantId(UUID.randomUUID()), CENTRE)).isEmpty();
    }

    @Test
    void should_list_by_name_with_the_deactivated_ones_and_refuse_out_of_range_paging_like_the_legacy() {
        IntervenantVue b = unIntervenant(CENTRE, "B", null);
        unIntervenant(CENTRE, "A", null);
        desactiver.executer(new IntervenantId(b.id()), CENTRE, ADMIN);
        unIntervenant(AUTRE_CENTRE, "Z", null);

        assertThat(lister.executer(CENTRE, 0, 20).items()).extracting(IntervenantVue::nom).containsExactly("A", "B");
        assertThat(lister.executer(CENTRE, 5, 20).items()).isEmpty();
        assertThat(lister.executer(CENTRE, 0, 100_000).size()).as("KB-GMAO-28 : size non borné").isEqualTo(100_000);
        assertThatThrownBy(() -> lister.executer(CENTRE, -1, 20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page index must not be less than zero");
        assertThatThrownBy(() -> lister.executer(CENTRE, 0, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page size must not be less than one");
    }
}
