package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.ChoixDesPayeurs;
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
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

    /** Le langage publié {@code PayeursDuCentre} (RF3b) lu sur les mêmes données. */
    protected abstract PayeursDuCentre publie();

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

        assertThat(usage().nombreDeReferences(CENTRE, NiveauDePayeur.CAISSE, utilisee.id())).isEqualTo(2);
        assertThat(usage().nombreDeReferences(CENTRE, NiveauDePayeur.CAISSE, libre.id())).isEqualTo(1);
        assertThat(usage().nombreDeReferences(CENTRE, NiveauDePayeur.CAISSE, UUID.randomUUID())).isZero();
        assertThat(usage().nombreDeReferences(CENTRE, NiveauDePayeur.AGENCE, a1.id())).isEqualTo(2);
        assertThat(usage().nombreDeReferences(CENTRE, NiveauDePayeur.AGENCE, a2.id())).isZero();
        assertThat(usage().nombreDeReferences(CENTRE, NiveauDePayeur.AGENCE, agenceLibre.id())).isZero();
        // RF4 : l'usage est borné au centre ; un autre centre ne voit aucune référence à ces payeurs
        assertThat(usage().nombreDeReferences(AUTRE, NiveauDePayeur.CAISSE, utilisee.id())).isZero();
        assertThat(usage().nombreDeReferences(AUTRE, NiveauDePayeur.AGENCE, a1.id())).isZero();
    }

    // ---- le langage publié PayeursDuCentre (RF3b, ADR 0127) : lecture « annuaire », toujours bornée au centre indiqué

    @Test
    void l_annuaire_rend_les_caisses_les_agences_et_les_centres_payeurs_du_centre_par_code_et_jamais_ceux_d_un_autre() {
        CaisseDAssurance c2 = caisse(CENTRE, "C-2", "Seconde", "VACANCIER");
        CaisseDAssurance c1 = caisse(CENTRE, "C-1", "Premiere", null);
        CaisseDAssurance cb = caisse(AUTRE, "C-B", "Autre centre", null);
        Agence a2 = agence(CENTRE, c1, "AG-2", "Agence 2");
        Agence a1 = agence(CENTRE, c2, "AG-1", "Agence 1");
        Agence ab = agence(AUTRE, cb, "AG-B", "Agence B");
        centre(CENTRE, a1, "CP-2", "Centre 2", null);
        centre(CENTRE, a2, "CP-1", "Centre 1", "12 rue Didouche Mourad");
        centre(AUTRE, ab, "CP-B", "Centre B", null);

        assertThat(publie().caisses(CENTRE.valeur())).extracting(PayeursDuCentre.CaissePubliee::code, PayeursDuCentre.CaissePubliee::nom,
                PayeursDuCentre.CaissePubliee::type, PayeursDuCentre.CaissePubliee::id)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("C-1", "Premiere", "STANDARD", c1.id()),
                        org.assertj.core.groups.Tuple.tuple("C-2", "Seconde", "VACANCIER", c2.id()));
        assertThat(publie().agences(CENTRE.valeur())).extracting(PayeursDuCentre.AgencePubliee::code, PayeursDuCentre.AgencePubliee::caisse)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AG-1", c2.id()), org.assertj.core.groups.Tuple.tuple("AG-2", c1.id()));
        assertThat(publie().centresPayeurs(CENTRE.valeur())).extracting(PayeursDuCentre.CentrePayeurPublie::code,
                PayeursDuCentre.CentrePayeurPublie::adresse, PayeursDuCentre.CentrePayeurPublie::agence)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CP-1", "12 rue Didouche Mourad", a2.id()),
                        org.assertj.core.groups.Tuple.tuple("CP-2", null, a1.id()));
        assertThat(publie().caisses(AUTRE.valeur())).extracting(PayeursDuCentre.CaissePubliee::code).containsExactly("C-B");
        assertThat(publie().caisses(UUID.randomUUID())).isEmpty();
        assertThat(publie().agences(UUID.randomUUID())).isEmpty();
        assertThat(publie().centresPayeurs(UUID.randomUUID())).isEmpty();
    }

    @Test
    void le_detail_d_un_centre_payeur_donne_son_agence_et_sa_caisse() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse nationale", "VACANCIER");
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CentrePayeur cp = centre(CENTRE, a, "CP-1", "Centre 1", "adresse");

        PayeursDuCentre.ChaineDuPayeur detail = publie().detail(CENTRE.valeur(), cp.id()).orElseThrow();

        assertThat(detail.centrePayeur()).isEqualTo(new PayeursDuCentre.CentrePayeurPublie(cp.id(), "CP-1", "Centre 1", "adresse", a.id()));
        assertThat(detail.agence()).isEqualTo(new PayeursDuCentre.AgencePubliee(a.id(), "AG-1", "Agence 1", c.id()));
        assertThat(detail.caisse()).isEqualTo(new PayeursDuCentre.CaissePubliee(c.id(), "CNAS", "Caisse nationale", "VACANCIER"));
    }

    @Test
    void le_detail_est_vide_pour_un_centre_payeur_inconnu_ou_d_un_autre_centre() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CentrePayeur cp = centre(CENTRE, a, "CP-1", "Centre 1", null);

        assertThat(publie().detail(AUTRE.valeur(), cp.id())).isEmpty();
        assertThat(publie().detail(CENTRE.valeur(), UUID.randomUUID())).isEmpty();
        assertThat(publie().detail(CENTRE.valeur(), null)).isEmpty();
    }

    @Test
    void la_chaine_est_bornee_a_chaque_etage_une_agence_ou_une_caisse_d_un_autre_centre_n_est_jamais_suivie() {
        CaisseDAssurance etrangere = caisse(AUTRE, "C-B", "Caisse etrangere", null);
        Agence agenceEtrangere = agence(AUTRE, etrangere, "AG-B", "Agence etrangere");
        Agence agenceVersCaisseEtrangere = Agence.reconstituer(UUID.randomUUID(), CENTRE, "AG-X", "Agence vers caisse etrangere", etrangere.id());
        agences().enregistrer(agenceVersCaisseEtrangere);
        CentrePayeur versAgenceEtrangere = CentrePayeur.reconstituer(UUID.randomUUID(), CENTRE, "CP-1", "Vers agence etrangere", null,
                agenceEtrangere.id());
        centresPayeurs().enregistrer(versAgenceEtrangere);
        CentrePayeur versCaisseEtrangere = CentrePayeur.reconstituer(UUID.randomUUID(), CENTRE, "CP-2", "Vers caisse etrangere", null,
                agenceVersCaisseEtrangere.id());
        centresPayeurs().enregistrer(versCaisseEtrangere);
        CentrePayeur versAgenceInconnue = CentrePayeur.reconstituer(UUID.randomUUID(), CENTRE, "CP-3", "Vers agence inconnue", null,
                UUID.randomUUID());
        centresPayeurs().enregistrer(versAgenceInconnue);

        PayeursDuCentre.ChaineDuPayeur a = publie().detail(CENTRE.valeur(), versAgenceEtrangere.id()).orElseThrow();
        assertThat(a.centrePayeur().agence()).isEqualTo(agenceEtrangere.id());
        assertThat(a.agence()).isNull();
        assertThat(a.caisse()).isNull();
        PayeursDuCentre.ChaineDuPayeur b = publie().detail(CENTRE.valeur(), versCaisseEtrangere.id()).orElseThrow();
        assertThat(b.agence()).isNotNull();
        assertThat(b.agence().caisse()).isEqualTo(etrangere.id());
        assertThat(b.caisse()).isNull();
        PayeursDuCentre.ChaineDuPayeur inconnue = publie().detail(CENTRE.valeur(), versAgenceInconnue.id()).orElseThrow();
        assertThat(inconnue.agence()).isNull();
        assertThat(inconnue.caisse()).isNull();
    }

    @Test
    void les_chaines_du_centre_rendent_chaque_centre_payeur_par_code_avec_sa_chaine_bornee() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CentrePayeur cp2 = centre(CENTRE, a, "CP-2", "Centre 2", null);
        CentrePayeur cp1 = centre(CENTRE, a, "CP-1", "Centre 1", null);
        CaisseDAssurance cb = caisse(AUTRE, "C-B", "Caisse B", null);
        Agence ab = agence(AUTRE, cb, "AG-B", "Agence B");
        centre(AUTRE, ab, "CP-B", "Centre B", null);
        CentrePayeur orphelin = CentrePayeur.reconstituer(UUID.randomUUID(), CENTRE, "CP-3", "Orphelin", null, ab.id());
        centresPayeurs().enregistrer(orphelin);

        List<PayeursDuCentre.ChaineDuPayeur> chaines = publie().chaines(CENTRE.valeur());

        assertThat(chaines).extracting(ch -> ch.centrePayeur().id()).containsExactly(cp1.id(), cp2.id(), orphelin.id());
        assertThat(chaines).extracting(ch -> ch.agence() == null ? null : ch.agence().code()).containsExactly("AG-1", "AG-1", null);
        assertThat(chaines).extracting(ch -> ch.caisse() == null ? null : ch.caisse().code()).containsExactly("CNAS", "CNAS", null);
        assertThat(publie().chaines(AUTRE.valeur())).extracting(ch -> ch.caisse().code()).containsExactly("C-B");
        assertThat(publie().chaines(UUID.randomUUID())).isEmpty();
    }

    @Test
    void un_centre_payeur_existe_dans_son_centre_seulement() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CentrePayeur cp = centre(CENTRE, a, "CP-1", "Centre 1", null);

        assertThat(publie().existe(CENTRE.valeur(), cp.id())).isTrue();
        assertThat(publie().existe(AUTRE.valeur(), cp.id())).isFalse();
        assertThat(publie().existe(CENTRE.valeur(), UUID.randomUUID())).isFalse();
        assertThat(publie().existe(CENTRE.valeur(), null)).isFalse();
        assertThat(publie().existe(CENTRE.valeur(), a.id())).isFalse();
    }

    // ---- listes de choix des écrans (RF3c : /referentials/{caisses,agences,centres-payeurs,centres-payeurs-details})

    /** Les listes de choix servies aux écrans (RF3c), lues sur les mêmes données. */
    protected abstract ChoixDesPayeurs choix();

    @Test
    void les_listes_de_choix_sont_triees_par_nom_et_bornees_au_centre() {
        CaisseDAssurance cb = caisse(CENTRE, "CB", "Caisse B", "VACANCIER");
        CaisseDAssurance ca = caisse(CENTRE, "CA", "Caisse A", "STANDARD");
        Agence agB = agence(CENTRE, cb, "AGB", "Agence B");
        Agence agA = agence(CENTRE, ca, "AGA", "Agence A");
        centre(CENTRE, agB, "CPB", "Centre B", "Rue B");
        centre(CENTRE, agA, "CPA", "Centre A", null);
        CaisseDAssurance etrangere = caisse(AUTRE, "CE", "Caisse etrangere", null);
        centre(AUTRE, agence(AUTRE, etrangere, "AGE", "Agence etrangere"), "CPE", "Centre etranger", null);

        assertThat(choix().choixDeCaisses(CENTRE.valeur())).extracting(ChoixDesPayeurs.ChoixDePayeur::nom, ChoixDesPayeurs.ChoixDePayeur::type)
                .containsExactly(tuple("Caisse A", "STANDARD"), tuple("Caisse B", "VACANCIER"));
        assertThat(choix().choixDAgences(CENTRE.valeur())).extracting(ChoixDesPayeurs.ChoixDePayeur::code).containsExactly("AGA", "AGB");
        assertThat(choix().choixDeCentresPayeurs(CENTRE.valeur())).extracting(ChoixDesPayeurs.ChoixDePayeur::nom, ChoixDesPayeurs.ChoixDePayeur::adresse)
                .containsExactly(tuple("Centre A", null), tuple("Centre B", "Rue B"));
        assertThat(choix().choixDeCaisses(UUID.randomUUID())).isEmpty();
        assertThat(choix().choixDAgences(UUID.randomUUID())).isEmpty();
        assertThat(choix().choixDeCentresPayeurs(UUID.randomUUID())).isEmpty();
        assertThat(choix().detailsDesCentresPayeurs(UUID.randomUUID())).isEmpty();
    }

    @Test
    void le_detail_d_un_centre_payeur_porte_son_agence_et_sa_caisse_par_nom() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse nationale", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CentrePayeur cp2 = centre(CENTRE, a, "CP-2", "Zeta", "Rue 2");
        CentrePayeur cp1 = centre(CENTRE, a, "CP-1", "Alpha", null);
        centre(AUTRE, agence(AUTRE, caisse(AUTRE, "CE", "Caisse etrangere", null), "AGE", "Agence etrangere"), "CPE", "Etranger", null);

        List<ChoixDesPayeurs.DetailDeCentrePayeur> details = choix().detailsDesCentresPayeurs(CENTRE.valeur());

        assertThat(details).extracting(ChoixDesPayeurs.DetailDeCentrePayeur::id).containsExactly(cp1.id(), cp2.id());
        assertThat(details.get(1)).isEqualTo(new ChoixDesPayeurs.DetailDeCentrePayeur(cp2.id(), "CP-2", "Zeta", "Rue 2", "AG-1", "Agence 1",
                "CNAS", "Caisse nationale"));
    }

    /** PL8b : la caisse de chaque agence demandée, SANS borne de centre (reproduction de KB-FAC-65 / KB-RF3-07). */
    @Test
    void la_caisse_d_une_agence_est_publiee_par_identite_d_agence_sans_borne_de_centre() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse nationale", "STANDARD");
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CaisseDAssurance etrangere = caisse(AUTRE, "CE", "Caisse etrangere", null);
        Agence ae = agence(AUTRE, etrangere, "AGE", "Agence etrangere");

        var caisses = publie().caissesDesAgences(List.of(a.id(), ae.id(), UUID.randomUUID()));

        assertThat(caisses).containsOnlyKeys(a.id(), ae.id());
        assertThat(caisses.get(a.id())).isEqualTo(new PayeursDuCentre.CaissePubliee(c.id(), "CNAS", "Caisse nationale", "STANDARD"));
        assertThat(caisses.get(ae.id()).code()).isEqualTo("CE");
        assertThat(publie().caissesDesAgences(List.of())).isEmpty();
    }
}
