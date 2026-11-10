package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.application.UsageDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agence;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agences;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CaisseDAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Caisses;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentrePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentresPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NiveauDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.FicheDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.PageDePayeurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la chaîne des payeurs (RF3a) : les trois ports, la lecture paginée de l'administration et l'usage. Le double en mémoire
 * et les tables réelles ({@code caisse_assurance}, {@code agence}, {@code centre_payeur}) passent EXACTEMENT les mêmes tests.
 */
abstract class PayeursContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99998300-0000-0000-0000-00000000000a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("99998300-0000-0000-0000-00000000000b"));

    protected abstract Caisses caisses();

    protected abstract Agences agences();

    protected abstract CentresPayeurs centresPayeurs();

    protected abstract LectureDesPayeurs lecture();

    protected abstract UsageDesPayeurs usage();

    protected CaisseDAssurance caisse(CentreId centre, String code, String nom, String type) {
        CaisseDAssurance c = CaisseDAssurance.referencer(UUID.randomUUID(), centre, code, nom, type);
        caisses().enregistrer(c);
        return c;
    }

    protected Agence agence(CentreId centre, CaisseDAssurance caisse, String code, String nom) {
        Agence a = Agence.referencer(UUID.randomUUID(), centre, code, nom, caisse.id().toString(), List.of(caisse));
        agences().enregistrer(a);
        return a;
    }

    protected CentrePayeur centre(CentreId centre, Agence agence, String code, String nom, String adresse) {
        CentrePayeur c = CentrePayeur.referencer(UUID.randomUUID(), centre, code, nom, adresse, agence.id().toString(), List.of(agence));
        centresPayeurs().enregistrer(c);
        return c;
    }

    // ---- les trois ports

    @Test
    void une_caisse_enregistree_se_retrouve_dans_son_centre_seulement_avec_son_type() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse nationale", "VACANCIER");

        assertThat(caisses().trouver(CENTRE, c.id())).hasValueSatisfying(trouvee -> {
            assertThat(trouvee.code()).isEqualTo("CNAS");
            assertThat(trouvee.nom()).isEqualTo("Caisse nationale");
            assertThat(trouvee.type()).isEqualTo("VACANCIER");
            assertThat(trouvee.centre()).isEqualTo(CENTRE);
        });
        assertThat(caisses().trouver(AUTRE, c.id())).isEmpty();
        assertThat(caisses().trouver(CENTRE, UUID.randomUUID())).isEmpty();
    }

    @Test
    void une_agence_garde_sa_caisse_et_un_centre_payeur_son_agence_et_son_adresse() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CentrePayeur cp = centre(CENTRE, a, "CP-1", "Centre 1", "12 rue Didouche Mourad");
        CentrePayeur sans = centre(CENTRE, a, "CP-2", "Centre 2", null);

        assertThat(agences().trouver(CENTRE, a.id()).orElseThrow().caisse()).isEqualTo(c.id());
        assertThat(centresPayeurs().trouver(CENTRE, cp.id()).orElseThrow().agence()).isEqualTo(a.id());
        assertThat(centresPayeurs().trouver(CENTRE, cp.id()).orElseThrow().adresse()).isEqualTo("12 rue Didouche Mourad");
        assertThat(centresPayeurs().trouver(CENTRE, sans.id()).orElseThrow().adresse()).isNull();
        assertThat(agences().trouver(AUTRE, a.id())).isEmpty();
        assertThat(centresPayeurs().trouver(AUTRE, cp.id())).isEmpty();
    }

    @Test
    void duCentre_ne_rend_que_les_payeurs_du_centre_a_chaque_niveau() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);
        CaisseDAssurance cb = caisse(AUTRE, "CNAS-B", "Caisse B", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        Agence ab = agence(AUTRE, cb, "AG-B", "Agence B");
        centre(CENTRE, a, "CP-1", "Centre 1", null);
        centre(AUTRE, ab, "CP-B", "Centre B", null);

        assertThat(caisses().duCentre(CENTRE)).extracting(CaisseDAssurance::code).containsExactly("CNAS");
        assertThat(agences().duCentre(CENTRE)).extracting(Agence::code).containsExactly("AG-1");
        assertThat(centresPayeurs().duCentre(CENTRE)).extracting(CentrePayeur::code).containsExactly("CP-1");
        assertThat(caisses().duCentre(AUTRE)).extracting(CaisseDAssurance::code).containsExactly("CNAS-B");
    }

    @Test
    void remplacer_change_tous_les_champs_et_reste_sans_effet_hors_du_centre() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", "STANDARD");
        CaisseDAssurance autre = caisse(CENTRE, "CASNOS", "Autre caisse", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");

        caisses().remplacer(c.corriger("CNAS2", "Caisse renommee", "VACANCIER"));
        agences().remplacer(a.corriger("AG-1B", "Agence renommee", autre.id().toString(), List.of(c, autre)));
        caisses().remplacer(CaisseDAssurance.reconstituer(c.id(), AUTRE, "PIRATE", "Pirate", "STANDARD"));

        CaisseDAssurance relue = caisses().trouver(CENTRE, c.id()).orElseThrow();
        assertThat(relue.code()).isEqualTo("CNAS2");
        assertThat(relue.nom()).isEqualTo("Caisse renommee");
        assertThat(relue.type()).isEqualTo("VACANCIER");
        Agence relueAgence = agences().trouver(CENTRE, a.id()).orElseThrow();
        assertThat(relueAgence.code()).isEqualTo("AG-1B");
        assertThat(relueAgence.caisse()).isEqualTo(autre.id());
    }

    @Test
    void supprimer_retire_le_payeur_du_centre_et_ne_touche_pas_a_celui_d_un_autre_centre() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);

        caisses().supprimer(AUTRE, c.id());
        assertThat(caisses().trouver(CENTRE, c.id())).isPresent();
        caisses().supprimer(CENTRE, c.id());
        assertThat(caisses().trouver(CENTRE, c.id())).isEmpty();
    }

    // ---- la lecture paginée de l'administration

    @Test
    void la_page_est_triee_par_code_pagine_et_bornee_au_centre() {
        caisse(CENTRE, "C-3", "Troisieme", null);
        caisse(CENTRE, "C-1", "Premiere", null);
        caisse(CENTRE, "C-2", "Deuxieme", null);
        caisse(AUTRE, "C-0", "Autre centre", null);

        PageDePayeurs page0 = lecture().page(CENTRE, NiveauDePayeur.CAISSE, null, 0, 2);
        PageDePayeurs page1 = lecture().page(CENTRE, NiveauDePayeur.CAISSE, null, 1, 2);
        PageDePayeurs horsLimite = lecture().page(CENTRE, NiveauDePayeur.CAISSE, null, 9, 2);

        assertThat(page0.lignes()).extracting(FicheDePayeur::code).containsExactly("C-1", "C-2");
        assertThat(page1.lignes()).extracting(FicheDePayeur::code).containsExactly("C-3");
        assertThat(page0.total()).isEqualTo(3);
        assertThat(page0.page()).isZero();
        assertThat(page0.taille()).isEqualTo(2);
        assertThat(horsLimite.lignes()).isEmpty();
        assertThat(horsLimite.total()).isEqualTo(3);
    }

    @Test
    void la_recherche_ignore_la_casse_prend_les_jokers_litteralement_et_ne_porte_ni_sur_le_type() {
        caisse(CENTRE, "B-50%", "Beta cinquante", "STANDARD");
        caisse(CENTRE, "G_MED", "Gamma", "VACANCIER");
        caisse(CENTRE, "ZEBRA", "Zebra", "STANDARD");

        assertThat(codes(NiveauDePayeur.CAISSE, "zebra")).containsExactly("ZEBRA");
        assertThat(codes(NiveauDePayeur.CAISSE, "GAMMA")).containsExactly("G_MED");
        assertThat(codes(NiveauDePayeur.CAISSE, "%")).containsExactly("B-50%");
        assertThat(codes(NiveauDePayeur.CAISSE, "_")).containsExactly("G_MED");
        assertThat(codes(NiveauDePayeur.CAISSE, "vacancier")).isEmpty();
        assertThat(codes(NiveauDePayeur.CAISSE, "   ")).hasSize(3);
        assertThat(codes(NiveauDePayeur.CAISSE, "introuvable")).isEmpty();
    }

    @Test
    void la_page_d_une_agence_rend_sa_caisse_et_la_recherche_porte_aussi_sur_le_code_et_le_nom_de_la_caisse() {
        CaisseDAssurance cnas = caisse(CENTRE, "CNAS", "Caisse nationale", null);
        CaisseDAssurance casnos = caisse(CENTRE, "CASNOS", "Caisse des non-salaries", null);
        agence(CENTRE, cnas, "AG-1", "Agence 1");
        agence(CENTRE, casnos, "AG-2", "Agence 2");

        PageDePayeurs page = lecture().page(CENTRE, NiveauDePayeur.AGENCE, null, 0, 20);

        assertThat(page.lignes()).extracting(FicheDePayeur::code, FicheDePayeur::parent, FicheDePayeur::parentLibelle)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AG-1", cnas.id(), "CNAS · Caisse nationale"),
                        org.assertj.core.groups.Tuple.tuple("AG-2", casnos.id(), "CASNOS · Caisse des non-salaries"));
        assertThat(codes(NiveauDePayeur.AGENCE, "casnos")).containsExactly("AG-2");
        assertThat(codes(NiveauDePayeur.AGENCE, "NATIONALE")).containsExactly("AG-1");
    }

    @Test
    void la_page_d_un_centre_payeur_rend_son_agence_et_son_adresse_et_la_recherche_porte_sur_l_adresse_et_l_agence() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);
        Agence a1 = agence(CENTRE, c, "AG-1", "Agence nord");
        Agence a2 = agence(CENTRE, c, "AG-2", "Agence sud");
        centre(CENTRE, a1, "CP-1", "Centre 1", "12 rue Didouche Mourad");
        centre(CENTRE, a2, "CP-2", "Centre 2", null);

        PageDePayeurs page = lecture().page(CENTRE, NiveauDePayeur.CENTRE_PAYEUR, null, 0, 20);

        assertThat(page.lignes()).extracting(FicheDePayeur::code, FicheDePayeur::adresse, FicheDePayeur::parentLibelle)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CP-1", "12 rue Didouche Mourad", "AG-1 · Agence nord"),
                        org.assertj.core.groups.Tuple.tuple("CP-2", null, "AG-2 · Agence sud"));
        assertThat(codes(NiveauDePayeur.CENTRE_PAYEUR, "didouche")).containsExactly("CP-1");
        assertThat(codes(NiveauDePayeur.CENTRE_PAYEUR, "sud")).containsExactly("CP-2");
        assertThat(codes(NiveauDePayeur.CENTRE_PAYEUR, "AG-1")).containsExactly("CP-1");
    }

    private List<String> codes(NiveauDePayeur niveau, String recherche) {
        return lecture().page(CENTRE, niveau, recherche, 0, 20).lignes().stream().map(FicheDePayeur::code).toList();
    }

    // ---- l'usage

    @Test
    void les_agences_retiennent_leur_caisse_et_les_centres_payeurs_leur_agence() {
        CaisseDAssurance utilisee = caisse(CENTRE, "CNAS", "Caisse", null);
        CaisseDAssurance libre = caisse(CENTRE, "CASNOS", "Caisse libre", null);
        Agence a1 = agence(CENTRE, utilisee, "AG-1", "Agence 1");
        Agence a2 = agence(CENTRE, utilisee, "AG-2", "Agence 2");
        Agence agenceLibre = agence(CENTRE, libre, "AG-3", "Agence 3");
        centre(CENTRE, a1, "CP-1", "Centre 1", null);
        centre(CENTRE, a1, "CP-2", "Centre 2", null);

        assertThat(usage().nombreDeReferences(NiveauDePayeur.CAISSE, utilisee.id())).isEqualTo(2);
        assertThat(usage().nombreDeReferences(NiveauDePayeur.CAISSE, libre.id())).isEqualTo(1);
        assertThat(usage().nombreDeReferences(NiveauDePayeur.CAISSE, UUID.randomUUID())).isZero();
        assertThat(usage().nombreDeReferences(NiveauDePayeur.AGENCE, a1.id())).isEqualTo(2);
        assertThat(usage().nombreDeReferences(NiveauDePayeur.AGENCE, a2.id())).isZero();
        assertThat(usage().nombreDeReferences(NiveauDePayeur.AGENCE, agenceLibre.id())).isZero();
    }
}
