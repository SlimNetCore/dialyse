package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CarnetDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Transporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CategoriesDeTransport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.FicheDeTransporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.PageDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du carnet de transporteurs, de ses lectures et du langage publié (RF1) : le double en mémoire et les tables réelles
 * ({@code transporteur}, {@code categorie_transport}, {@code patients}) passent EXACTEMENT les mêmes tests.
 */
abstract class TransporteursContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000b"));

    protected abstract CarnetDeTransporteurs carnet();

    protected abstract LectureDesTransporteurs lecture();

    protected abstract Transporteurs publie();

    protected abstract CategoriesDeTransport categories();

    protected abstract UUID categorie(CentreId centre, String libelle);

    /** Un patient du centre donné utilise ce transporteur à l'aller et/ou au retour. */
    protected abstract void patientUtilisant(CentreId centreDuPatient, UUID aller, UUID retour);

    protected Transporteur enregistre(CentreId centre, String nom, String telephone) {
        Transporteur t = Transporteur.referencer(UUID.randomUUID(), centre, nom, telephone);
        carnet().enregistrer(t);
        return t;
    }

    @Test
    void un_transporteur_enregistre_se_retrouve_dans_son_centre_seulement() {
        Transporteur t = enregistre(CENTRE, "Alpha", "0795006136");

        assertThat(carnet().trouver(CENTRE, t.id())).hasValueSatisfying(trouve -> {
            assertThat(trouve.id()).isEqualTo(t.id());
            assertThat(trouve.nom()).isEqualTo("Alpha");
            assertThat(trouve.telephone()).isEqualTo("0795006136");
            assertThat(trouve.centre()).isEqualTo(CENTRE);
        });
        assertThat(carnet().trouver(AUTRE, t.id())).isEmpty();
        assertThat(carnet().trouver(CENTRE, UUID.randomUUID())).isEmpty();
    }

    @Test
    void un_telephone_absent_reste_absent() {
        Transporteur t = enregistre(CENTRE, "Alpha", null);

        assertThat(carnet().trouver(CENTRE, t.id()).orElseThrow().telephone()).isNull();
    }

    @Test
    void le_carnet_d_un_centre_ne_contient_que_ses_transporteurs() {
        Transporteur a = enregistre(CENTRE, "Alpha", null);
        Transporteur b = enregistre(CENTRE, "Beta", null);
        enregistre(AUTRE, "Gamma", null);

        assertThat(carnet().duCentre(CENTRE)).extracting(Transporteur::id).containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(carnet().duCentre(AUTRE)).extracting(Transporteur::nom).containsExactly("Gamma");
    }

    @Test
    void remplacer_change_le_nom_et_le_telephone_du_seul_transporteur_du_centre() {
        Transporteur t = enregistre(CENTRE, "Alpha", "0795006136");
        Transporteur autreCentre = enregistre(AUTRE, "Alpha", null);

        carnet().remplacer(t.corriger("Alpha Est", null));
        carnet().remplacer(Transporteur.referencer(autreCentre.id(), CENTRE, "Intrus", null));

        Transporteur relu = carnet().trouver(CENTRE, t.id()).orElseThrow();
        assertThat(relu.nom()).isEqualTo("Alpha Est");
        assertThat(relu.telephone()).isNull();
        assertThat(carnet().trouver(AUTRE, autreCentre.id()).orElseThrow().nom()).as("le centre propriétaire ne change pas").isEqualTo("Alpha");
    }

    @Test
    void supprimer_retire_le_transporteur_du_seul_centre_demande() {
        Transporteur t = enregistre(CENTRE, "Alpha", null);

        carnet().supprimer(AUTRE, t.id());
        assertThat(carnet().trouver(CENTRE, t.id())).isPresent();

        carnet().supprimer(CENTRE, t.id());
        assertThat(carnet().trouver(CENTRE, t.id())).isEmpty();
    }

    @Test
    void les_references_comptent_l_aller_et_le_retour_des_seuls_patients_du_centre_du_transporteur() {
        // KB-RF1-03 corrigé (ADR 0123) : les patients d'un autre centre ne comptent plus
        Transporteur t = enregistre(CENTRE, "Alpha", null);
        Transporteur libre = enregistre(CENTRE, "Beta", null);
        patientUtilisant(CENTRE, t.id(), null);
        patientUtilisant(CENTRE, null, t.id());
        patientUtilisant(CENTRE, t.id(), t.id());
        patientUtilisant(AUTRE, t.id(), null);
        patientUtilisant(AUTRE, null, t.id());

        assertThat(carnet().nombreDeReferences(CENTRE, t.id())).isEqualTo(4);
        assertThat(carnet().nombreDeReferences(CENTRE, libre.id())).isZero();
    }

    @Test
    void un_transporteur_utilise_seulement_par_des_patients_d_un_autre_centre_n_a_aucune_reference() {
        Transporteur t = enregistre(CENTRE, "Alpha", null);
        patientUtilisant(AUTRE, t.id(), t.id());

        assertThat(carnet().nombreDeReferences(CENTRE, t.id())).isZero();
    }

    @Test
    void la_page_est_triee_par_nom_bornee_au_centre_et_donne_le_total() {
        Transporteur c = enregistre(CENTRE, "Gamma", "0555 55 55 55");
        Transporteur a = enregistre(CENTRE, "Alpha", null);
        Transporteur b = enregistre(CENTRE, "Beta", "021 11 22 33");
        enregistre(AUTRE, "Autre", null);

        PageDeTransporteurs page = lecture().page(CENTRE, null, 0, 20);
        assertThat(page.lignes()).extracting(FicheDeTransporteur::id).containsExactly(a.id(), b.id(), c.id());
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.taille()).isEqualTo(20);
        assertThat(page.lignes().get(1)).isEqualTo(new FicheDeTransporteur(b.id(), "Beta", "021 11 22 33"));

        PageDeTransporteurs suivante = lecture().page(CENTRE, null, 1, 2);
        assertThat(suivante.lignes()).extracting(FicheDeTransporteur::nom).containsExactly("Gamma");
        assertThat(suivante.total()).isEqualTo(3);
        assertThat(lecture().page(CENTRE, null, 9, 2).lignes()).isEmpty();
    }

    @Test
    void la_recherche_ignore_la_casse_porte_sur_le_nom_et_le_telephone_et_prend_les_jokers_litteralement() {
        enregistre(CENTRE, "Alpha ambulances", null);
        enregistre(CENTRE, "Beta 50% Taxi", "021 11 22 33");
        enregistre(CENTRE, "Gamma_Med", "0795006136");

        assertThat(noms(lecture().page(CENTRE, "AMBULANCES", 0, 20))).containsExactly("Alpha ambulances");
        assertThat(noms(lecture().page(CENTRE, "6136", 0, 20))).containsExactly("Gamma_Med");
        assertThat(noms(lecture().page(CENTRE, "%", 0, 20))).containsExactly("Beta 50% Taxi");
        assertThat(noms(lecture().page(CENTRE, "_", 0, 20))).containsExactly("Gamma_Med");
        assertThat(noms(lecture().page(CENTRE, "  gamma ", 0, 20))).containsExactly("Gamma_Med");
        assertThat(lecture().page(CENTRE, "introuvable", 0, 20).total()).isZero();
        assertThat(lecture().page(CENTRE, "", 0, 20).total()).isEqualTo(3);
        assertThat(lecture().page(CENTRE, null, 0, 20).total()).isEqualTo(3);
    }

    private static List<String> noms(PageDeTransporteurs page) {
        return page.lignes().stream().map(FicheDeTransporteur::nom).toList();
    }

    @Test
    void le_langage_publie_liste_nomme_et_reconnait_les_transporteurs_du_seul_centre() {
        Transporteur b = enregistre(CENTRE, "Beta", "0795006136");
        Transporteur a = enregistre(CENTRE, "Alpha", null);
        Transporteur autre = enregistre(AUTRE, "Gamma", null);

        assertThat(publie().lister(CENTRE.valeur())).containsExactly(
                new Transporteurs.TransporteurPublie(a.id(), "Alpha"), new Transporteurs.TransporteurPublie(b.id(), "Beta"));
        assertThat(publie().lister(UUID.randomUUID())).isEmpty();
        assertThat(publie().nom(CENTRE.valeur(), a.id())).contains("Alpha");
        assertThat(publie().nom(CENTRE.valeur(), autre.id())).isEmpty();
        assertThat(publie().existe(CENTRE.valeur(), b.id())).isTrue();
        assertThat(publie().existe(CENTRE.valeur(), autre.id())).isFalse();
        assertThat(publie().existe(AUTRE.valeur(), autre.id())).isTrue();
    }

    @Test
    void des_homonymes_historiques_sont_toujours_rendus_dans_l_ordre_nom_puis_identite() {
        UUID bas = UUID.fromString("99997700-0000-0000-0000-000000000101");
        UUID haut = UUID.fromString("99997700-0000-0000-0000-000000000102");
        carnet().enregistrer(Transporteur.referencer(haut, CENTRE, "Alpha", null));
        carnet().enregistrer(Transporteur.referencer(bas, CENTRE, "Alpha", null));

        assertThat(carnet().duCentre(CENTRE)).extracting(Transporteur::id).containsExactly(bas, haut);
        assertThat(publie().lister(CENTRE.valeur())).extracting(Transporteurs.TransporteurPublie::id).containsExactly(bas, haut);
    }

    @Test
    void les_categories_de_transport_sont_listees_par_libelle_pour_le_seul_centre() {
        UUID vsl = categorie(CENTRE, "VSL");
        UUID ambulance = categorie(CENTRE, "Ambulance");
        categorie(AUTRE, "Taxi");

        assertThat(categories().lister(CENTRE.valeur())).containsExactly(
                new CategoriesDeTransport.CategorieDeTransportPubliee(ambulance, "Ambulance"),
                new CategoriesDeTransport.CategorieDeTransportPubliee(vsl, "VSL"));
        assertThat(categories().lister(UUID.randomUUID())).isEmpty();
    }
}
