package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees.PlaceDeSeancePubliee;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des lectures publiées de la Séance (ADR 0096, SE7c1) : joué par le double en mémoire, par l'adapter JDBC sur H2 et sur
 * PostgreSQL 16. Les cas reproduisent ceux du filet de caractérisation (séance inconnue, d'un autre centre, statut en minuscules, salle
 * ou créneau d'un autre centre ou absent).
 */
abstract class LecturesPublieesDesSeancesContrat {

    static final UUID A = new UUID(0, 0xA);
    static final UUID B = new UUID(0, 0xB);
    static final UUID SALLE_A = new UUID(5, 0xA);
    static final UUID SALLE_B = new UUID(5, 0xB);
    static final UUID CRENEAU_A = new UUID(6, 0xA);
    static final UUID CRENEAU_B = new UUID(6, 0xB);

    protected abstract void poserSalle(UUID centre, UUID id, String code);

    protected abstract void poserCreneau(UUID centre, UUID id, String code, String libelle);

    protected abstract void poserSeance(UUID centre, UUID id, String statut, UUID salle, UUID creneau);

    protected abstract FacturationDesSeancesPubliee facturation();

    protected abstract PlacesDesSeancesPubliees places();

    private void referentiel() {
        poserSalle(A, SALLE_A, "S-A");
        poserSalle(B, SALLE_B, "S-B");
        poserCreneau(A, CRENEAU_A, "C1", "Première vague");
        poserCreneau(B, CRENEAU_B, "MATIN", "Matin B");
    }

    @Test
    void une_seance_au_statut_facturee_est_facturee_sans_tenir_compte_de_la_casse() {
        UUID facturee = new UUID(1, 1);
        UUID enMinuscules = new UUID(1, 2);
        poserSeance(A, facturee, "FACTUREE", null, null);
        poserSeance(A, enMinuscules, "facturee", null, null);

        assertThat(facturation().estFacturee(A, facturee)).isTrue();
        assertThat(facturation().estFacturee(A, enMinuscules)).isTrue();
    }

    @Test
    void une_seance_a_un_autre_statut_n_est_pas_facturee() {
        UUID validee = new UUID(1, 3);
        UUID signee = new UUID(1, 4);
        poserSeance(A, validee, "VALIDEE", null, null);
        poserSeance(A, signee, "SIGNEE", null, null);

        assertThat(facturation().estFacturee(A, validee)).isFalse();
        assertThat(facturation().estFacturee(A, signee)).isFalse();
    }

    @Test
    void une_seance_inconnue_ou_d_un_autre_centre_n_est_pas_facturee_pour_ce_centre() {
        UUID autreCentre = new UUID(1, 5);
        poserSeance(B, autreCentre, "FACTUREE", null, null);

        assertThat(facturation().estFacturee(A, new UUID(1, 99))).isFalse();
        assertThat(facturation().estFacturee(A, autreCentre)).isFalse();
        assertThat(facturation().estFacturee(B, autreCentre)).isTrue();
    }

    @Test
    void la_place_d_une_seance_inconnue_ou_d_un_autre_centre_est_vide() {
        referentiel();
        UUID autreCentre = new UUID(1, 6);
        poserSeance(B, autreCentre, "VALIDEE", SALLE_B, CRENEAU_B);

        assertThat(places().de(A, new UUID(1, 98))).isEmpty();
        assertThat(places().de(A, autreCentre)).isEmpty();
        assertThat(places().de(B, autreCentre)).contains(new PlaceDeSeancePubliee("S-B", "MATIN", "Matin B"));
    }

    @Test
    void la_place_d_une_seance_sans_salle_ni_creneau_n_a_ni_salle_ni_creneau() {
        referentiel();
        UUID seance = new UUID(1, 7);
        poserSeance(A, seance, "VALIDEE", null, null);

        assertThat(places().de(A, seance)).contains(new PlaceDeSeancePubliee(null, null, null));
    }

    @Test
    void la_place_donne_le_code_de_la_salle_et_le_code_et_le_libelle_du_creneau_du_centre() {
        referentiel();
        UUID seance = new UUID(1, 8);
        poserSeance(A, seance, "VALIDEE", SALLE_A, CRENEAU_A);

        assertThat(places().de(A, seance)).contains(new PlaceDeSeancePubliee("S-A", "C1", "Première vague"));
    }

    @Test
    void une_salle_ou_un_creneau_d_un_autre_centre_est_ignore() {
        referentiel();
        UUID seance = new UUID(1, 9);
        poserSeance(A, seance, "VALIDEE", SALLE_B, CRENEAU_B);

        assertThat(places().de(A, seance)).contains(new PlaceDeSeancePubliee(null, null, null));
    }

    @Test
    void une_salle_ou_un_creneau_disparu_est_ignore() {
        referentiel();
        UUID seance = new UUID(1, 10);
        poserSeance(A, seance, "VALIDEE", new UUID(5, 0xFF), new UUID(6, 0xFF));

        assertThat(places().de(A, seance)).contains(new PlaceDeSeancePubliee(null, null, null));
    }
}
