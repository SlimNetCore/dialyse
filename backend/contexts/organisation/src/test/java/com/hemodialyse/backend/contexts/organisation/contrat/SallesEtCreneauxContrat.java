package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DefinirLesSallesDIsolement;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre.CreneauDuCentre;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.SallesEtCreneauxDuCentre.SalleDuCentre;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des salles, des créneaux et de la commande d'isolement (O4 lot D, ADR 0110) : exécuté par le faux en mémoire et par l'adapter JDBC. */
abstract class SallesEtCreneauxContrat {

    static final UUID CENTRE = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID AUTRE = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID S1 = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID S2 = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    static final UUID S3 = UUID.fromString("00000000-0000-4000-8000-0000000000b3");
    static final UUID S_AUTRE = UUID.fromString("00000000-0000-4000-8000-0000000000b4");
    static final UUID C1 = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID C2 = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID C_AUTRE = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    static final UUID INCONNU = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    protected abstract SallesEtCreneauxDuCentre lectures();

    protected abstract DefinirLesSallesDIsolement commande();

    protected abstract void salle(UUID centre, UUID id, String code, String nom, boolean isolement, Integer capacite);

    protected abstract void creneau(UUID centre, UUID id, String code, String libelle);

    private void referentiel() {
        salle(CENTRE, S2, "S2", "Salle deux", true, 2);
        salle(CENTRE, S1, "S1", "Salle un", false, 3);
        salle(CENTRE, S3, "S3", "Salle trois", false, null);
        salle(AUTRE, S_AUTRE, "S1", "Salle d'ailleurs", true, 5);
        creneau(CENTRE, C2, "P2", "Soir");
        creneau(CENTRE, C1, "P1", "Matin");
        creneau(AUTRE, C_AUTRE, "P1", "Autre centre");
    }

    private Set<UUID> sallesDIsolement(UUID centre) {
        return lectures().sallesParCode(centre).stream().filter(SalleDuCentre::estSalleDIsolement).map(SalleDuCentre::id)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void les_salles_du_centre_sont_triees_par_code_avec_leur_isolement_et_leur_capacite() {
        referentiel();

        assertThat(lectures().sallesParCode(CENTRE)).containsExactly(
                new SalleDuCentre(S1, "S1", "Salle un", false, 3),
                new SalleDuCentre(S2, "S2", "Salle deux", true, 2),
                new SalleDuCentre(S3, "S3", "Salle trois", false, null));
        assertThat(lectures().sallesParCode(INCONNU)).isEmpty();
    }

    @Test
    void une_page_de_salles_porte_sur_le_tri_par_code_et_le_total_sur_tout_le_centre() {
        referentiel();

        SallesEtCreneauxDuCentre.PageDeSalles page = lectures().pageDesSalles(CENTRE, 2, 1);

        assertThat(page.salles()).extracting(SalleDuCentre::id).containsExactly(S2, S3);
        assertThat(page.total()).isEqualTo(3);
        assertThat(lectures().pageDesSalles(CENTRE, 2, 5).salles()).isEmpty();
        assertThat(lectures().pageDesSalles(CENTRE, 2, 5).total()).isEqualTo(3);
        assertThat(lectures().pageDesSalles(CENTRE, 0, 0).salles()).isEmpty();
        assertThat(lectures().pageDesSalles(INCONNU, 20, 0).total()).isZero();
    }

    @Test
    void une_salle_n_existe_que_dans_son_centre() {
        referentiel();

        assertThat(lectures().salleDuCentre(CENTRE, S2)).contains(new SalleDuCentre(S2, "S2", "Salle deux", true, 2));
        assertThat(lectures().salleDuCentre(AUTRE, S2)).isEmpty();
        assertThat(lectures().salleDuCentre(CENTRE, S_AUTRE)).isEmpty();
        assertThat(lectures().salleDuCentre(CENTRE, INCONNU)).isEmpty();
    }

    @Test
    void les_creneaux_sont_tries_par_code_comptes_et_appartiennent_a_leur_centre() {
        referentiel();

        assertThat(lectures().creneauxParCode(CENTRE)).containsExactly(new CreneauDuCentre(C1, "Matin"), new CreneauDuCentre(C2, "Soir"));
        assertThat(lectures().nombreDeCreneaux(CENTRE)).isEqualTo(2);
        assertThat(lectures().nombreDeCreneaux(INCONNU)).isZero();
        assertThat(lectures().contientLeCreneau(CENTRE, C1)).isTrue();
        assertThat(lectures().contientLeCreneau(CENTRE, C_AUTRE)).isFalse();
        assertThat(lectures().contientLeCreneau(CENTRE, INCONNU)).isFalse();
    }

    @Test
    void definir_les_salles_d_isolement_retire_toutes_les_autres_du_centre_sans_toucher_aux_autres_centres() {
        referentiel();

        commande().definir(CENTRE, Set.of(S1, S3));

        assertThat(sallesDIsolement(CENTRE)).containsExactlyInAnyOrder(S1, S3);
        assertThat(sallesDIsolement(AUTRE)).containsExactly(S_AUTRE);
    }

    @Test
    void definir_ignore_sans_erreur_une_salle_inconnue_ou_d_un_autre_centre() {
        referentiel();

        commande().definir(CENTRE, Set.of(S1, INCONNU, S_AUTRE));

        assertThat(sallesDIsolement(CENTRE)).containsExactly(S1);
        assertThat(sallesDIsolement(AUTRE)).containsExactly(S_AUTRE);
    }

    @Test
    void definir_sans_salle_vide_l_isolement_du_centre_et_un_centre_inconnu_ne_change_rien() {
        referentiel();

        commande().definir(INCONNU, Set.of(S1));
        assertThat(sallesDIsolement(CENTRE)).containsExactly(S2);

        commande().definir(CENTRE, Set.of());
        assertThat(sallesDIsolement(CENTRE)).isEmpty();
        assertThat(sallesDIsolement(AUTRE)).containsExactly(S_AUTRE);
    }
}
