package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.contrat.ForfaitsEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Forfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.Anomalie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.BilanDImport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.FicheDeForfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.LigneAImporter;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeForfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeForfait.Motif;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.UsageDuForfait;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** Cas d'usage de la gestion du catalogue de forfaits (RF2), avec le catalogue en mémoire. Aucune règle d'accès : le centre est donné. */
class GererLeCatalogueDeForfaitsTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000a"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000b"));

    private final ForfaitsEnMemoire catalogue = new ForfaitsEnMemoire();
    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<UUID, Long> parLesPec = new HashMap<>();
    private final Map<UUID, Long> parLesFactures = new HashMap<>();
    private final Map<UUID, Long> parLesSeances = new HashMap<>();
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
    private final List<UsageDuForfait> usages = List.of(
            id -> parLesPec.getOrDefault(id, 0L), id -> parLesFactures.getOrDefault(id, 0L), id -> parLesSeances.getOrDefault(id, 0L));
    private final GererLeCatalogueDeForfaits gerer = new GererLeCatalogueDeForfaits(catalogue, catalogue, usages, identifiants, unite);

    private RefusDeForfait refus(Runnable action) {
        try {
            action.run();
        } catch (RefusDeForfait refus) {
            return refus;
        }
        throw new AssertionError("aucun refus");
    }

    private Motif motifDe(Runnable action) {
        return refus(action).motif();
    }

    @Test
    void creer_enregistre_un_forfait_du_centre_avec_une_nouvelle_identite_et_un_prix_arrondi() {
        FicheDeForfait fiche = gerer.creer(CENTRE.valeur(), "  HD-CONV ", " Hémodialyse ", "5600,505");

        assertThat(fiche.id()).isEqualTo(new UUID(8, 1));
        assertThat(fiche.code()).isEqualTo("HD-CONV");
        assertThat(fiche.libelle()).isEqualTo("Hémodialyse");
        assertThat(fiche.prix()).hasToString("5600.51");
        assertThat(catalogue.trouver(CENTRE, fiche.id())).isPresent();
        assertThat(catalogue.trouver(AUTRE, fiche.id())).isEmpty();
    }

    @Test
    void creer_sans_prix_laisse_le_prix_absent_sans_le_refuser() {
        FicheDeForfait fiche = gerer.creer(CENTRE.valeur(), "SANS", "Sans prix", null);

        assertThat(fiche.prix()).isNull();
    }

    @Test
    void creer_refuse_une_saisie_invalide_avec_toutes_ses_anomalies_et_n_ecrit_rien() {
        RefusDeForfait refus = refus(() -> gerer.creer(CENTRE.valeur(), "", "", "-5"));

        assertThat(refus.motif()).isEqualTo(Motif.SAISIE_INVALIDE);
        assertThat(refus.anomalies()).extracting(Anomalie::champ, Anomalie::code, Anomalie::ligne).containsExactly(
                tuple("code", "REQUIRED", 0), tuple("libelle", "REQUIRED", 0), tuple("prix", "NEGATIVE_NUMBER", 0));
        assertThat(catalogue.duCentre(CENTRE)).isEmpty();
    }

    @Test
    void creer_refuse_un_code_deja_porte_dans_le_centre_mais_pas_dans_un_autre() {
        gerer.creer(CENTRE.valeur(), "HD-CONV", "Forfait", null);

        assertThat(motifDe(() -> gerer.creer(CENTRE.valeur(), "  hd-CONV ", "Autre", "1"))).isEqualTo(Motif.DEJA_EXISTANT);
        assertThat(gerer.creer(AUTRE.valeur(), "HD-CONV", "Forfait", null).code()).isEqualTo("HD-CONV");
        assertThat(catalogue.duCentre(CENTRE)).hasSize(1);
    }

    @Test
    void modifier_remplace_tous_les_champs_et_efface_le_prix_omis() {
        UUID id = gerer.creer(CENTRE.valeur(), "HD-CONV", "Forfait", "5600").id();

        FicheDeForfait fiche = gerer.modifier(CENTRE.valeur(), id, "HD-CONV2", "Forfait corrigé", null);

        assertThat(fiche).isEqualTo(new FicheDeForfait(id, "HD-CONV2", "Forfait corrigé", null));
        assertThat(catalogue.trouver(CENTRE, id).orElseThrow().prix()).isNull();
    }

    @Test
    void modifier_un_forfait_inconnu_ou_d_un_autre_centre_est_refuse_avant_toute_validation() {
        UUID autre = gerer.creer(AUTRE.valeur(), "HD-CONV", "Forfait", null).id();

        assertThat(motifDe(() -> gerer.modifier(CENTRE.valeur(), UUID.randomUUID(), "", "", "x"))).isEqualTo(Motif.INTROUVABLE);
        assertThat(motifDe(() -> gerer.modifier(CENTRE.valeur(), autre, "PIRATE", "Pirate", null))).isEqualTo(Motif.INTROUVABLE);
        assertThat(catalogue.trouver(AUTRE, autre).orElseThrow().code()).isEqualTo("HD-CONV");
    }

    @Test
    void modifier_valide_la_saisie_puis_l_unicite_sauf_pour_son_propre_code() {
        UUID premier = gerer.creer(CENTRE.valeur(), "HD-CONV", "Forfait", null).id();
        UUID second = gerer.creer(CENTRE.valeur(), "HD-PREM", "Premium", null).id();

        assertThat(motifDe(() -> gerer.modifier(CENTRE.valeur(), second, "", "Premium", null))).isEqualTo(Motif.SAISIE_INVALIDE);
        assertThat(motifDe(() -> gerer.modifier(CENTRE.valeur(), second, " hd-conv ", "Premium", null))).isEqualTo(Motif.DEJA_EXISTANT);
        assertThat(gerer.modifier(CENTRE.valeur(), premier, "hd-conv", "Forfait", "1").code()).isEqualTo("hd-conv");
    }

    @Test
    void supprimer_refuse_un_forfait_inconnu_ou_utilise_en_additionnant_les_references_de_tous_les_proprietaires() {
        UUID libre = gerer.creer(CENTRE.valeur(), "LIBRE", "Libre", null).id();
        UUID utilise = gerer.creer(CENTRE.valeur(), "UTILISE", "Utilisé", null).id();
        parLesPec.put(utilise, 3L);
        parLesFactures.put(utilise, 1L);
        parLesSeances.put(utilise, 1L);

        assertThat(motifDe(() -> gerer.supprimer(CENTRE.valeur(), UUID.randomUUID()))).isEqualTo(Motif.INTROUVABLE);
        RefusDeForfait refus = refus(() -> gerer.supprimer(CENTRE.valeur(), utilise));
        assertThat(refus.motif()).isEqualTo(Motif.UTILISE);
        assertThat(refus.references()).isEqualTo(5);
        assertThat(catalogue.trouver(CENTRE, utilise)).isPresent();

        gerer.supprimer(CENTRE.valeur(), libre);
        assertThat(catalogue.trouver(CENTRE, libre)).isEmpty();
        assertThat(motifDe(() -> gerer.supprimer(CENTRE.valeur(), libre))).isEqualTo(Motif.INTROUVABLE);
    }

    @Test
    void une_seule_reference_d_un_seul_proprietaire_suffit_a_refuser_la_suppression() {
        UUID impose = gerer.creer(CENTRE.valeur(), "IMPOSE", "Imposé à une séance", null).id();
        parLesSeances.put(impose, 1L);

        assertThat(refus(() -> gerer.supprimer(CENTRE.valeur(), impose)).references()).isEqualTo(1);
    }

    @Test
    void lister_rend_la_page_du_centre_triee_par_code_avec_le_prix() {
        gerer.creer(CENTRE.valeur(), "B", "Beta", "0");
        gerer.creer(CENTRE.valeur(), "A", "Alpha", null);
        gerer.creer(AUTRE.valeur(), "G", "Gamma", null);

        var page = gerer.lister(CENTRE.valeur(), null, 0, 20);

        assertThat(page.lignes()).extracting(FicheDeForfait::code, FicheDeForfait::prix).containsExactly(
                tuple("A", null), tuple("B", new BigDecimal("0.00")));
        assertThat(page.total()).isEqualTo(2);
    }

    // ---- import

    private static LigneAImporter ligne(int numero, String code, String libelle, String prix) {
        return new LigneAImporter(numero, code, libelle, prix);
    }

    @Test
    void la_verification_d_un_import_compte_les_creations_et_les_mises_a_jour_sans_rien_ecrire() {
        gerer.creer(CENTRE.valeur(), "HD-CONV", "Ancien", "1");

        BilanDImport bilan = gerer.importer(CENTRE.valeur(),
                List.of(ligne(2, "hd-conv", "Nouveau", "5600,5"), ligne(3, "HD-NEW", "Neuf", null)), false);

        assertThat(bilan).isEqualTo(new BilanDImport(1, 1, List.of(), false));
        assertThat(catalogue.duCentre(CENTRE)).hasSize(1);
        assertThat(catalogue.duCentre(CENTRE).get(0).libelle()).isEqualTo("Ancien");
    }

    @Test
    void l_import_ecrit_les_creations_et_remplace_les_lignes_connues_par_leur_cle_y_compris_le_prix_efface() {
        UUID connu = gerer.creer(CENTRE.valeur(), "HD-CONV", "Ancien", "1").id();

        BilanDImport bilan = gerer.importer(CENTRE.valeur(),
                List.of(ligne(2, "hd-conv", "Nouveau", null), ligne(3, " HD-NEW ", "Neuf", "0")), true);

        assertThat(bilan).isEqualTo(new BilanDImport(1, 1, List.of(), true));
        Forfait misAJour = catalogue.trouver(CENTRE, connu).orElseThrow();
        assertThat(misAJour.code()).as("le code du fichier remplace celui du catalogue").isEqualTo("hd-conv");
        assertThat(misAJour.libelle()).isEqualTo("Nouveau");
        assertThat(misAJour.prix()).isNull();
        assertThat(catalogue.duCentre(CENTRE)).extracting(Forfait::code).containsExactlyInAnyOrder("hd-conv", "HD-NEW");
        assertThat(catalogue.duCentre(AUTRE)).isEmpty();
    }

    @Test
    void une_seule_anomalie_refuse_tout_le_fichier_avec_le_numero_de_chaque_ligne_et_n_ecrit_rien() {
        BilanDImport bilan = gerer.importer(CENTRE.valeur(), List.of(
                ligne(2, "OK", "Valide", null),
                ligne(3, "AUTRE", "Autre", "abc"),
                ligne(4, "", "Sans code", null),
                ligne(5, "ok", "Doublon", null),
                ligne(6, "OK ", "Doublon bis", "1")), true);

        assertThat(bilan.appliquee()).isFalse();
        assertThat(bilan.aCreer()).isEqualTo(1);
        assertThat(bilan.aMettreAJour()).isZero();
        assertThat(bilan.anomalies()).extracting(Anomalie::ligne, Anomalie::champ, Anomalie::code).containsExactly(
                tuple(3, "prix", "INVALID_NUMBER"), tuple(4, "code", "REQUIRED"),
                tuple(5, "code", "DUPLICATE_IN_FILE"), tuple(6, "code", "DUPLICATE_IN_FILE"));
        assertThat(bilan.anomalies().get(2).message()).isEqualTo("Doublon : même code qu'à la ligne 2.");
        assertThat(bilan.anomalies().get(2).parametres()).isEqualTo(Map.of("firstRow", "2"));
        assertThat(catalogue.duCentre(CENTRE)).isEmpty();
    }
}
