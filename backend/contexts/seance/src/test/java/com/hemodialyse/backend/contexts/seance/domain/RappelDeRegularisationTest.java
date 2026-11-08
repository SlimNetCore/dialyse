package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE4 : la fenêtre du rappel (RG-SEA-046), les séances à régulariser et le rappel d'un centre pour un jour du centre. */
class RappelDeRegularisationTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);
    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));

    @Test
    void la_fenetre_couvre_les_sept_jours_passes_la_veille_comprise_et_aujourd_hui_exclu() {
        var fenetre = FenetreDeRegularisation.avant(AUJOURDHUI);

        assertThat(fenetre.du()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(fenetre.au()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(FenetreDeRegularisation.JOURS).isEqualTo(7);
    }

    @ParameterizedTest
    @CsvSource({"-9,false", "-8,false", "-7,true", "-6,true", "-1,true", "0,false", "1,false", "30,false"})
    void la_fenetre_ne_contient_que_les_jours_de_moins_sept_a_moins_un(int decalage, boolean attendu) {
        // KB-SEA-37 reproduit : au-delà de sept jours une séance oubliée n'est plus jamais rappelée
        assertThat(FenetreDeRegularisation.avant(AUJOURDHUI).contient(AUJOURDHUI.plusDays(decalage))).isEqualTo(attendu);
    }

    @Test
    void la_recherche_des_oubliees_ne_retient_que_les_creees_non_deverrouillees_la_plus_ancienne_d_abord() {
        var recherche = FenetreDeRegularisation.avant(AUJOURDHUI).rechercheDesOubliees();

        assertThat(recherche.du()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(recherche.au()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(recherche.statuts()).containsExactly(StatutDeSeance.CREE);
        assertThat(recherche.deverrouillee()).isFalse();
        assertThat(recherche.ordre()).isEqualTo(RechercheDeSeances.OrdreDeTri.DATE);
        assertThat(recherche.decroissant()).isFalse();
        assertThat(recherche.texte()).isNull();
    }

    @Test
    void une_fenetre_inversee_ou_sans_borne_est_refusee() {
        assertThatThrownBy(() -> new FenetreDeRegularisation(AUJOURDHUI, AUJOURDHUI.minusDays(1))).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new FenetreDeRegularisation(null, AUJOURDHUI)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new FenetreDeRegularisation(AUJOURDHUI, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void aucune_seance_a_regulariser_n_a_pas_de_plus_ancienne() {
        assertThat(SeancesARegulariser.aucune().estVide()).isTrue();
        assertThat(SeancesARegulariser.aucune().plusAncienne()).isNull();
        assertThat(new SeancesARegulariser(0, null).estVide()).isTrue();
    }

    @Test
    void des_seances_a_regulariser_portent_leur_nombre_et_la_date_de_la_plus_ancienne() {
        var seances = new SeancesARegulariser(3, LocalDate.of(2026, 6, 9));

        assertThat(seances.estVide()).isFalse();
        assertThat(seances.total()).isEqualTo(3);
        assertThat(seances.plusAncienne()).isEqualTo(LocalDate.of(2026, 6, 9));
    }

    @Test
    void un_nombre_negatif_ou_une_plus_ancienne_sans_seance_est_refuse() {
        assertThatThrownBy(() -> new SeancesARegulariser(-1, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new SeancesARegulariser(0, LocalDate.of(2026, 6, 9))).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void la_plus_ancienne_peut_manquer_quand_la_ligne_est_illisible_mais_comptee() {
        // KB-SEA-07 : l'historique ignore une ligne illisible mais la plateforme l'a comptée ; le legacy envoie alors une date vide
        assertThat(new SeancesARegulariser(1, null).estVide()).isFalse();
    }

    @Test
    void un_centre_avec_des_seances_a_regulariser_est_rappele_pour_le_jour_du_centre() {
        var rappel = RappelDeRegularisation.si(CENTRE, AUJOURDHUI, new SeancesARegulariser(2, LocalDate.of(2026, 6, 10)));

        assertThat(rappel).isPresent();
        assertThat(rappel.get().centre()).isEqualTo(CENTRE);
        assertThat(rappel.get().jour()).isEqualTo(AUJOURDHUI);
        assertThat(rappel.get().seances().total()).isEqualTo(2);
    }

    @Test
    void un_centre_sans_seance_a_regulariser_n_est_pas_rappele() {
        assertThat(RappelDeRegularisation.si(CENTRE, AUJOURDHUI, SeancesARegulariser.aucune())).isEmpty();
    }

    @Test
    void un_rappel_vide_ou_sans_centre_ni_jour_est_refuse() {
        assertThatThrownBy(() -> new RappelDeRegularisation(CENTRE, AUJOURDHUI, SeancesARegulariser.aucune()))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new RappelDeRegularisation(null, AUJOURDHUI, new SeancesARegulariser(1, null)))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new RappelDeRegularisation(CENTRE, null, new SeancesARegulariser(1, null)))
                .isInstanceOf(SaisieInvalide.class);
    }
}
