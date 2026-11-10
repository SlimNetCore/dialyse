package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDuCatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Forfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre.ForfaitPublie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.FicheDeForfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.PageDeForfaits;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du catalogue de forfaits, de sa lecture et du langage publié (RF2) : le double en mémoire et la table réelle
 * ({@code forfait}) passent EXACTEMENT les mêmes tests.
 */
abstract class ForfaitsContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000b"));

    protected abstract CatalogueDeForfaits catalogue();

    protected abstract LectureDuCatalogueDeForfaits lecture();

    protected abstract ForfaitsDuCentre publie();

    protected Forfait enregistre(CentreId centre, String code, String libelle, String prix) {
        Forfait f = Forfait.referencer(UUID.randomUUID(), centre, code, libelle, prix);
        catalogue().enregistrer(f);
        return f;
    }

    @Test
    void un_forfait_enregistre_se_retrouve_dans_son_centre_seulement() {
        Forfait f = enregistre(CENTRE, "HD-CONV", "Hémodialyse conventionnelle", "5600.50");

        assertThat(catalogue().trouver(CENTRE, f.id())).hasValueSatisfying(trouve -> {
            assertThat(trouve.id()).isEqualTo(f.id());
            assertThat(trouve.code()).isEqualTo("HD-CONV");
            assertThat(trouve.libelle()).isEqualTo("Hémodialyse conventionnelle");
            assertThat(trouve.prix()).isEqualByComparingTo("5600.50");
            assertThat(trouve.centre()).isEqualTo(CENTRE);
        });
        assertThat(catalogue().trouver(AUTRE, f.id())).isEmpty();
        assertThat(catalogue().trouver(CENTRE, UUID.randomUUID())).isEmpty();
    }

    @Test
    void un_prix_absent_reste_absent_et_zero_reste_zero() {
        Forfait sansPrix = enregistre(CENTRE, "SANS", "Sans prix", null);
        Forfait gratuit = enregistre(CENTRE, "ZERO", "Gratuit", "0");

        assertThat(catalogue().trouver(CENTRE, sansPrix.id()).orElseThrow().prix()).isNull();
        assertThat(catalogue().trouver(CENTRE, gratuit.id()).orElseThrow().prix()).isEqualByComparingTo("0");
    }

    @Test
    void le_catalogue_d_un_centre_ne_contient_que_ses_forfaits() {
        Forfait a = enregistre(CENTRE, "A", "Alpha", null);
        Forfait b = enregistre(CENTRE, "B", "Beta", null);
        enregistre(AUTRE, "G", "Gamma", null);

        assertThat(catalogue().duCentre(CENTRE)).extracting(Forfait::id).containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(catalogue().duCentre(AUTRE)).extracting(Forfait::code).containsExactly("G");
    }

    @Test
    void remplacer_change_le_code_le_libelle_et_le_prix_du_seul_forfait_du_centre() {
        Forfait f = enregistre(CENTRE, "A", "Alpha", "100");
        Forfait autreCentre = enregistre(AUTRE, "A", "Alpha", "100");

        catalogue().remplacer(f.corriger("A2", "Alpha Est", null));
        catalogue().remplacer(Forfait.referencer(autreCentre.id(), CENTRE, "INTRUS", "Intrus", "1"));

        Forfait relu = catalogue().trouver(CENTRE, f.id()).orElseThrow();
        assertThat(relu.code()).isEqualTo("A2");
        assertThat(relu.libelle()).isEqualTo("Alpha Est");
        assertThat(relu.prix()).isNull();
        Forfait intact = catalogue().trouver(AUTRE, autreCentre.id()).orElseThrow();
        assertThat(intact.code()).as("le centre propriétaire ne change pas").isEqualTo("A");
        assertThat(intact.prix()).isEqualByComparingTo("100");
    }

    @Test
    void supprimer_retire_le_forfait_du_seul_centre_demande() {
        Forfait f = enregistre(CENTRE, "A", "Alpha", null);

        catalogue().supprimer(AUTRE, f.id());
        assertThat(catalogue().trouver(CENTRE, f.id())).isPresent();

        catalogue().supprimer(CENTRE, f.id());
        assertThat(catalogue().trouver(CENTRE, f.id())).isEmpty();
    }

    @Test
    void la_page_est_triee_par_code_bornee_au_centre_et_donne_le_total() {
        Forfait c = enregistre(CENTRE, "C-HD", "Gamma", "5600");
        Forfait a = enregistre(CENTRE, "A-HD", "Alpha", null);
        Forfait b = enregistre(CENTRE, "B-HD", "Beta", "0");
        enregistre(AUTRE, "AUTRE", "Autre", null);

        PageDeForfaits page = lecture().page(CENTRE, null, 0, 20);
        assertThat(page.lignes()).extracting(FicheDeForfait::id).containsExactly(a.id(), b.id(), c.id());
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.taille()).isEqualTo(20);
        assertThat(page.lignes().get(2).prix()).isEqualByComparingTo("5600");
        assertThat(page.lignes().get(0).prix()).isNull();
        assertThat(page.lignes().get(1)).extracting(FicheDeForfait::code, FicheDeForfait::libelle).containsExactly("B-HD", "Beta");

        PageDeForfaits suivante = lecture().page(CENTRE, null, 1, 2);
        assertThat(suivante.lignes()).extracting(FicheDeForfait::code).containsExactly("C-HD");
        assertThat(suivante.total()).isEqualTo(3);
        assertThat(lecture().page(CENTRE, null, 9, 2).lignes()).isEmpty();
    }

    @Test
    void la_recherche_ignore_la_casse_porte_sur_le_code_et_le_libelle_pas_sur_le_prix_et_prend_les_jokers_litteralement() {
        enregistre(CENTRE, "ALPHA", "Premier forfait", "12.50");
        enregistre(CENTRE, "B-50%", "Beta", null);
        enregistre(CENTRE, "G_MED", "Gamma", null);

        assertThat(codes(lecture().page(CENTRE, "alpha", 0, 20))).containsExactly("ALPHA");
        assertThat(codes(lecture().page(CENTRE, "PREMIER", 0, 20))).containsExactly("ALPHA");
        assertThat(codes(lecture().page(CENTRE, "12.50", 0, 20))).isEmpty();
        assertThat(codes(lecture().page(CENTRE, "%", 0, 20))).containsExactly("B-50%");
        assertThat(codes(lecture().page(CENTRE, "_", 0, 20))).containsExactly("G_MED");
        assertThat(codes(lecture().page(CENTRE, "  gamma ", 0, 20))).containsExactly("G_MED");
        assertThat(lecture().page(CENTRE, "introuvable", 0, 20).total()).isZero();
        assertThat(lecture().page(CENTRE, "", 0, 20).total()).isEqualTo(3);
        assertThat(lecture().page(CENTRE, null, 0, 20).total()).isEqualTo(3);
    }

    private static List<String> codes(PageDeForfaits page) {
        return page.lignes().stream().map(FicheDeForfait::code).toList();
    }

    @Test
    void le_langage_publie_liste_trouve_et_reconnait_les_forfaits_du_seul_centre() {
        Forfait b = enregistre(CENTRE, "B-HD", "Beta", "5600.50");
        Forfait a = enregistre(CENTRE, "A-HD", "Alpha", null);
        Forfait autre = enregistre(AUTRE, "G-HD", "Gamma", "1");

        assertThat(publie().lister(CENTRE.valeur())).extracting(ForfaitPublie::id).containsExactly(a.id(), b.id());
        assertThat(publie().lister(CENTRE.valeur()).get(1)).extracting(ForfaitPublie::code, ForfaitPublie::libelle)
                .containsExactly("B-HD", "Beta");
        assertThat(publie().lister(UUID.randomUUID())).isEmpty();
        assertThat(publie().trouver(CENTRE.valeur(), b.id())).hasValueSatisfying(p -> {
            assertThat(p.code()).isEqualTo("B-HD");
            assertThat(p.libelle()).isEqualTo("Beta");
            assertThat(p.prix()).isEqualByComparingTo("5600.50");
        });
        assertThat(publie().trouver(CENTRE.valeur(), a.id()).orElseThrow().prix()).isNull();
        assertThat(publie().trouver(CENTRE.valeur(), autre.id())).isEmpty();
        assertThat(publie().existe(CENTRE.valeur(), b.id())).isTrue();
        assertThat(publie().existe(CENTRE.valeur(), autre.id())).isFalse();
        assertThat(publie().existe(AUTRE.valeur(), autre.id())).isTrue();
    }

    @Test
    void le_langage_publie_donne_le_prix_seulement_si_le_forfait_du_centre_en_a_un() {
        Forfait avecPrix = enregistre(CENTRE, "A", "Alpha", "5600.50");
        Forfait gratuit = enregistre(CENTRE, "Z", "Gratuit", "0");
        Forfait sansPrix = enregistre(CENTRE, "S", "Sans prix", null);
        Forfait autre = enregistre(AUTRE, "G", "Gamma", "1");

        assertThat(publie().prix(CENTRE.valeur(), avecPrix.id())).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("5600.50"));
        assertThat(publie().prix(CENTRE.valeur(), gratuit.id())).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo(BigDecimal.ZERO));
        assertThat(publie().prix(CENTRE.valeur(), sansPrix.id())).isEmpty();
        assertThat(publie().prix(CENTRE.valeur(), autre.id())).isEmpty();
        assertThat(publie().prix(CENTRE.valeur(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void le_langage_publie_retrouve_un_forfait_par_son_code_dans_le_seul_centre_sans_tenir_compte_de_la_casse() {
        Forfait f = enregistre(CENTRE, "HD-CONV", "Hémodialyse", "1");
        enregistre(AUTRE, "HD-AUTRE", "Autre", null);

        assertThat(publie().parCode(CENTRE.valeur(), "hd-conv").map(ForfaitPublie::id)).contains(f.id());
        assertThat(publie().parCode(CENTRE.valeur(), "  HD-CONV ").map(ForfaitPublie::id)).contains(f.id());
        assertThat(publie().parCode(CENTRE.valeur(), "HD-AUTRE")).isEmpty();
        assertThat(publie().parCode(CENTRE.valeur(), "INCONNU")).isEmpty();
        assertThat(publie().parCode(CENTRE.valeur(), null)).isEmpty();
    }
}
