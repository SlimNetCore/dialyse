package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.contrat.TransporteursEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Transporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.Anomalie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.BilanDImport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.FicheDeTransporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.LigneAImporter;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeTransporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeTransporteur.Motif;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage de la gestion des transporteurs (RF1), avec le carnet en mémoire. Aucune règle d'accès : le centre est donné. */
class GererLesTransporteursTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000a"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("99997700-0000-0000-0000-00000000000b"));

    private final TransporteursEnMemoire carnet = new TransporteursEnMemoire();
    private final AtomicInteger sequence = new AtomicInteger();
    private final Identifiants identifiants = new Identifiants() {
        @Override
        public UUID nouveau() {
            return new UUID(7, sequence.incrementAndGet());
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
    private final GererLesTransporteurs gerer = new GererLesTransporteurs(carnet, carnet, identifiants, unite);

    private Motif motifDe(Runnable action) {
        try {
            action.run();
        } catch (RefusDeTransporteur refus) {
            return refus.motif();
        }
        throw new AssertionError("aucun refus");
    }

    private RefusDeTransporteur refus(Runnable action) {
        try {
            action.run();
        } catch (RefusDeTransporteur refus) {
            return refus;
        }
        throw new AssertionError("aucun refus");
    }

    @Test
    void creer_enregistre_un_transporteur_du_centre_avec_une_nouvelle_identite() {
        FicheDeTransporteur fiche = gerer.creer(CENTRE.valeur(), "  Ambulances Rouiba ", "0795 00 61 36");

        assertThat(fiche).isEqualTo(new FicheDeTransporteur(new UUID(7, 1), "Ambulances Rouiba", "0795 00 61 36"));
        assertThat(carnet.trouver(CENTRE, fiche.id())).isPresent();
        assertThat(carnet.trouver(AUTRE, fiche.id())).isEmpty();
    }

    @Test
    void creer_refuse_une_saisie_invalide_avec_toutes_ses_anomalies_et_n_ecrit_rien() {
        RefusDeTransporteur refus = refus(() -> gerer.creer(CENTRE.valeur(), "", "x"));

        assertThat(refus.motif()).isEqualTo(Motif.SAISIE_INVALIDE);
        assertThat(refus.anomalies()).extracting(Anomalie::champ, Anomalie::code, Anomalie::ligne)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("nom", "REQUIRED", 0),
                        org.assertj.core.groups.Tuple.tuple("telephone", "INVALID_PHONE", 0));
        assertThat(carnet.duCentre(CENTRE)).isEmpty();
    }

    @Test
    void creer_refuse_un_nom_deja_porte_dans_le_centre_mais_pas_dans_un_autre() {
        gerer.creer(CENTRE.valeur(), "Taxi Medical", null);

        assertThat(motifDe(() -> gerer.creer(CENTRE.valeur(), "  TAXI   medical ", null))).isEqualTo(Motif.DEJA_EXISTANT);
        assertThat(gerer.creer(AUTRE.valeur(), "Taxi Medical", null).nom()).isEqualTo("Taxi Medical");
        assertThat(carnet.duCentre(CENTRE)).hasSize(1);
    }

    @Test
    void modifier_remplace_le_nom_et_le_telephone() {
        UUID id = gerer.creer(CENTRE.valeur(), "Taxi", "0795006136").id();

        FicheDeTransporteur fiche = gerer.modifier(CENTRE.valeur(), id, "Taxi Medical", null);

        assertThat(fiche).isEqualTo(new FicheDeTransporteur(id, "Taxi Medical", null));
        assertThat(carnet.trouver(CENTRE, id).orElseThrow().nom()).isEqualTo("Taxi Medical");
    }

    @Test
    void modifier_un_transporteur_inconnu_ou_d_un_autre_centre_est_refuse_avant_toute_validation() {
        UUID autre = gerer.creer(AUTRE.valeur(), "Taxi", null).id();

        assertThat(motifDe(() -> gerer.modifier(CENTRE.valeur(), UUID.randomUUID(), "", null))).isEqualTo(Motif.INTROUVABLE);
        assertThat(motifDe(() -> gerer.modifier(CENTRE.valeur(), autre, "Pirate", null))).isEqualTo(Motif.INTROUVABLE);
        assertThat(carnet.trouver(AUTRE, autre).orElseThrow().nom()).isEqualTo("Taxi");
    }

    @Test
    void modifier_valide_la_saisie_puis_l_unicite_sauf_pour_son_propre_nom() {
        UUID premier = gerer.creer(CENTRE.valeur(), "Taxi", null).id();
        UUID second = gerer.creer(CENTRE.valeur(), "Ambulance", null).id();

        assertThat(motifDe(() -> gerer.modifier(CENTRE.valeur(), second, "", null))).isEqualTo(Motif.SAISIE_INVALIDE);
        assertThat(motifDe(() -> gerer.modifier(CENTRE.valeur(), second, " taxi ", null))).isEqualTo(Motif.DEJA_EXISTANT);
        assertThat(gerer.modifier(CENTRE.valeur(), premier, "TAXI", "0795006136").nom()).isEqualTo("TAXI");
    }

    @Test
    void supprimer_refuse_un_transporteur_inconnu_ou_utilise_en_donnant_le_nombre_de_references() {
        UUID libre = gerer.creer(CENTRE.valeur(), "Libre", null).id();
        UUID utilise = gerer.creer(CENTRE.valeur(), "Utilise", null).id();
        UUID utiliseAilleurs = gerer.creer(CENTRE.valeur(), "Utilise ailleurs", null).id();
        carnet.utiliser(CENTRE, utilise, 3);
        carnet.utiliser(AUTRE, utiliseAilleurs, 2);

        assertThat(motifDe(() -> gerer.supprimer(CENTRE.valeur(), UUID.randomUUID()))).isEqualTo(Motif.INTROUVABLE);
        RefusDeTransporteur refus = refus(() -> gerer.supprimer(CENTRE.valeur(), utilise));
        assertThat(refus.motif()).isEqualTo(Motif.UTILISE);
        assertThat(refus.references()).isEqualTo(3);
        assertThat(carnet.trouver(CENTRE, utilise)).isPresent();

        // KB-RF1-03 corrigé : seuls les patients du centre du transporteur le retiennent
        gerer.supprimer(CENTRE.valeur(), utiliseAilleurs);
        assertThat(carnet.trouver(CENTRE, utiliseAilleurs)).isEmpty();

        gerer.supprimer(CENTRE.valeur(), libre);
        assertThat(carnet.trouver(CENTRE, libre)).isEmpty();
        assertThat(motifDe(() -> gerer.supprimer(CENTRE.valeur(), libre))).isEqualTo(Motif.INTROUVABLE);
    }

    @Test
    void lister_rend_la_page_du_centre_triee_par_nom() {
        gerer.creer(CENTRE.valeur(), "Beta", null);
        gerer.creer(CENTRE.valeur(), "Alpha", "0795006136");
        gerer.creer(AUTRE.valeur(), "Gamma", null);

        var page = gerer.lister(CENTRE.valeur(), null, 0, 20);

        assertThat(page.lignes()).extracting(FicheDeTransporteur::nom).containsExactly("Alpha", "Beta");
        assertThat(page.total()).isEqualTo(2);
    }

    // ---- import

    private static LigneAImporter ligne(int numero, String nom, String telephone) {
        return new LigneAImporter(numero, nom, telephone);
    }

    @Test
    void la_verification_d_un_import_compte_les_creations_et_les_mises_a_jour_sans_rien_ecrire() {
        gerer.creer(CENTRE.valeur(), "Ambulances Rouiba", null);

        BilanDImport bilan = gerer.importer(CENTRE.valeur(),
                List.of(ligne(2, "ambulances  rouiba", "0795006136"), ligne(3, "Taxi", null)), false);

        assertThat(bilan).isEqualTo(new BilanDImport(1, 1, List.of(), false));
        assertThat(carnet.duCentre(CENTRE)).hasSize(1);
        assertThat(carnet.duCentre(CENTRE).get(0).telephone()).isNull();
    }

    @Test
    void l_import_ecrit_les_creations_et_remplace_les_lignes_connues_par_leur_cle() {
        UUID connu = gerer.creer(CENTRE.valeur(), "Ambulances Rouiba", "0111 11 11 11").id();

        BilanDImport bilan = gerer.importer(CENTRE.valeur(),
                List.of(ligne(2, "ambulances  rouiba", "0795006136"), ligne(3, " Taxi ", "021 12 34 56")), true);

        assertThat(bilan).isEqualTo(new BilanDImport(1, 1, List.of(), true));
        Transporteur mis_a_jour = carnet.trouver(CENTRE, connu).orElseThrow();
        assertThat(mis_a_jour.nom()).as("seuls les bords sont nettoyés").isEqualTo("ambulances  rouiba");
        assertThat(mis_a_jour.telephone()).isEqualTo("0795006136");
        assertThat(carnet.duCentre(CENTRE)).extracting(Transporteur::nom).containsExactlyInAnyOrder("ambulances  rouiba", "Taxi");
        assertThat(carnet.duCentre(AUTRE)).isEmpty();
    }

    @Test
    void une_seule_anomalie_refuse_tout_le_fichier_avec_le_numero_de_chaque_ligne_et_n_ecrit_rien() {
        BilanDImport bilan = gerer.importer(CENTRE.valeur(), List.of(
                ligne(2, "Valide", null),
                ligne(3, "Autre", "abc"),
                ligne(4, "", null),
                ligne(5, "VALIDE", null),
                ligne(6, "valide ", "0795006136")), true);

        assertThat(bilan.appliquee()).isFalse();
        assertThat(bilan.aCreer()).isEqualTo(1);
        assertThat(bilan.aMettreAJour()).isZero();
        assertThat(bilan.anomalies()).extracting(Anomalie::ligne, Anomalie::champ, Anomalie::code).containsExactly(
                org.assertj.core.groups.Tuple.tuple(3, "telephone", "INVALID_PHONE"),
                org.assertj.core.groups.Tuple.tuple(4, "nom", "REQUIRED"),
                org.assertj.core.groups.Tuple.tuple(5, "nom", "DUPLICATE_IN_FILE"),
                org.assertj.core.groups.Tuple.tuple(6, "nom", "DUPLICATE_IN_FILE"));
        assertThat(bilan.anomalies().get(2).message()).isEqualTo("Doublon : même nom qu'à la ligne 2.");
        assertThat(bilan.anomalies().get(2).parametres()).isEqualTo(Map.of("firstRow", "2"));
        assertThat(carnet.duCentre(CENTRE)).isEmpty();
    }
}
