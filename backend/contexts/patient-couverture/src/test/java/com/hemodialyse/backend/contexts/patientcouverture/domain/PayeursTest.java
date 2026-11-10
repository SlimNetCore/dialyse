package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Règles de la chaîne des payeurs (RF3a) : caisse d'assurance, agence, centre payeur. Code (50 caractères au plus) et nom (255 au plus)
 * obligatoires ; la clé naturelle est le code, sans casse ni espaces répétés, dans le centre ; le type de caisse vaut STANDARD ou
 * VACANCIER (STANDARD à défaut) ; l'adresse du centre payeur est facultative ; une agence relève d'une caisse et un centre payeur d'une
 * agence DU MÊME CENTRE, désignée par son code ou son identifiant. Les messages sont ceux de l'administration actuelle (refonte sur place).
 */
class PayeursTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99998300-0000-0000-0000-00000000000a"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("99998300-0000-0000-0000-00000000000b"));
    private static final UUID ID = UUID.fromString("99998300-0000-0000-0000-0000000000f1");

    private static CaisseDAssurance caisse(String code, String nom, String type) {
        return CaisseDAssurance.referencer(ID, CENTRE, code, nom, type);
    }

    private static List<AnomalieDeSaisie> refusees(Runnable saisie) {
        try {
            saisie.run();
        } catch (SaisieDePayeurInvalide refus) {
            return refus.anomalies();
        }
        throw new AssertionError("saisie acceptee");
    }

    // ---- caisse d'assurance

    @Test
    void une_caisse_garde_son_code_son_nom_et_son_type_nettoyes_des_espaces_et_des_espaces_insecables() {
        CaisseDAssurance c = caisse("  CNAS ", " Caisse nationale ", " vacancier ");

        assertThat(c.code()).isEqualTo("CNAS");
        assertThat(c.nom()).isEqualTo("Caisse nationale");
        assertThat(c.type()).isEqualTo("VACANCIER");
        assertThat(c.centre()).isEqualTo(CENTRE);
        assertThat(c.niveau()).isEqualTo(NiveauDePayeur.CAISSE);
    }

    @Test
    void le_type_de_caisse_est_standard_a_defaut_et_les_accents_tirets_et_espaces_sont_normalises() {
        assertThat(caisse("A", "A", null).type()).isEqualTo("STANDARD");
        assertThat(caisse("A", "A", "   ").type()).isEqualTo("STANDARD");
        assertThat(caisse("A", "A", "standard").type()).isEqualTo("STANDARD");
        assertThat(caisse("A", "A", "Vacancier").type()).isEqualTo("VACANCIER");
    }

    @Test
    void un_type_inconnu_est_refuse_avec_les_valeurs_autorisees() {
        List<AnomalieDeSaisie> anomalies = refusees(() -> caisse("A", "A", "premium"));

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.getFirst().champ()).isEqualTo("typeCaisse");
        assertThat(anomalies.getFirst().code()).isEqualTo("INVALID_VALUE");
        assertThat(anomalies.getFirst().message())
                .isEqualTo("« Type de caisse » : « premium » n'est pas une valeur autorisée (STANDARD, VACANCIER).");
        assertThat(anomalies.getFirst().parametres()).isEqualTo(Map.of("value", "premium", "allowed", "STANDARD, VACANCIER"));
    }

    @Test
    void toutes_les_anomalies_d_une_caisse_sont_rendues_ensemble_dans_l_ordre_code_nom_type() {
        List<AnomalieDeSaisie> anomalies = refusees(() -> caisse("", " ", "premium"));

        assertThat(anomalies).extracting(AnomalieDeSaisie::champ, AnomalieDeSaisie::code)
                .containsExactly(tuple("code", "REQUIRED"), tuple("nom", "REQUIRED"), tuple("typeCaisse", "INVALID_VALUE"));
        assertThat(anomalies.getFirst().message()).isEqualTo("« Code » est obligatoire.");
        assertThat(anomalies.getFirst().parametres()).isEmpty();
    }

    @Test
    void le_code_tient_en_cinquante_caracteres_et_le_nom_en_deux_cent_cinquante_cinq() {
        assertThat(caisse("C".repeat(50), "N".repeat(255), null).code()).hasSize(50);

        List<AnomalieDeSaisie> anomalies = refusees(() -> caisse("C".repeat(51), "N".repeat(256), null));
        assertThat(anomalies).extracting(AnomalieDeSaisie::champ, AnomalieDeSaisie::code)
                .containsExactly(tuple("code", "TOO_LONG"), tuple("nom", "TOO_LONG"));
        assertThat(anomalies.getFirst().message()).isEqualTo("« Code » dépasse 50 caractères (51).");
        assertThat(anomalies.getFirst().parametres()).isEqualTo(Map.of("max", "50", "length", "51"));
        assertThat(anomalies.get(1).message()).isEqualTo("« Nom » dépasse 255 caractères (256).");
    }

    @Test
    void la_cle_naturelle_ignore_la_casse_et_les_espaces_repetes() {
        assertThat(caisse("  Cnas   Est ", "Caisse", null).cle()).isEqualTo("cnas est");
        assertThat(Payeur.cleDe("  A   B ")).isEqualTo("a b");
        assertThat(Payeur.cleDe(null)).isEmpty();
    }

    @Test
    void un_autre_payeur_du_centre_de_meme_cle_est_un_doublon_mais_pas_le_payeur_lui_meme() {
        CaisseDAssurance existante = caisse("CNAS", "Caisse", null);
        CaisseDAssurance autre = CaisseDAssurance.referencer(UUID.randomUUID(), CENTRE, " cnas ", "Autre", null);

        assertThatThrownBy(() -> autre.verifierLUnicite(List.of(existante))).isInstanceOf(PayeurDejaExistant.class);
        existante.verifierLUnicite(List.of(existante));
        autre.verifierLUnicite(List.of());
    }

    @Test
    void corriger_une_caisse_remplace_tout_en_gardant_l_identite_et_le_centre_et_valide_la_saisie() {
        CaisseDAssurance c = caisse("CNAS", "Caisse", "VACANCIER");

        CaisseDAssurance corrigee = c.corriger("CNAS2", "Caisse renommee", null);

        assertThat(corrigee.id()).isEqualTo(ID);
        assertThat(corrigee.centre()).isEqualTo(CENTRE);
        assertThat(corrigee.type()).isEqualTo("STANDARD");
        assertThat(refusees(() -> c.corriger("", "x", null))).hasSize(1);
    }

    @Test
    void reconstituer_une_caisse_enregistree_ne_revalide_pas_les_donnees_historiques() {
        CaisseDAssurance c = CaisseDAssurance.reconstituer(ID, CENTRE, "X".repeat(80), "", null);

        assertThat(c.code()).hasSize(80);
        assertThat(c.type()).isNull();
    }

    // ---- agence

    private static CaisseDAssurance parente(UUID id, CentreId centre, String code) {
        return CaisseDAssurance.referencer(id, centre, code, "Caisse " + code, null);
    }

    @Test
    void une_agence_designe_sa_caisse_par_son_code_sans_casse_ni_espaces_repetes_ou_par_son_identifiant() {
        UUID cnas = UUID.randomUUID();
        List<CaisseDAssurance> caisses = List.of(parente(cnas, CENTRE, "CNAS"), parente(UUID.randomUUID(), CENTRE, "CASNOS"));

        assertThat(Agence.referencer(ID, CENTRE, "AG-1", "Agence 1", "  cnas ", caisses).caisse()).isEqualTo(cnas);
        assertThat(Agence.referencer(ID, CENTRE, "AG-1", "Agence 1", " " + cnas + " ", caisses).caisse()).isEqualTo(cnas);
    }

    @Test
    void une_agence_refuse_une_caisse_absente_inconnue_ou_d_un_autre_centre() {
        UUID etrangere = UUID.randomUUID();
        List<CaisseDAssurance> caisses = List.of(parente(UUID.randomUUID(), CENTRE, "CNAS"));
        List<CaisseDAssurance> toutes = List.of(caisses.getFirst(), parente(etrangere, AUTRE, "CNAS-B"));

        List<AnomalieDeSaisie> absente = refusees(() -> Agence.referencer(ID, CENTRE, "A", "A", null, caisses));
        assertThat(absente).extracting(AnomalieDeSaisie::champ, AnomalieDeSaisie::code, AnomalieDeSaisie::message)
                .containsExactly(tuple("caisse", "REQUIRED", "« Caisse (code) » est obligatoire."));

        List<AnomalieDeSaisie> inconnue = refusees(() -> Agence.referencer(ID, CENTRE, "A", "A", "NULLE-PART", caisses));
        assertThat(inconnue.getFirst().code()).isEqualTo("REFERENCE_NOT_FOUND");
        assertThat(inconnue.getFirst().message()).isEqualTo(
                "« Caisse (code) » : « NULLE-PART » est introuvable dans « Caisses » — créez-le d'abord (ou importez d'abord le fichier « Caisses »).");
        assertThat(inconnue.getFirst().parametres()).isEqualTo(Map.of("value", "NULLE-PART", "target", "caisses"));

        // la liste donnée est celle du centre : une caisse d'un autre centre n'y figure pas
        assertThat(refusees(() -> Agence.referencer(ID, CENTRE, "A", "A", etrangere.toString(), caisses)).getFirst().code())
                .isEqualTo("REFERENCE_NOT_FOUND");
        assertThat(toutes).hasSize(2);
    }

    @Test
    void les_anomalies_d_une_agence_suivent_l_ordre_code_nom_caisse() {
        List<AnomalieDeSaisie> anomalies = refusees(() -> Agence.referencer(ID, CENTRE, "", "", "NULLE-PART", List.of()));

        assertThat(anomalies).extracting(AnomalieDeSaisie::champ, AnomalieDeSaisie::code)
                .containsExactly(tuple("code", "REQUIRED"), tuple("nom", "REQUIRED"), tuple("caisse", "REFERENCE_NOT_FOUND"));
    }

    @Test
    void corriger_une_agence_peut_changer_de_caisse() {
        UUID cnas = UUID.randomUUID();
        UUID casnos = UUID.randomUUID();
        List<CaisseDAssurance> caisses = List.of(parente(cnas, CENTRE, "CNAS"), parente(casnos, CENTRE, "CASNOS"));
        Agence a = Agence.referencer(ID, CENTRE, "AG-1", "Agence 1", "CNAS", caisses);

        Agence corrigee = a.corriger("AG-1B", "Agence renommee", "CASNOS", caisses);

        assertThat(corrigee.caisse()).isEqualTo(casnos);
        assertThat(corrigee.id()).isEqualTo(ID);
        assertThat(corrigee.niveau()).isEqualTo(NiveauDePayeur.AGENCE);
    }

    // ---- centre payeur

    @Test
    void un_centre_payeur_designe_son_agence_garde_son_adresse_facultative_et_la_borne_a_255_caracteres() {
        UUID agenceId = UUID.randomUUID();
        List<Agence> agences = List.of(Agence.reconstituer(agenceId, CENTRE, "AG-1", "Agence 1", UUID.randomUUID()));

        CentrePayeur avec = CentrePayeur.referencer(ID, CENTRE, "CP-1", "Centre 1", " 12 rue Didouche Mourad ", "ag-1", agences);
        CentrePayeur sans = CentrePayeur.referencer(ID, CENTRE, "CP-2", "Centre 2", "  ", agenceId.toString(), agences);

        assertThat(avec.adresse()).isEqualTo("12 rue Didouche Mourad");
        assertThat(avec.agence()).isEqualTo(agenceId);
        assertThat(sans.adresse()).isNull();
        assertThat(sans.niveau()).isEqualTo(NiveauDePayeur.CENTRE_PAYEUR);
        assertThat(CentrePayeur.referencer(ID, CENTRE, "CP-3", "C", "R".repeat(255), "AG-1", agences).adresse()).hasSize(255);
        assertThat(refusees(() -> CentrePayeur.referencer(ID, CENTRE, "CP-3", "C", "R".repeat(256), "AG-1", agences)).getFirst().message())
                .isEqualTo("« Adresse » dépasse 255 caractères (256).");
    }

    @Test
    void les_anomalies_d_un_centre_payeur_suivent_l_ordre_code_nom_adresse_agence() {
        List<AnomalieDeSaisie> anomalies = refusees(
                () -> CentrePayeur.referencer(ID, CENTRE, null, null, "R".repeat(256), null, List.of()));

        assertThat(anomalies).extracting(AnomalieDeSaisie::champ, AnomalieDeSaisie::code)
                .containsExactly(tuple("code", "REQUIRED"), tuple("nom", "REQUIRED"), tuple("adresse", "TOO_LONG"),
                        tuple("agence", "REQUIRED"));
        assertThat(anomalies.getLast().message()).isEqualTo("« Agence (code) » est obligatoire.");
    }

    @Test
    void un_centre_payeur_refuse_une_caisse_donnee_a_la_place_de_l_agence() {
        UUID caisseId = UUID.randomUUID();

        List<AnomalieDeSaisie> anomalies = refusees(
                () -> CentrePayeur.referencer(ID, CENTRE, "CP", "C", null, caisseId.toString(), List.of()));

        assertThat(anomalies.getFirst().message()).contains("introuvable dans « Agences »");
        assertThat(anomalies.getFirst().parametres()).containsEntry("target", "agences");
    }

    @Test
    void les_niveaux_portent_le_libelle_pluriel_et_la_designation_de_l_administration() {
        assertThat(NiveauDePayeur.CAISSE.libelle()).isEqualTo("Caisses");
        assertThat(NiveauDePayeur.AGENCE.libelle()).isEqualTo("Agences");
        assertThat(NiveauDePayeur.CENTRE_PAYEUR.libelle()).isEqualTo("Centres payeurs");
        assertThat(NiveauDePayeur.CAISSE.designation()).isEqualTo("caisses");
        assertThat(NiveauDePayeur.AGENCE.designation()).isEqualTo("agences");
        assertThat(NiveauDePayeur.CENTRE_PAYEUR.designation()).isEqualTo("centres-payeurs");
    }
}
