package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.application.equipement.PageDEquipements;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodesEquipementDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.PrixAcquisition;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat partagé du repository, des codes et du read model des équipements : exécuté contre le double in-memory et
 * l'adapter JPA sur H2 (même suite : le double est fidèle). Les comportements du legacy reproduits y sont verrouillés :
 * historique écrit avec l'équipement (R8), prix arrondi à deux décimales par la base, longueurs refusées par la base
 * (KB-GMAO-35), unicité (centre, code) sur toutes les lignes, supprimées logiquement comprises (KB-GMAO-33), suppression
 * logique ignorée de la liste et des codes mais lisible par identifiant, et rétablie par une écriture (KB-GMAO-34).
 */
public abstract class EquipementContrat {

    protected static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    protected static final Instant T1 = Instant.parse("2026-03-01T10:00:00Z");
    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997100-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997100-0000-0000-0000-00000000000b"));
    protected static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99997100-0000-0000-0000-0000000000f1"));
    protected static final SalleId SALLE = new SalleId(UUID.fromString("99997100-0000-0000-0000-0000000005a1"));

    private int sequence = 0;

    protected abstract EquipementRepository repository();

    protected abstract CodesEquipementDuCentre codes();

    protected abstract LectureEquipements lecture();

    /** La base sait poser {@code deleted_at} ; aucune commande du domaine ne le fait (KB-GMAO-34). */
    protected abstract void supprimerLogiquement(EquipementId id);

    private EquipementId prochainId() {
        return new EquipementId(UUID.fromString(String.format("99997100-0000-0000-0000-%012d", ++sequence)));
    }

    protected Equipement enregistrer(CentreId centre, String code, Instant creeLe) {
        Equipement equipement = Equipement.enregistrer(prochainId(), centre, new CodeEquipement(code), "Désignation " + code,
                TypeEquipement.GENERATEUR_DIALYSE, "Fresenius", "5008S", "SN-1", T0, "Salle 1", SALLE,
                PrixAcquisition.ouAucun(new BigDecimal("15000.50")), ADMIN, creeLe);
        repository().enregistrer(equipement);
        return equipement;
    }

    private Equipement minimal(String code, String fabricant, BigDecimal prix) {
        return Equipement.enregistrer(prochainId(), CENTRE_A, new CodeEquipement(code), "Minimal",
                TypeEquipement.AUTRE, fabricant, null, null, T0, null, null, PrixAcquisition.ouAucun(prix), ADMIN, T0);
    }

    @Test
    void should_restore_all_fields_of_a_registered_equipment() {
        Equipement equipement = enregistrer(CENTRE_A, " G-01 ", T0);

        Equipement relu = repository().trouverParId(equipement.id()).orElseThrow();

        assertThat(relu.id()).isEqualTo(equipement.id());
        assertThat(relu.centreId()).isEqualTo(CENTRE_A);
        assertThat(relu.code().valeur()).isEqualTo(" G-01 ");
        assertThat(relu.designation()).isEqualTo("Désignation  G-01 ");
        assertThat(relu.type()).isEqualTo(TypeEquipement.GENERATEUR_DIALYSE);
        assertThat(relu.fabricant()).isEqualTo("Fresenius");
        assertThat(relu.modele()).isEqualTo("5008S");
        assertThat(relu.numeroSerie()).isEqualTo("SN-1");
        assertThat(relu.dateInstallation()).isEqualTo(T0);
        assertThat(relu.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(relu.localisation()).isEqualTo("Salle 1");
        assertThat(relu.salleId()).isEqualTo(SALLE);
        assertThat(relu.prixAcquisition().valeur()).isEqualByComparingTo("15000.50");
        assertThat(relu.observations()).isNull();
        assertThat(relu.creeLe()).isEqualTo(T0);
        assertThat(relu.creePar()).isEqualTo(ADMIN);
        assertThat(relu.modifieLe()).isNull();
        assertThat(relu.modifiePar()).isNull();
        assertThat(relu.tirerEvenements()).isEmpty();
        assertThat(relu.tirerChangementsDeStatut()).isEmpty();
    }

    @Test
    void should_accept_the_optional_fields_to_be_absent() {
        Equipement equipement = minimal("M-01", null, null);
        repository().enregistrer(equipement);

        Equipement relu = repository().trouverParId(equipement.id()).orElseThrow();

        assertThat(relu.fabricant()).isNull();
        assertThat(relu.salleId()).isNull();
        assertThat(relu.prixAcquisition()).isNull();
    }

    @Test
    void should_find_nothing_for_an_unknown_identity() {
        assertThat(repository().trouverParId(prochainId())).isEmpty();
        assertThat(lecture().parId(prochainId())).isEmpty();
    }

    @Test
    void should_write_the_history_with_the_equipment_in_the_order_of_the_changes() {
        Equipement equipement = enregistrer(CENTRE_A, "G-01", T0);
        equipement.mettreHorsService("Panne", ADMIN, T1);
        repository().enregistrer(equipement);
        equipement.remettreEnService(ADMIN, Instant.parse("2026-03-01T11:00:00Z"));
        repository().enregistrer(equipement);

        assertThat(repository().historiqueDe(equipement.id())).containsExactly(
                new ChangementDeStatut(null, StatutEquipement.EN_SERVICE, null, ADMIN, T0),
                new ChangementDeStatut(StatutEquipement.EN_SERVICE, StatutEquipement.HORS_SERVICE, "Hors service: Panne", ADMIN, T1),
                new ChangementDeStatut(StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE, "Hors service: Panne", ADMIN,
                        Instant.parse("2026-03-01T11:00:00Z")));
    }

    @Test
    void should_not_write_history_for_a_change_without_status_change() {
        Equipement equipement = enregistrer(CENTRE_A, "G-01", T0);
        equipement.consignerObservation("Bruit", ADMIN, T1);
        equipement.corrigerIdentification("Renommé", null, null, null, null, null, null, ADMIN, T1);
        repository().enregistrer(equipement);

        assertThat(repository().historiqueDe(equipement.id())).hasSize(1);
        Equipement relu = repository().trouverParId(equipement.id()).orElseThrow();
        assertThat(relu.designation()).isEqualTo("Renommé");
        assertThat(relu.salleId()).isNull();
        assertThat(relu.observations()).isEqualTo(T1 + " - Bruit");
        assertThat(relu.modifieLe()).isEqualTo(T1);
        assertThat(relu.modifiePar()).isEqualTo(ADMIN);
        assertThat(relu.creeLe()).isEqualTo(T0);
    }

    @Test
    void should_keep_an_empty_history_for_an_unknown_equipment() {
        assertThat(repository().historiqueDe(prochainId())).isEmpty();
    }

    // ------------------------------------------------------------------ codes (R7)

    @Test
    void should_know_the_codes_used_in_a_centre_compared_exactly() {
        enregistrer(CENTRE_A, "G-01", T0);

        assertThat(codes().estUtilise(CENTRE_A, new CodeEquipement("G-01"))).isTrue();
        assertThat(codes().estUtilise(CENTRE_B, new CodeEquipement("G-01"))).isFalse();
        for (String variante : new String[]{"g-01", " G-01", "G-01 ", "G -01"}) {
            assertThat(codes().estUtilise(CENTRE_A, new CodeEquipement(variante))).as(variante).isFalse();
        }
    }

    @Test
    void should_ignore_the_code_of_a_logically_deleted_equipment_but_the_database_still_refuses_it() {
        Equipement supprime = enregistrer(CENTRE_A, "G-DEL", T0);
        supprimerLogiquement(supprime.id());

        assertThat(codes().estUtilise(CENTRE_A, new CodeEquipement("G-DEL"))).isFalse();
        assertThatThrownBy(() -> enregistrer(CENTRE_A, "G-DEL", T1)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_refuse_the_same_code_twice_in_a_centre_and_accept_it_in_another() {
        enregistrer(CENTRE_A, "G-01", T0);

        assertThatThrownBy(() -> enregistrer(CENTRE_A, "G-01", T1)).isInstanceOf(RuntimeException.class);
        assertThat(enregistrer(CENTRE_B, "G-01", T1).centreId()).isEqualTo(CENTRE_B);
    }

    // ------------------------------------------------------------------ limites de la base (KB-GMAO-35)

    @Test
    void should_accept_the_maximal_lengths_and_refuse_longer_values() {
        repository().enregistrer(minimal("X".repeat(50), "F".repeat(100), null));

        assertThatThrownBy(() -> repository().enregistrer(minimal("C-1", "F".repeat(101), null)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> repository().enregistrer(minimal("X".repeat(51), null, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_round_the_price_to_two_decimals_half_up_and_refuse_an_overflow() {
        Equipement arrondi = minimal("P-1", null, new BigDecimal("12.345"));
        repository().enregistrer(arrondi);
        Equipement entier = minimal("P-2", null, new BigDecimal("1234"));
        repository().enregistrer(entier);
        repository().enregistrer(minimal("P-3", null, new BigDecimal("999999999999.99")));

        assertThat(repository().trouverParId(arrondi.id()).orElseThrow().prixAcquisition().valeur())
                .isEqualTo(new BigDecimal("12.35"));
        assertThat(lecture().parId(entier.id()).orElseThrow().prixAcquisition()).isEqualTo(new BigDecimal("1234.00"));
        assertThatThrownBy(() -> repository().enregistrer(minimal("P-4", null, new BigDecimal("1000000000000"))))
                .isInstanceOf(RuntimeException.class);
    }

    // ------------------------------------------------------------------ suppression logique (KB-GMAO-34)

    @Test
    void should_read_a_logically_deleted_equipment_by_identity_but_not_list_it() {
        Equipement supprime = enregistrer(CENTRE_A, "G-DEL", T0);
        enregistrer(CENTRE_A, "G-01", T1);
        supprimerLogiquement(supprime.id());

        assertThat(repository().trouverParId(supprime.id())).isPresent();
        assertThat(lecture().parId(supprime.id())).isPresent();
        assertThat(lecture().duCentre(CENTRE_A, null, 0, 20).items()).extracting(EquipementVue::code).containsExactly("G-01");
    }

    @Test
    void should_bring_a_logically_deleted_equipment_back_when_it_is_written_again() {
        Equipement supprime = enregistrer(CENTRE_A, "G-DEL", T0);
        supprimerLogiquement(supprime.id());

        Equipement relu = repository().trouverParId(supprime.id()).orElseThrow();
        relu.consignerObservation("Retrouvé", ADMIN, T1);
        repository().enregistrer(relu);

        assertThat(lecture().duCentre(CENTRE_A, null, 0, 20).items()).extracting(EquipementVue::code).containsExactly("G-DEL");
        assertThat(codes().estUtilise(CENTRE_A, new CodeEquipement("G-DEL"))).isTrue();
    }

    // ------------------------------------------------------------------ lecture du parc

    @Test
    void should_list_the_centre_from_newest_to_oldest_with_total_and_page() {
        enregistrer(CENTRE_A, "G-OLD", T0);
        enregistrer(CENTRE_A, "G-NEW", T1);
        enregistrer(CENTRE_B, "G-OTHER", T1);

        PageDEquipements page = lecture().duCentre(CENTRE_A, null, 0, 20);

        assertThat(page.items()).extracting(EquipementVue::code).containsExactly("G-NEW", "G-OLD");
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void should_paginate_and_return_an_empty_page_beyond_the_last() {
        enregistrer(CENTRE_A, "G-1", T0);
        enregistrer(CENTRE_A, "G-2", T1);
        enregistrer(CENTRE_A, "G-3", Instant.parse("2026-03-01T11:00:00Z"));

        assertThat(lecture().duCentre(CENTRE_A, null, 0, 2).items()).extracting(EquipementVue::code).containsExactly("G-3", "G-2");
        assertThat(lecture().duCentre(CENTRE_A, null, 1, 2).items()).extracting(EquipementVue::code).containsExactly("G-1");
        assertThat(lecture().duCentre(CENTRE_A, null, 5, 2).items()).isEmpty();
        assertThat(lecture().duCentre(CENTRE_A, null, 5, 2).total()).isEqualTo(3);
        assertThat(lecture().duCentre(CENTRE_A, null, 0, 100000).items()).hasSize(3);
    }

    @Test
    void should_filter_by_exact_status_code_and_treat_a_blank_status_as_no_filter() {
        Equipement horsService = enregistrer(CENTRE_A, "G-HS", T0);
        horsService.mettreHorsService("Panne", ADMIN, T1);
        repository().enregistrer(horsService);
        enregistrer(CENTRE_A, "G-OK", T1);

        assertThat(lecture().duCentre(CENTRE_A, "HORS_SERVICE", 0, 20).items()).extracting(EquipementVue::code)
                .containsExactly("G-HS");
        assertThat(lecture().duCentre(CENTRE_A, "EN_SERVICE", 0, 20).total()).isEqualTo(1);
        assertThat(lecture().duCentre(CENTRE_A, "hors_service", 0, 20).items()).isEmpty();
        assertThat(lecture().duCentre(CENTRE_A, "INCONNU", 0, 20).items()).isEmpty();
        assertThat(lecture().duCentre(CENTRE_A, "", 0, 20).total()).isEqualTo(2);
        assertThat(lecture().duCentre(CENTRE_A, "  ", 0, 20).total()).isEqualTo(2);
    }

    @Test
    void should_read_the_view_of_an_equipment_by_identity_whatever_its_centre() {
        Equipement equipement = enregistrer(CENTRE_A, "G-01", T0);

        EquipementVue vue = lecture().parId(equipement.id()).orElseThrow();

        assertThat(vue.centreId()).isEqualTo(CENTRE_A.valeur());
        assertThat(vue.code()).isEqualTo("G-01");
        assertThat(vue.statut()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(vue.creePar()).isEqualTo(ADMIN.valeur());
        assertThat(vue.salleId()).isEqualTo(SALLE.valeur());
        assertThat(vue.dateInstallation()).isEqualTo(T0);
    }
}
