package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.contrat.PayeursEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.Anomalie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.BilanDImport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.FicheDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.LigneAImporter;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.Niveau;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.SaisieDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDePayeur.Motif;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Cas d'usage de la gestion de la chaîne des payeurs (RF3a), avec les ports en mémoire. Aucune règle d'accès : le centre est donné
 * (l'administration des référentiels l'a déjà contrôlé).
 */
class GererLesPayeursTest {

    private static final UUID CENTRE = UUID.fromString("99998300-0000-0000-0000-00000000000a");
    private static final UUID AUTRE = UUID.fromString("99998300-0000-0000-0000-00000000000b");

    private final PayeursEnMemoire payeurs = new PayeursEnMemoire();
    private final AtomicInteger sequence = new AtomicInteger();
    private final Identifiants identifiants = new Identifiants() {
        @Override
        public UUID nouveau() {
            return new UUID(8, sequence.incrementAndGet());
        }

        @Override
        public String nouveauCodePatient() {
            throw new UnsupportedOperationException();
        }
    };
    private final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }
    };
    private final GererLesPayeurs gerer = new GererLesPayeurs(payeurs.caisses(), payeurs.agences(), payeurs.centresPayeurs(), payeurs,
            payeurs, identifiants, unite);

    private static SaisieDePayeur caisse(String code, String nom, String type) {
        return new SaisieDePayeur(code, nom, type, null, null);
    }

    private static SaisieDePayeur agence(String code, String nom, String caisse) {
        return new SaisieDePayeur(code, nom, null, null, caisse);
    }

    private static SaisieDePayeur centre(String code, String nom, String adresse, String agence) {
        return new SaisieDePayeur(code, nom, null, adresse, agence);
    }

    private RefusDePayeur refus(Runnable action) {
        try {
            action.run();
        } catch (RefusDePayeur refus) {
            return refus;
        }
        throw new AssertionError("aucun refus");
    }

    private Motif motifDe(Runnable action) {
        return refus(action).motif();
    }

    private FicheDePayeur uneCaisse(String code) {
        return gerer.creer(CENTRE, Niveau.CAISSE, caisse(code, "Caisse " + code, null));
    }

    private FicheDePayeur uneAgence(String code, String caisse) {
        return gerer.creer(CENTRE, Niveau.AGENCE, agence(code, "Agence " + code, caisse));
    }

    private FicheDePayeur unCentre(String code, String agence) {
        return gerer.creer(CENTRE, Niveau.CENTRE_PAYEUR, centre(code, "Centre " + code, null, agence));
    }

    // ---- création

    @Test
    void creer_une_caisse_l_enregistre_dans_le_centre_avec_une_nouvelle_identite_et_le_type_standard_par_defaut() {
        FicheDePayeur fiche = gerer.creer(CENTRE, Niveau.CAISSE, caisse("  CNAS ", " Caisse nationale ", null));

        assertThat(fiche.id()).isEqualTo(new UUID(8, 1));
        assertThat(fiche.code()).isEqualTo("CNAS");
        assertThat(fiche.nom()).isEqualTo("Caisse nationale");
        assertThat(fiche.typeCaisse()).isEqualTo("STANDARD");
        assertThat(fiche.parent()).isNull();
        assertThat(payeurs.caisses().trouver(new com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId(CENTRE), fiche.id())).isPresent();
        assertThat(payeurs.caisses().trouver(new com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId(AUTRE), fiche.id())).isEmpty();
    }

    @Test
    void creer_une_agence_la_rattache_a_sa_caisse_et_rend_le_libelle_de_la_caisse() {
        FicheDePayeur caisse = uneCaisse("CNAS");

        FicheDePayeur agence = gerer.creer(CENTRE, Niveau.AGENCE, agence("AG-1", "Agence 1", " cnas "));

        assertThat(agence.parent()).isEqualTo(caisse.id());
        assertThat(agence.parentLibelle()).isEqualTo("CNAS · Caisse CNAS");
    }

    @Test
    void creer_un_centre_payeur_le_rattache_a_son_agence_et_garde_l_adresse() {
        FicheDePayeur caisse = uneCaisse("CNAS");
        FicheDePayeur agence = uneAgence("AG-1", caisse.id().toString());

        FicheDePayeur centre = gerer.creer(CENTRE, Niveau.CENTRE_PAYEUR, centre("CP-1", "Centre 1", " 12 rue Didouche Mourad ", "AG-1"));

        assertThat(centre.parent()).isEqualTo(agence.id());
        assertThat(centre.adresse()).isEqualTo("12 rue Didouche Mourad");
        assertThat(centre.parentLibelle()).isEqualTo("AG-1 · Agence AG-1");
    }

    @Test
    void creer_refuse_une_saisie_invalide_avec_toutes_ses_anomalies_et_n_ecrit_rien() {
        RefusDePayeur refus = refus(() -> gerer.creer(CENTRE, Niveau.CAISSE, caisse("", "", "premium")));

        assertThat(refus.motif()).isEqualTo(Motif.SAISIE_INVALIDE);
        assertThat(refus.anomalies()).extracting(Anomalie::champ, Anomalie::code, Anomalie::ligne).containsExactly(
                tuple("code", "REQUIRED", 0), tuple("nom", "REQUIRED", 0), tuple("typeCaisse", "INVALID_VALUE", 0));
        assertThat(gerer.toutes(CENTRE, Niveau.CAISSE)).isEmpty();
    }

    @Test
    void creer_refuse_un_code_deja_porte_dans_le_niveau_et_le_centre_mais_pas_dans_un_autre_centre_ni_un_autre_niveau() {
        uneCaisse("CNAS");

        assertThat(motifDe(() -> gerer.creer(CENTRE, Niveau.CAISSE, caisse("  cnas ", "Autre", null)))).isEqualTo(Motif.DEJA_EXISTANT);
        assertThat(gerer.creer(AUTRE, Niveau.CAISSE, caisse("CNAS", "Caisse", null)).code()).isEqualTo("CNAS");
        assertThat(uneAgence("CNAS", "CNAS").code()).isEqualTo("CNAS");
        assertThat(gerer.toutes(CENTRE, Niveau.CAISSE)).hasSize(1);
    }

    @Test
    void creer_une_agence_refuse_une_caisse_d_un_autre_centre() {
        gerer.creer(AUTRE, Niveau.CAISSE, caisse("CNAS-B", "Caisse B", null));

        RefusDePayeur refus = refus(() -> uneAgence("AG-1", "CNAS-B"));

        assertThat(refus.motif()).isEqualTo(Motif.SAISIE_INVALIDE);
        assertThat(refus.anomalies()).extracting(Anomalie::champ, Anomalie::code).containsExactly(tuple("caisse", "REFERENCE_NOT_FOUND"));
    }

    // ---- modification

    @Test
    void modifier_remplace_tous_les_champs_et_garde_l_identite() {
        FicheDePayeur c = gerer.creer(CENTRE, Niveau.CAISSE, caisse("CNAS", "Caisse", "VACANCIER"));

        FicheDePayeur modifiee = gerer.modifier(CENTRE, Niveau.CAISSE, c.id(), caisse("CNAS2", "Renommee", null));

        assertThat(modifiee.id()).isEqualTo(c.id());
        assertThat(modifiee.code()).isEqualTo("CNAS2");
        assertThat(modifiee.typeCaisse()).isEqualTo("STANDARD");
        assertThat(gerer.toutes(CENTRE, Niveau.CAISSE)).extracting(FicheDePayeur::code).containsExactly("CNAS2");
    }

    @Test
    void modifier_une_agence_peut_changer_de_caisse() {
        uneCaisse("CNAS");
        FicheDePayeur casnos = uneCaisse("CASNOS");
        FicheDePayeur agence = uneAgence("AG-1", "CNAS");

        FicheDePayeur modifiee = gerer.modifier(CENTRE, Niveau.AGENCE, agence.id(), agence("AG-1", "Agence 1", "CASNOS"));

        assertThat(modifiee.parent()).isEqualTo(casnos.id());
        assertThat(modifiee.parentLibelle()).isEqualTo("CASNOS · Caisse CASNOS");
    }

    @Test
    void modifier_peut_garder_son_propre_code_a_la_casse_pres_mais_pas_prendre_celui_d_un_autre() {
        FicheDePayeur a = uneCaisse("CNAS");
        FicheDePayeur b = uneCaisse("CASNOS");

        assertThat(gerer.modifier(CENTRE, Niveau.CAISSE, a.id(), caisse("cnas", "Caisse", null)).code()).isEqualTo("cnas");
        assertThat(motifDe(() -> gerer.modifier(CENTRE, Niveau.CAISSE, b.id(), caisse(" Cnas ", "Caisse", null))))
                .isEqualTo(Motif.DEJA_EXISTANT);
    }

    @Test
    void modifier_un_payeur_inconnu_ou_d_un_autre_centre_est_introuvable_avant_toute_validation() {
        FicheDePayeur b = gerer.creer(AUTRE, Niveau.CAISSE, caisse("CNAS", "Caisse B", null));

        assertThat(motifDe(() -> gerer.modifier(CENTRE, Niveau.CAISSE, b.id(), caisse("", "", null)))).isEqualTo(Motif.INTROUVABLE);
        assertThat(motifDe(() -> gerer.modifier(CENTRE, Niveau.CAISSE, UUID.randomUUID(), caisse("X", "X", null))))
                .isEqualTo(Motif.INTROUVABLE);
    }

    // ---- suppression

    @Test
    void supprimer_une_caisse_est_refusee_tant_que_des_agences_la_referencent_et_rend_leur_nombre() {
        FicheDePayeur caisse = uneCaisse("CNAS");
        uneAgence("AG-1", "CNAS");
        uneAgence("AG-2", "CNAS");

        RefusDePayeur refus = refus(() -> gerer.supprimer(CENTRE, Niveau.CAISSE, caisse.id()));

        assertThat(refus.motif()).isEqualTo(Motif.UTILISE);
        assertThat(refus.references()).isEqualTo(2);
        assertThat(gerer.toutes(CENTRE, Niveau.CAISSE)).hasSize(1);
    }

    @Test
    void supprimer_une_agence_est_refusee_tant_qu_un_centre_payeur_la_reference() {
        uneCaisse("CNAS");
        FicheDePayeur agence = uneAgence("AG-1", "CNAS");
        FicheDePayeur centre = unCentre("CP-1", "AG-1");

        assertThat(refus(() -> gerer.supprimer(CENTRE, Niveau.AGENCE, agence.id())).references()).isEqualTo(1);
        gerer.supprimer(CENTRE, Niveau.CENTRE_PAYEUR, centre.id());
        gerer.supprimer(CENTRE, Niveau.AGENCE, agence.id());
        assertThat(gerer.toutes(CENTRE, Niveau.AGENCE)).isEmpty();
    }

    @Test
    void supprimer_un_centre_payeur_est_refuse_tant_que_des_patients_en_relevent() {
        uneCaisse("CNAS");
        uneAgence("AG-1", "CNAS");
        FicheDePayeur centre = unCentre("CP-1", "AG-1");
        payeurs.patientsDuCentrePayeur(centre.id(), 3);

        RefusDePayeur refus = refus(() -> gerer.supprimer(CENTRE, Niveau.CENTRE_PAYEUR, centre.id()));

        assertThat(refus.motif()).isEqualTo(Motif.UTILISE);
        assertThat(refus.references()).isEqualTo(3);
        payeurs.patientsDuCentrePayeur(centre.id(), 0);
        gerer.supprimer(CENTRE, Niveau.CENTRE_PAYEUR, centre.id());
        assertThat(gerer.toutes(CENTRE, Niveau.CENTRE_PAYEUR)).isEmpty();
    }

    @Test
    void supprimer_un_payeur_inconnu_ou_d_un_autre_centre_est_introuvable() {
        FicheDePayeur b = gerer.creer(AUTRE, Niveau.CAISSE, caisse("CNAS", "Caisse B", null));

        assertThat(motifDe(() -> gerer.supprimer(CENTRE, Niveau.CAISSE, b.id()))).isEqualTo(Motif.INTROUVABLE);
        assertThat(motifDe(() -> gerer.supprimer(CENTRE, Niveau.CAISSE, UUID.randomUUID()))).isEqualTo(Motif.INTROUVABLE);
        assertThat(gerer.toutes(AUTRE, Niveau.CAISSE)).hasSize(1);
    }

    // ---- lecture

    @Test
    void lister_rend_la_page_triee_par_code_du_centre_avec_le_libelle_du_parent() {
        uneCaisse("B-CA");
        uneCaisse("A-CA");
        gerer.creer(AUTRE, Niveau.CAISSE, caisse("Z-CA", "Autre centre", null));
        uneAgence("AG-1", "A-CA");

        var caisses = gerer.lister(CENTRE, Niveau.CAISSE, null, 0, 20);
        var agences = gerer.lister(CENTRE, Niveau.AGENCE, null, 0, 20);

        assertThat(caisses.lignes()).extracting(FicheDePayeur::code).containsExactly("A-CA", "B-CA");
        assertThat(caisses.total()).isEqualTo(2);
        assertThat(agences.lignes().getFirst().parentLibelle()).isEqualTo("A-CA · Caisse A-CA");
    }

    // ---- import

    private static LigneAImporter ligne(int numero, SaisieDePayeur saisie) {
        return new LigneAImporter(numero, saisie);
    }

    @Test
    void importer_cree_les_nouveaux_payeurs_et_met_a_jour_les_connus_par_leur_code_sans_casse() {
        FicheDePayeur connue = uneCaisse("CNAS");

        BilanDImport bilan = gerer.importer(CENTRE, Niveau.CAISSE, List.of(
                ligne(2, caisse("cnas", "Caisse nationale", "VACANCIER")), ligne(3, caisse("NEW", "Nouvelle", null))), true);

        assertThat(bilan.aCreer()).isEqualTo(1);
        assertThat(bilan.aMettreAJour()).isEqualTo(1);
        assertThat(bilan.anomalies()).isEmpty();
        assertThat(bilan.appliquee()).isTrue();
        assertThat(gerer.toutes(CENTRE, Niveau.CAISSE)).extracting(FicheDePayeur::id, FicheDePayeur::code, FicheDePayeur::typeCaisse)
                .containsExactlyInAnyOrder(tuple(connue.id(), "cnas", "VACANCIER"), tuple(new UUID(8, 2), "NEW", "STANDARD"));
    }

    @Test
    void importer_en_verification_seule_compte_sans_rien_ecrire() {
        BilanDImport bilan = gerer.importer(CENTRE, Niveau.CAISSE, List.of(ligne(2, caisse("A", "A", null))), false);

        assertThat(bilan.aCreer()).isEqualTo(1);
        assertThat(bilan.appliquee()).isFalse();
        assertThat(gerer.toutes(CENTRE, Niveau.CAISSE)).isEmpty();
    }

    @Test
    void importer_refuse_le_fichier_entier_des_qu_une_ligne_est_en_anomalie_et_les_rend_toutes() {
        uneCaisse("CNAS");

        BilanDImport bilan = gerer.importer(CENTRE, Niveau.CAISSE, List.of(
                ligne(2, caisse("OK", "Valide", null)),
                ligne(3, caisse("CNAS", "Existante", "PREMIUM")),
                ligne(4, caisse("", "Sans code", null)),
                ligne(5, caisse("ok ", "Doublon", null))), true);

        assertThat(bilan.appliquee()).isFalse();
        assertThat(bilan.aCreer()).isEqualTo(1);
        assertThat(bilan.anomalies()).extracting(Anomalie::ligne, Anomalie::champ, Anomalie::code).containsExactly(
                tuple(3, "typeCaisse", "INVALID_VALUE"), tuple(4, "code", "REQUIRED"), tuple(5, "code", "DUPLICATE_IN_FILE"));
        assertThat(bilan.anomalies().getLast().message()).isEqualTo("Doublon : même code qu'à la ligne 2.");
        assertThat(bilan.anomalies().getLast().parametres()).containsEntry("firstRow", "2");
        assertThat(gerer.toutes(CENTRE, Niveau.CAISSE)).hasSize(1);
    }

    @Test
    void importer_des_agences_dont_la_caisse_n_existe_pas_encore_les_refuse_meme_si_le_meme_fichier_la_creerait() {
        BilanDImport bilan = gerer.importer(CENTRE, Niveau.AGENCE, List.of(ligne(2, agence("AG-1", "Agence 1", "CNAS"))), true);

        assertThat(bilan.appliquee()).isFalse();
        assertThat(bilan.anomalies()).extracting(Anomalie::ligne, Anomalie::champ, Anomalie::code)
                .containsExactly(tuple(2, "caisse", "REFERENCE_NOT_FOUND"));
        assertThat(gerer.toutes(CENTRE, Niveau.AGENCE)).isEmpty();
    }

    @Test
    void importer_les_trois_niveaux_dans_l_ordre_puis_rejouer_met_tout_a_jour() {
        gerer.importer(CENTRE, Niveau.CAISSE, List.of(ligne(2, caisse("CNAS", "Caisse", null))), true);
        gerer.importer(CENTRE, Niveau.AGENCE, List.of(ligne(2, agence("AG-1", "Agence", "cnas"))), true);
        BilanDImport centres = gerer.importer(CENTRE, Niveau.CENTRE_PAYEUR,
                List.of(ligne(2, centre("CP-1", "Centre", "Adresse", "AG-1"))), true);
        BilanDImport rejeu = gerer.importer(CENTRE, Niveau.CENTRE_PAYEUR,
                List.of(ligne(2, centre("CP-1", "Centre renomme", null, "AG-1"))), true);

        assertThat(centres.aCreer()).isEqualTo(1);
        assertThat(rejeu.aMettreAJour()).isEqualTo(1);
        assertThat(gerer.toutes(CENTRE, Niveau.CENTRE_PAYEUR)).extracting(FicheDePayeur::nom, FicheDePayeur::adresse)
                .containsExactly(tuple("Centre renomme", null));
    }
}
