package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cycle de vie de l'équipement (R1 à R8) et comportements du legacy reproduits (KB-GMAO-33 à 41). */
class EquipementTest {

    private static final Instant T0 = Instant.parse("2026-10-11T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-11T09:30:00Z");
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final UtilisateurId AUTRE = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f2"));
    private static final EquipementId ID = new EquipementId(UUID.fromString("00000000-0000-0000-0000-000000000e01"));
    private static final SalleId SALLE = new SalleId(UUID.fromString("00000000-0000-0000-0000-0000000005a1"));

    private static Equipement unGenerateur() {
        return Equipement.enregistrer(ID, CENTRE, new CodeEquipement(" G-01 "), "Générateur 01",
                TypeEquipement.GENERATEUR_DIALYSE, "Fresenius", "5008S", "SN-1", Instant.parse("2024-03-01T08:00:00Z"),
                "Salle 1", SALLE, PrixAcquisition.ouAucun(new BigDecimal("15000.5")), ADMIN, T0);
    }

    private static Equipement dansLeStatut(StatutEquipement statut) {
        return Equipement.reconstituer(ID, CENTRE, new CodeEquipement("G-01"), "Générateur 01",
                TypeEquipement.GENERATEUR_DIALYSE, null, null, null, T0, statut, null, null, null, null, T0, null, ADMIN,
                null);
    }

    // ------------------------------------------------------------------ enregistrement

    @Test
    void should_register_an_equipment_in_service_with_the_first_history_line_and_an_event() {
        Equipement equipement = unGenerateur();

        assertThat(equipement.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(equipement.code().valeur()).isEqualTo(" G-01 ");
        assertThat(equipement.creeLe()).isEqualTo(T0);
        assertThat(equipement.creePar()).isEqualTo(ADMIN);
        assertThat(equipement.modifieLe()).isNull();
        assertThat(equipement.observations()).isNull();
        assertThat(equipement.tirerChangementsDeStatut()).containsExactly(
                new ChangementDeStatut(null, StatutEquipement.EN_SERVICE, null, ADMIN, T0));
        assertThat(equipement.tirerEvenements()).containsExactly(
                new EquipementEnregistre(ID, CENTRE, TypeEquipement.GENERATEUR_DIALYSE, new CodeEquipement(" G-01 "), SALLE,
                        ADMIN, T0));
    }

    @Test
    void should_hand_over_events_and_history_only_once() {
        Equipement equipement = unGenerateur();
        equipement.tirerEvenements();
        equipement.tirerChangementsDeStatut();

        assertThat(equipement.tirerEvenements()).isEmpty();
        assertThat(equipement.tirerChangementsDeStatut()).isEmpty();
    }

    @Test
    void should_require_the_designation_the_type_the_installation_date_the_code_and_the_centre() {
        assertThatThrownBy(() -> Equipement.enregistrer(ID, CENTRE, new CodeEquipement("X"), " ", TypeEquipement.AUTRE, null,
                null, null, T0, null, null, null, ADMIN, T0)).hasMessage("Désignation requise");
        assertThatThrownBy(() -> Equipement.enregistrer(ID, CENTRE, new CodeEquipement("X"), "D", null, null, null, null, T0,
                null, null, null, ADMIN, T0)).hasMessage("Type d'équipement requis");
        assertThatThrownBy(() -> Equipement.enregistrer(ID, CENTRE, new CodeEquipement("X"), "D", TypeEquipement.AUTRE, null,
                null, null, null, null, null, null, ADMIN, T0)).hasMessage("Date d'installation requise");
        assertThatThrownBy(() -> Equipement.enregistrer(ID, null, new CodeEquipement("X"), "D", TypeEquipement.AUTRE, null,
                null, null, T0, null, null, null, ADMIN, T0)).hasMessage("Centre requis");
        assertThatThrownBy(() -> Equipement.enregistrer(ID, CENTRE, null, "D", TypeEquipement.AUTRE, null, null, null, T0,
                null, null, null, ADMIN, T0)).hasMessage("Code requis");
        assertThatThrownBy(() -> new CodeEquipement("  ")).hasMessage("Code requis");
    }

    @Test
    void should_accept_a_zero_price_and_refuse_a_negative_one() {
        assertThat(new PrixAcquisition(BigDecimal.ZERO).valeur()).isEqualByComparingTo("0");
        assertThat(PrixAcquisition.ouAucun(null)).isNull();
        assertThatThrownBy(() -> new PrixAcquisition(new BigDecimal("-0.01")))
                .isInstanceOf(PrixNegatif.class).hasMessage("Le prix d'acquisition ne peut pas être négatif");
    }

    @Test
    void should_recognise_the_type_code_exactly() {
        assertThat(TypeEquipement.depuis("STATION_TRAITEMENT_EAU")).isEqualTo(TypeEquipement.STATION_TRAITEMENT_EAU);
        assertThatThrownBy(() -> TypeEquipement.depuis("generateur_dialyse")).isInstanceOf(TypeEquipementInconnu.class);
        assertThat(TypeEquipement.GENERATEUR_DIALYSE.estGenerateurDeDialyse()).isTrue();
        assertThat(TypeEquipement.AUTRE.estGenerateurDeDialyse()).isFalse();
    }

    // ------------------------------------------------------------------ correction de l'identification (R5)

    @Test
    void should_replace_the_identification_entirely_and_leave_code_type_date_and_status_untouched() {
        Equipement equipement = unGenerateur();
        equipement.tirerEvenements();
        equipement.tirerChangementsDeStatut();

        equipement.corrigerIdentification("Nouveau nom", null, null, null, null, null, null, AUTRE, T1);

        assertThat(equipement.designation()).isEqualTo("Nouveau nom");
        assertThat(equipement.fabricant()).isNull();
        assertThat(equipement.salleId()).isNull();
        assertThat(equipement.prixAcquisition()).isNull();
        assertThat(equipement.code().valeur()).isEqualTo(" G-01 ");
        assertThat(equipement.type()).isEqualTo(TypeEquipement.GENERATEUR_DIALYSE);
        assertThat(equipement.dateInstallation()).isEqualTo(Instant.parse("2024-03-01T08:00:00Z"));
        assertThat(equipement.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(equipement.modifieLe()).isEqualTo(T1);
        assertThat(equipement.modifiePar()).isEqualTo(AUTRE);
        assertThat(equipement.tirerChangementsDeStatut()).isEmpty();
        assertThat(equipement.tirerEvenements()).containsExactly(
                new IdentificationEquipementCorrigee(ID, CENTRE, TypeEquipement.GENERATEUR_DIALYSE, null, AUTRE, T1));
    }

    @Test
    void should_still_correct_the_identification_of_a_reformed_equipment() {
        Equipement equipement = dansLeStatut(StatutEquipement.REFORME);

        equipement.corrigerIdentification("Renommé", null, null, null, null, null, null, ADMIN, T1);

        assertThat(equipement.designation()).isEqualTo("Renommé");
        assertThat(equipement.statut()).isEqualTo(StatutEquipement.REFORME);
    }

    @Test
    void should_require_a_designation_to_correct_the_identification() {
        assertThatThrownBy(() -> unGenerateur().corrigerIdentification("", null, null, null, null, null, null, ADMIN, T1))
                .hasMessage("Désignation requise");
    }

    // ------------------------------------------------------------------ mise hors service (R1, R2)

    @Test
    void should_put_out_of_service_with_the_reason_in_the_observations_and_the_whole_observations_as_history_motive() {
        Equipement equipement = unGenerateur();
        equipement.tirerEvenements();
        equipement.tirerChangementsDeStatut();

        equipement.mettreHorsService("Pompe HS", AUTRE, T1);

        assertThat(equipement.statut()).isEqualTo(StatutEquipement.HORS_SERVICE);
        assertThat(equipement.observations()).isEqualTo("Hors service: Pompe HS");
        assertThat(equipement.modifiePar()).isEqualTo(AUTRE);
        assertThat(equipement.tirerChangementsDeStatut()).containsExactly(new ChangementDeStatut(
                StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, "Hors service: Pompe HS", AUTRE, T1));
        assertThat(equipement.tirerEvenements()).containsExactly(new EquipementMisHorsService(ID, CENTRE,
                TypeEquipement.GENERATEUR_DIALYSE, new CodeEquipement(" G-01 "), StatutEquipement.EN_SERVICE,
                StatutEquipement.HORS_SERVICE, CauseDIndisponibilite.MISE_HORS_SERVICE, AUTRE, T1));
    }

    @Test
    void should_accumulate_the_observations_with_a_semicolon_when_changing_status() {
        Equipement equipement = dansLeStatut(StatutEquipement.EN_MAINTENANCE);
        equipement.consignerObservation("Vu par le technicien", ADMIN, T0);

        equipement.mettreHorsService("Irréparable", ADMIN, T1);

        assertThat(equipement.observations()).isEqualTo(T0 + " - Vu par le technicien; Hors service: Irréparable");
    }

    @Test
    void should_refuse_to_put_out_of_service_an_equipment_already_out_of_service() {
        assertThatThrownBy(() -> dansLeStatut(StatutEquipement.HORS_SERVICE).mettreHorsService("x", ADMIN, T1))
                .isInstanceOf(EquipementDejaHorsService.class).hasMessage("L'équipement est déjà hors service");
    }

    @Test
    void should_refuse_to_put_a_reformed_equipment_out_of_service() {
        assertThatThrownBy(() -> dansLeStatut(StatutEquipement.REFORME).mettreHorsService("x", ADMIN, T1))
                .isInstanceOf(EquipementReformeImmuable.class).hasMessage("Un équipement réformé ne peut plus changer de statut");
    }

    @Test
    void should_require_a_reason_to_put_out_of_service() {
        assertThatThrownBy(() -> unGenerateur().mettreHorsService(" ", ADMIN, T1)).hasMessage("Raison requise");
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"EN_SERVICE", "EN_MAINTENANCE", "EN_ATTENTE_PIECE", "DESACTIF", "A_REFORMER"})
    void should_put_out_of_service_from_any_status_other_than_hors_service_and_reforme(StatutEquipement depart) {
        Equipement equipement = dansLeStatut(depart);

        equipement.mettreHorsService("x", ADMIN, T1);

        assertThat(equipement.statut()).isEqualTo(StatutEquipement.HORS_SERVICE);
        assertThat(equipement.tirerChangementsDeStatut()).singleElement().extracting(ChangementDeStatut::precedent).isEqualTo(depart);
    }

    // ------------------------------------------------------------------ remise en service (R1, R3)

    @Test
    void should_return_to_service_only_from_out_of_service_without_touching_the_observations() {
        Equipement equipement = dansLeStatut(StatutEquipement.HORS_SERVICE);

        equipement.remettreEnService(AUTRE, T1);

        assertThat(equipement.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(equipement.observations()).isNull();
        assertThat(equipement.modifiePar()).isEqualTo(AUTRE);
        assertThat(equipement.tirerChangementsDeStatut()).containsExactly(new ChangementDeStatut(
                StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE, null, AUTRE, T1));
        assertThat(equipement.tirerEvenements()).containsExactly(new EquipementRemisEnService(ID, CENTRE,
                TypeEquipement.GENERATEUR_DIALYSE, new CodeEquipement("G-01"), StatutEquipement.HORS_SERVICE, AUTRE, T1));
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"EN_SERVICE", "EN_MAINTENANCE", "EN_ATTENTE_PIECE", "DESACTIF", "A_REFORMER"})
    void should_refuse_to_return_to_service_from_any_status_but_out_of_service(StatutEquipement depart) {
        assertThatThrownBy(() -> dansLeStatut(depart).remettreEnService(ADMIN, T1))
                .isInstanceOf(RemiseEnServiceImpossible.class)
                .hasMessage("Seul un équipement hors service peut être réactivé");
    }

    @Test
    void should_refuse_to_return_a_reformed_equipment_to_service() {
        assertThatThrownBy(() -> dansLeStatut(StatutEquipement.REFORME).remettreEnService(ADMIN, T1))
                .isInstanceOf(EquipementReformeImmuable.class).hasMessage("Un équipement réformé ne peut pas être réactivé");
    }

    // ------------------------------------------------------------------ réforme (R1)

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"REFORME"}, mode = EnumSource.Mode.EXCLUDE)
    void should_reform_from_any_status_and_become_terminal(StatutEquipement depart) {
        Equipement equipement = dansLeStatut(depart);

        equipement.reformer("Fin de vie", AUTRE, T1);

        assertThat(equipement.statut()).isEqualTo(StatutEquipement.REFORME);
        assertThat(equipement.observations()).isEqualTo("Réformé: Fin de vie");
        assertThat(equipement.tirerChangementsDeStatut()).containsExactly(
                new ChangementDeStatut(depart, StatutEquipement.REFORME, "Réformé: Fin de vie", AUTRE, T1));
        assertThat(equipement.tirerEvenements()).containsExactly(new EquipementReforme(ID, CENTRE,
                TypeEquipement.GENERATEUR_DIALYSE, new CodeEquipement("G-01"), depart, AUTRE, T1));
    }

    @Test
    void should_refuse_to_reform_twice() {
        assertThatThrownBy(() -> dansLeStatut(StatutEquipement.REFORME).reformer("x", ADMIN, T1))
                .isInstanceOf(EquipementDejaReforme.class).hasMessage("L'équipement est déjà réformé");
    }

    @Test
    void should_require_a_reason_to_reform() {
        assertThatThrownBy(() -> unGenerateur().reformer(" ", ADMIN, T1)).hasMessage("Motif de réforme requis");
    }

    // ------------------------------------------------------------------ état constaté (R4, KB-GMAO-40)

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE", "A_REFORMER"})
    void should_become_unavailable_with_the_intervention_cause_when_the_observed_state_is_not_in_service(StatutEquipement etat) {
        Equipement equipement = unGenerateur();
        equipement.tirerEvenements();
        equipement.tirerChangementsDeStatut();

        boolean change = equipement.constaterEtat(etat, "Intervention démarrée", AUTRE, T1);

        assertThat(change).isTrue();
        assertThat(equipement.statut()).isEqualTo(etat);
        assertThat(equipement.observations()).isEqualTo("Intervention démarrée");
        assertThat(equipement.tirerChangementsDeStatut()).containsExactly(
                new ChangementDeStatut(StatutEquipement.EN_SERVICE, etat, "Intervention démarrée", AUTRE, T1));
        assertThat(equipement.tirerEvenements()).containsExactly(new EquipementMisHorsService(ID, CENTRE,
                TypeEquipement.GENERATEUR_DIALYSE, new CodeEquipement(" G-01 "), StatutEquipement.EN_SERVICE, etat,
                CauseDIndisponibilite.INTERVENTION, AUTRE, T1));
    }

    @Test
    void should_return_to_service_by_observed_state_from_any_status_even_out_of_service() {
        Equipement equipement = dansLeStatut(StatutEquipement.HORS_SERVICE);

        boolean change = equipement.constaterEtat(StatutEquipement.EN_SERVICE, "Intervention terminée", ADMIN, T1);

        assertThat(change).isTrue();
        assertThat(equipement.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(equipement.tirerEvenements()).singleElement().isInstanceOf(EquipementRemisEnService.class);
    }

    @Test
    void should_do_nothing_when_the_observed_state_is_the_current_one() {
        Equipement equipement = unGenerateur();
        equipement.tirerEvenements();
        equipement.tirerChangementsDeStatut();

        boolean change = equipement.constaterEtat(StatutEquipement.EN_SERVICE, "Intervention démarrée", ADMIN, T1);

        assertThat(change).isFalse();
        assertThat(equipement.observations()).isNull();
        assertThat(equipement.modifieLe()).isNull();
        assertThat(equipement.tirerEvenements()).isEmpty();
        assertThat(equipement.tirerChangementsDeStatut()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"REFORME", "DESACTIF"})
    void should_never_let_an_intervention_set_reforme_or_desactif(StatutEquipement etat) {
        assertThatThrownBy(() -> unGenerateur().constaterEtat(etat, "x", ADMIN, T1))
                .isInstanceOf(EtatNonFixableParIntervention.class)
                .hasMessage("Cet état ne peut pas être fixé par une intervention");
    }

    @Test
    void should_ignore_no_state_change_on_a_reformed_equipment_and_refuse_it() {
        assertThatThrownBy(() -> dansLeStatut(StatutEquipement.REFORME).constaterEtat(StatutEquipement.EN_SERVICE, "x", ADMIN, T1))
                .isInstanceOf(EquipementReformeImmuable.class).hasMessage("Un équipement réformé ne peut plus changer de statut");
        assertThatThrownBy(() -> unGenerateur().constaterEtat(null, "x", ADMIN, T1)).hasMessage("Nouvel état requis");
    }

    // ------------------------------------------------------------------ observations

    @Test
    void should_append_timestamped_observations_separated_by_a_new_line_without_history_or_event() {
        Equipement equipement = unGenerateur();
        equipement.tirerEvenements();
        equipement.tirerChangementsDeStatut();

        equipement.consignerObservation("Bruit anormal", AUTRE, T0);
        equipement.consignerObservation("Revu", AUTRE, T1);

        assertThat(equipement.observations()).isEqualTo(T0 + " - Bruit anormal\n" + T1 + " - Revu");
        assertThat(equipement.modifieLe()).isEqualTo(T1);
        assertThat(equipement.modifiePar()).isEqualTo(AUTRE);
        assertThat(equipement.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(equipement.tirerEvenements()).isEmpty();
        assertThat(equipement.tirerChangementsDeStatut()).isEmpty();
    }

    @Test
    void should_accept_an_observation_on_a_reformed_equipment_and_require_a_text() {
        Equipement reforme = dansLeStatut(StatutEquipement.REFORME);
        reforme.consignerObservation("Archivé", ADMIN, T1);

        assertThat(reforme.observations()).isEqualTo(T1 + " - Archivé");
        assertThatThrownBy(() -> reforme.consignerObservation("", ADMIN, T1)).hasMessage("Observation requise");
    }

    @Test
    void should_record_the_whole_observations_as_motive_of_a_later_status_change() {
        Equipement equipement = unGenerateur();
        equipement.consignerObservation("Bruit", ADMIN, T0);
        equipement.tirerChangementsDeStatut();

        equipement.mettreHorsService("Panne", ADMIN, T1);

        assertThat(equipement.tirerChangementsDeStatut()).singleElement().extracting(ChangementDeStatut::motif)
                .isEqualTo(T0 + " - Bruit; Hors service: Panne");
    }

    @Test
    void should_know_the_two_unavailability_causes() {
        assertThat(List.of(CauseDIndisponibilite.values())).containsExactly(
                CauseDIndisponibilite.MISE_HORS_SERVICE, CauseDIndisponibilite.INTERVENTION);
    }
}
