package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation.LibelleDeCreneau;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des libellés publiés (O4 lot C, ADR 0109) : exécuté par le faux en mémoire et par l'adapter JDBC. */
abstract class LibellesContrat {

    static final UUID CENTRE = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID AUTRE = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID S1 = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    static final UUID S2 = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    static final UUID S_AUTRE = UUID.fromString("00000000-0000-4000-8000-0000000000b3");
    static final UUID C1 = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    static final UUID C2 = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    static final UUID C_AUTRE = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    static final UUID M1 = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    static final UUID M2 = UUID.fromString("00000000-0000-4000-8000-0000000000d2");
    static final UUID M3 = UUID.fromString("00000000-0000-4000-8000-0000000000d3");
    static final UUID M_DESACTIVE = UUID.fromString("00000000-0000-4000-8000-0000000000d4");
    static final UUID M_AUTRE = UUID.fromString("00000000-0000-4000-8000-0000000000d5");
    static final UUID INCONNU = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    protected abstract LibellesDeLOrganisation libelles();

    protected abstract void salle(UUID centre, UUID id, String code, String nom);

    protected abstract void creneau(UUID centre, UUID id, String code, String libelle);

    protected abstract void medecin(UUID centre, UUID id, String nom, String prenom, boolean actif);

    private void referentiel() {
        salle(CENTRE, S1, "S1", "Salle un");
        salle(CENTRE, S2, "S2", "Salle deux");
        salle(AUTRE, S_AUTRE, "S9", "Salle neuf");
        creneau(CENTRE, C1, "MAT", "Matin");
        creneau(CENTRE, C2, "APM", "Apres-midi");
        creneau(AUTRE, C_AUTRE, "SOIR", "Soir");
        medecin(CENTRE, M1, "Dupont", "Jean", true);
        medecin(CENTRE, M2, "Martin", null, true);
        medecin(CENTRE, M3, "Durand", "   ", true);
        medecin(CENTRE, M_DESACTIVE, "Ancien", "Paul", false);
        medecin(AUTRE, M_AUTRE, "Etranger", "Eve", true);
    }

    @Test
    void les_codes_de_salles_sont_ceux_du_centre_et_un_identifiant_inconnu_ou_d_un_autre_centre_n_a_pas_d_entree() {
        referentiel();

        assertThat(libelles().codesDesSalles(CENTRE, List.of(S1, S2, S_AUTRE, INCONNU)))
                .containsOnly(java.util.Map.entry(S1, "S1"), java.util.Map.entry(S2, "S2"));
    }

    @Test
    void les_creneaux_du_centre_donnent_code_et_libelle_et_ceux_d_un_autre_centre_sont_ignores() {
        referentiel();

        assertThat(libelles().creneauxDuCentre(CENTRE, List.of(C1, C2, C_AUTRE, INCONNU)))
                .containsOnly(java.util.Map.entry(C1, new LibelleDeCreneau("MAT", "Matin")),
                        java.util.Map.entry(C2, new LibelleDeCreneau("APM", "Apres-midi")));
    }

    @Test
    void les_noms_de_salles_et_libelles_de_creneaux_ne_sont_pas_bornes_au_centre() {
        referentiel();

        assertThat(libelles().nomsDesSalles(List.of(S1, S_AUTRE, INCONNU)))
                .containsOnly(java.util.Map.entry(S1, "Salle un"), java.util.Map.entry(S_AUTRE, "Salle neuf"));
        assertThat(libelles().libellesDesCreneaux(List.of(C1, C_AUTRE, INCONNU)))
                .containsOnly(java.util.Map.entry(C1, "Matin"), java.util.Map.entry(C_AUTRE, "Soir"));
    }

    @Test
    void un_lot_vide_ou_fait_d_identifiants_nuls_ne_rend_rien_et_un_doublon_compte_une_fois() {
        referentiel();

        assertThat(libelles().codesDesSalles(CENTRE, List.of())).isEmpty();
        assertThat(libelles().nomsDesMedecins(CENTRE, java.util.Arrays.asList((UUID) null))).isEmpty();
        assertThat(libelles().creneauxDuCentre(CENTRE, java.util.Arrays.asList(C1, C1, null))).containsOnlyKeys(C1);
        assertThat(libelles().nomsDesSalles(java.util.Arrays.asList((UUID) null))).isEmpty();
    }

    @Test
    void le_medecin_est_rendu_nom_puis_prenom_sans_blanc_de_bord_et_un_medecin_desactive_garde_son_libelle() {
        referentiel();

        assertThat(libelles().nomsDesMedecins(CENTRE, List.of(M1, M2, M3, M_DESACTIVE, M_AUTRE, INCONNU)))
                .containsOnly(java.util.Map.entry(M1, "Dupont Jean"), java.util.Map.entry(M2, "Martin"),
                        java.util.Map.entry(M3, "Durand"), java.util.Map.entry(M_DESACTIVE, "Ancien Paul"));
    }

    @Test
    void la_recherche_de_medecin_porte_sur_nom_prenom_dans_les_deux_ordres_ou_identifiant_sans_casse_dans_le_centre() {
        referentiel();

        assertThat(libelles().medecinsCorrespondant(CENTRE, "dupont")).containsExactly(M1);
        assertThat(libelles().medecinsCorrespondant(CENTRE, "  JEAN dupont ")).containsExactly(M1);
        assertThat(libelles().medecinsCorrespondant(CENTRE, "dupont jean")).containsExactly(M1);
        assertThat(libelles().medecinsCorrespondant(CENTRE, M2.toString())).containsExactly(M2);
        assertThat(libelles().medecinsCorrespondant(CENTRE, "etranger")).isEmpty();
        assertThat(libelles().medecinsCorrespondant(CENTRE, "introuvable")).isEmpty();
        assertThat(libelles().medecinsCorrespondant(CENTRE, "")).containsExactlyInAnyOrder(M1, M2, M3, M_DESACTIVE);
    }

    @Test
    void la_recherche_de_creneau_porte_sur_code_libelle_ou_identifiant_dans_le_centre() {
        referentiel();

        assertThat(libelles().creneauxCorrespondant(CENTRE, "matin")).containsExactly(C1);
        assertThat(libelles().creneauxCorrespondant(CENTRE, "APM")).containsExactly(C2);
        assertThat(libelles().creneauxCorrespondant(CENTRE, "mat matin")).containsExactly(C1);
        assertThat(libelles().creneauxCorrespondant(CENTRE, "MAT APM")).isEmpty();
        assertThat(libelles().creneauxCorrespondant(CENTRE, C2.toString())).containsExactly(C2);
        assertThat(libelles().creneauxCorrespondant(CENTRE, "soir")).isEmpty();
        assertThat(libelles().creneauxCorrespondant(AUTRE, "soir")).isEqualTo(Set.of(C_AUTRE));
    }

    @Test
    void les_jokers_du_terme_ne_sont_pas_echappes() {
        referentiel();

        assertThat(libelles().creneauxCorrespondant(CENTRE, "m%n")).containsExactly(C1);
        assertThat(libelles().creneauxCorrespondant(CENTRE, "m_tin")).containsExactly(C1);
    }
}
