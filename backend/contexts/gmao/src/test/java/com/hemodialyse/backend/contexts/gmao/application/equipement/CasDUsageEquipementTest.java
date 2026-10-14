package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.doubles.CapaciteDesSallesEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.gmao.doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.gmao.doubles.ParcEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PublicateurEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CauseDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipementDejaUtilise;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementEnregistre;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRemisEnService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementReforme;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.IdentificationEquipementCorrigee;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipementInconnu;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.UniciteDuCodeEquipement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage du cycle de vie de l'équipement (G3) sur adapters in-memory : R1 à R8 et comportements reproduits du legacy. */
class CasDUsageEquipementTest {

    private static final Instant T0 = Instant.parse("2026-10-11T09:00:00Z");
    private static final Instant INSTALLATION = Instant.parse("2024-03-01T08:00:00Z");
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final SalleId SALLE = new SalleId(UUID.fromString("00000000-0000-0000-0000-0000000005a1"));

    private ParcEnMemoire parc;
    private CapaciteDesSallesEnMemoire salles;
    private PublicateurEnMemoire publicateur;
    private UniteDeTravailDirecte unite;
    private EnregistrerEquipement enregistrer;
    private CorrigerIdentification corriger;
    private MettreHorsService mettreHorsService;
    private RemettreEnService remettreEnService;
    private Reformer reformer;
    private ConsignerObservation consignerObservation;
    private ConstaterEtat constaterEtat;
    private ConsulterEquipement consulter;
    private ListerParc lister;

    @BeforeEach
    void setUp() {
        parc = new ParcEnMemoire();
        salles = new CapaciteDesSallesEnMemoire();
        publicateur = new PublicateurEnMemoire();
        unite = new UniteDeTravailDirecte();
        HorlogeFixe horloge = new HorlogeFixe(T0);
        enregistrer = new EnregistrerEquipement(parc, new UniciteDuCodeEquipement(parc), salles, publicateur, horloge,
                new IdentifiantsSequentiels(), unite);
        corriger = new CorrigerIdentification(parc, salles, publicateur, horloge, unite);
        mettreHorsService = new MettreHorsService(parc, publicateur, horloge, unite);
        remettreEnService = new RemettreEnService(parc, publicateur, horloge, unite);
        reformer = new Reformer(parc, publicateur, horloge, unite);
        consignerObservation = new ConsignerObservation(parc, horloge, unite);
        constaterEtat = new ConstaterEtat(parc, publicateur, horloge, unite);
        consulter = new ConsulterEquipement(parc);
        lister = new ListerParc(parc);
    }

    private EquipementVue unEquipement(CentreId centre, String code, String type, UUID salle) {
        return enregistrer.executer(new EnregistrerEquipementCommande(centre, code, "Désignation " + code, type, null, null,
                null, INSTALLATION, null, salle, new BigDecimal("1234.5"), ADMIN));
    }

    private static EquipementId id(EquipementVue vue) {
        return new EquipementId(vue.id());
    }

    // ------------------------------------------------------------------ enregistrement

    @Test
    void should_register_an_equipment_in_service_write_its_history_and_publish_the_event_in_one_unit_of_work() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", SALLE.valeur());

        assertThat(vue.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(vue.centreId()).isEqualTo(CENTRE.valeur());
        assertThat(vue.creePar()).isEqualTo(ADMIN.valeur());
        assertThat(vue.prixAcquisition()).isEqualTo(new BigDecimal("1234.5"));
        assertThat(parc.historiqueDe(id(vue))).containsExactly(
                new ChangementDeStatut(null, StatutEquipement.EN_SERVICE, null, ADMIN, T0));
        assertThat(publicateur.publies).singleElement().isInstanceOf(EquipementEnregistre.class);
        assertThat(unite.executions).isEqualTo(1);
        assertThat(salles.verifications).hasSize(1);
    }

    @Test
    void should_refuse_a_code_already_used_in_the_centre_but_accept_it_in_another_centre_and_in_another_case() {
        unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", null);

        assertThatThrownBy(() -> unEquipement(CENTRE, "G-01", "AUTRE", null)).isInstanceOf(CodeEquipementDejaUtilise.class);
        assertThat(unEquipement(AUTRE_CENTRE, "G-01", "AUTRE", null).code()).isEqualTo("G-01");
        assertThat(unEquipement(CENTRE, "g-01", "AUTRE", null).code()).isEqualTo("g-01");
        assertThat(unEquipement(CENTRE, " G-01", "AUTRE", null).code()).isEqualTo(" G-01");
    }

    @Test
    void should_check_the_code_before_the_type_as_the_legacy_does() {
        unEquipement(CENTRE, "G-01", "AUTRE", null);

        assertThatThrownBy(() -> unEquipement(CENTRE, "G-01", "inconnu", null)).isInstanceOf(CodeEquipementDejaUtilise.class);
        assertThatThrownBy(() -> unEquipement(CENTRE, "G-02", "generateur_dialyse", null))
                .isInstanceOf(TypeEquipementInconnu.class);
    }

    @Test
    void should_check_the_room_capacity_for_a_dialysis_generator_only() {
        salles.saturee(SALLE);

        assertThatThrownBy(() -> unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", SALLE.valeur()))
                .isInstanceOf(CapaciteDesSallesEnMemoire.SalleSaturee.class);
        assertThat(parc.duCentre(CENTRE, null, 0, 20).items()).isEmpty();
        assertThat(publicateur.publies).isEmpty();

        assertThat(unEquipement(CENTRE, "E-01", "STATION_TRAITEMENT_EAU", SALLE.valeur()).salleId()).isEqualTo(SALLE.valeur());
        assertThat(salles.verifications).hasSize(1);
    }

    // ------------------------------------------------------------------ correction

    @Test
    void should_correct_the_identification_with_a_total_replacement_and_publish_the_event() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", SALLE.valeur());
        publicateur.publies.clear();

        EquipementVue corrige = corriger.executer(new CorrigerIdentificationCommande(id(vue), CENTRE, "Renommé", null, null,
                null, null, null, null, ADMIN));

        assertThat(corrige.designation()).isEqualTo("Renommé");
        assertThat(corrige.salleId()).isNull();
        assertThat(corrige.prixAcquisition()).isNull();
        assertThat(corrige.code()).isEqualTo("G-01");
        assertThat(parc.historiqueDe(id(vue))).hasSize(1);
        assertThat(publicateur.publies).singleElement().isInstanceOf(IdentificationEquipementCorrigee.class);
    }

    @Test
    void should_check_the_capacity_of_the_new_room_excluding_the_generator_itself() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", null);
        salles.saturee(SALLE);

        assertThatThrownBy(() -> corriger.executer(new CorrigerIdentificationCommande(id(vue), CENTRE, "D", null, null, null,
                null, SALLE.valeur(), null, ADMIN))).isInstanceOf(CapaciteDesSallesEnMemoire.SalleSaturee.class);
        assertThat(salles.verifications.get(salles.verifications.size() - 1)).endsWith("/" + id(vue));
    }

    @Test
    void should_refuse_an_unknown_equipment_and_an_equipment_of_another_centre_the_same_way() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "AUTRE", null);
        EquipementId inconnu = new EquipementId(UUID.fromString("00000000-0000-0000-0000-00000000dead"));

        assertThatThrownBy(() -> corriger.executer(new CorrigerIdentificationCommande(inconnu, CENTRE, "D", null, null, null,
                null, null, null, ADMIN))).isInstanceOf(EquipementIntrouvable.class).hasMessage("Équipement non trouvé");
        assertThatThrownBy(() -> mettreHorsService.executer(id(vue), AUTRE_CENTRE, "x", ADMIN))
                .isInstanceOf(EquipementIntrouvable.class);
        assertThatThrownBy(() -> remettreEnService.executer(id(vue), AUTRE_CENTRE, ADMIN)).isInstanceOf(EquipementIntrouvable.class);
        assertThatThrownBy(() -> reformer.executer(id(vue), AUTRE_CENTRE, "x", ADMIN)).isInstanceOf(EquipementIntrouvable.class);
        assertThatThrownBy(() -> consignerObservation.executer(id(vue), AUTRE_CENTRE, "x", ADMIN))
                .isInstanceOf(EquipementIntrouvable.class);
        assertThatThrownBy(() -> constaterEtat.constaterEtat(vue.id(), AUTRE_CENTRE.valeur(), "HORS_SERVICE", "m", ADMIN.valeur()))
                .isInstanceOf(EquipementIntrouvable.class);
    }

    // ------------------------------------------------------------------ cycle de vie

    @Test
    void should_put_out_of_service_then_return_to_service_with_history_and_events() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", null);
        publicateur.publies.clear();

        EquipementVue horsService = mettreHorsService.executer(id(vue), CENTRE, "Pompe HS", ADMIN);
        EquipementVue remis = remettreEnService.executer(id(vue), CENTRE, ADMIN);

        assertThat(horsService.statut()).isEqualTo(StatutEquipement.HORS_SERVICE);
        assertThat(horsService.observations()).isEqualTo("Hors service: Pompe HS");
        assertThat(remis.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(parc.historiqueDe(id(vue))).extracting(ChangementDeStatut::nouveau)
                .containsExactly(StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE);
        assertThat(publicateur.publies).hasSize(2);
        assertThat(publicateur.publies.get(0)).isInstanceOf(EquipementMisHorsService.class);
        assertThat(((EquipementMisHorsService) publicateur.publies.get(0)).cause()).isEqualTo(CauseDIndisponibilite.MISE_HORS_SERVICE);
        assertThat(publicateur.publies.get(1)).isInstanceOf(EquipementRemisEnService.class);
    }

    @Test
    void should_write_nothing_and_publish_nothing_when_a_rule_refuses_the_command() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", null);
        reformer.executer(id(vue), CENTRE, "Fin de vie", ADMIN);
        publicateur.publies.clear();

        assertThatThrownBy(() -> mettreHorsService.executer(id(vue), CENTRE, "x", ADMIN)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> remettreEnService.executer(id(vue), CENTRE, ADMIN)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reformer.executer(id(vue), CENTRE, "x", ADMIN)).isInstanceOf(IllegalStateException.class);

        assertThat(parc.historiqueDe(id(vue))).hasSize(2);
        assertThat(publicateur.publies).isEmpty();
    }

    @Test
    void should_reform_and_publish_the_terminal_event_with_the_previous_status() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", null);
        publicateur.publies.clear();

        EquipementVue reforme = reformer.executer(id(vue), CENTRE, "Fin de vie", ADMIN);

        assertThat(reforme.statut()).isEqualTo(StatutEquipement.REFORME);
        assertThat(publicateur.publies).singleElement().isInstanceOf(EquipementReforme.class);
        assertThat(((EquipementReforme) publicateur.publies.get(0)).statutPrecedent()).isEqualTo(StatutEquipement.EN_SERVICE);
    }

    @Test
    void should_consign_observations_on_a_reformed_equipment_without_event_or_history() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "AUTRE", null);
        reformer.executer(id(vue), CENTRE, "Fin de vie", ADMIN);
        publicateur.publies.clear();

        EquipementVue avecObservation = consignerObservation.executer(id(vue), CENTRE, "Archivé", ADMIN);

        assertThat(avecObservation.observations()).isEqualTo("Réformé: Fin de vie\n" + T0 + " - Archivé");
        assertThat(publicateur.publies).isEmpty();
        assertThat(parc.historiqueDe(id(vue))).hasSize(2);
    }

    // ------------------------------------------------------------------ état constaté par une intervention

    @Test
    void should_apply_the_observed_state_with_the_intervention_motive_in_the_history_and_the_cause_in_the_event() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", null);
        publicateur.publies.clear();

        constaterEtat.constaterEtat(vue.id(), CENTRE.valeur(), "EN_MAINTENANCE", "Intervention démarrée", ADMIN.valeur());

        assertThat(parc.trouverParId(id(vue)).orElseThrow().statut()).isEqualTo(StatutEquipement.EN_MAINTENANCE);
        assertThat(parc.historiqueDe(id(vue))).last().isEqualTo(new ChangementDeStatut(StatutEquipement.EN_SERVICE,
                StatutEquipement.EN_MAINTENANCE, "Intervention démarrée", ADMIN, T0));
        assertThat(publicateur.publies).singleElement().isInstanceOf(EquipementMisHorsService.class);
        assertThat(((EquipementMisHorsService) publicateur.publies.get(0)).cause()).isEqualTo(CauseDIndisponibilite.INTERVENTION);
    }

    @Test
    void should_do_nothing_when_the_observed_state_is_the_current_state() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "GENERATEUR_DIALYSE", null);
        publicateur.publies.clear();

        constaterEtat.constaterEtat(vue.id(), CENTRE.valeur(), "EN_SERVICE", "Intervention démarrée", ADMIN.valeur());

        assertThat(parc.historiqueDe(id(vue))).hasSize(1);
        assertThat(publicateur.publies).isEmpty();
    }

    @Test
    void should_refuse_a_state_an_intervention_may_not_set_and_a_reformed_equipment() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "AUTRE", null);

        assertThatThrownBy(() -> constaterEtat.constaterEtat(vue.id(), CENTRE.valeur(), "REFORME", "m", ADMIN.valeur()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cet état ne peut pas être fixé par une intervention");
        reformer.executer(id(vue), CENTRE, "Fin de vie", ADMIN);
        assertThatThrownBy(() -> constaterEtat.constaterEtat(vue.id(), CENTRE.valeur(), "EN_SERVICE", "m", ADMIN.valeur()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Un équipement réformé ne peut plus changer de statut");
    }

    // ------------------------------------------------------------------ lectures

    @Test
    void should_consult_an_equipment_of_the_centre_only() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "AUTRE", null);

        assertThat(consulter.executer(id(vue), CENTRE)).get().extracting(EquipementVue::id).isEqualTo(vue.id());
        assertThat(consulter.executer(id(vue), AUTRE_CENTRE)).isEmpty();
    }

    @Test
    void should_consult_a_logically_deleted_equipment_but_not_list_it() {
        EquipementVue vue = unEquipement(CENTRE, "G-01", "AUTRE", null);
        parc.supprimerLogiquement(id(vue));

        assertThat(consulter.executer(id(vue), CENTRE)).isPresent();
        assertThat(lister.executer(CENTRE, null, 0, 20).items()).isEmpty();
    }

    @Test
    void should_list_the_park_of_the_centre_from_newest_to_oldest_filtered_by_exact_status() {
        unEquipement(CENTRE, "G-01", "AUTRE", null);
        EquipementVue hs = unEquipement(CENTRE, "G-02", "AUTRE", null);
        unEquipement(AUTRE_CENTRE, "G-03", "AUTRE", null);
        mettreHorsService.executer(id(hs), CENTRE, "x", ADMIN);

        assertThat(lister.executer(CENTRE, null, 0, 20).items()).extracting(EquipementVue::code).hasSize(2);
        assertThat(lister.executer(CENTRE, "", 0, 20).total()).isEqualTo(2);
        assertThat(lister.executer(CENTRE, "HORS_SERVICE", 0, 20).items()).extracting(EquipementVue::code).containsExactly("G-02");
        assertThat(lister.executer(CENTRE, "hors_service", 0, 20).items()).isEmpty();
        assertThat(lister.executer(CENTRE, null, 5, 20).items()).isEmpty();
    }

    @Test
    void should_refuse_the_pagination_bounds_the_legacy_refuses_and_accept_a_huge_size() {
        assertThatThrownBy(() -> lister.executer(CENTRE, null, -1, 20))
                .hasMessage("Page index must not be less than zero");
        assertThatThrownBy(() -> lister.executer(CENTRE, null, 0, 0)).hasMessage("Page size must not be less than one");
        assertThat(lister.executer(CENTRE, null, 0, 100000).size()).isEqualTo(100000);
    }
}
