package com.hemodialyse.backend.characterization.organisation;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.documents.infrastructure.rendu.ModeleDocumentProvisioner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filet de caractérisation O4 lot E : cas limites de l'identité imprimée (papier en-tête, ex {@code DocumentIdentityProvider}) et du provisionnement des
 * modèles de documents ({@code ModeleDocumentProvisioner}) sans filet jusqu'ici : société nue (le centre sans société est impossible : {@code centers.societe_id NOT NULL}), société inactive, logo vide,
 * pied de page vide ou blanc, textes à blancs de bord, ville identique à la wilaya, société inconnue, centre inactif provisionné,
 * modèle désactivé conservé. Écrit VERT sur les lecteurs SQL d'origine, avant leur remplacement par {@code IdentiteImprimee} et
 * {@code AnnuaireDesCentres} : mêmes assertions après. Aucune date, aucun aléa dans les assertions (identifiants aléatoires non observés).
 */
class IdentiteImprimeeCasLimitesCharacterizationIntegrationTest extends OrganisationCharacterizationSupport {

    @Autowired
    private PapierEnTeteDuCentre identite;
    @Autowired
    private ModeleDocumentProvisioner provisionneur;
    @Autowired
    private com.hemodialyse.backend.contexts.documents.application.CatalogueDesTypesLivres catalogue;

    @Override
    protected String theme() {
        return "o4e-identite-imprimee";
    }

    @AfterEach
    void retireLesModelesDeDocuments() {
        jdbc.update("DELETE FROM modele_document WHERE center_id IN (SELECT id FROM centers WHERE code LIKE ?)", CODE_PREFIXE + "%");
    }

    private UUID societeComplete(String code, boolean actif, byte[] logo, String piedPage) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, nif, nis, rc, adresse, ville, wilaya, telephone, email, site_web, "
                        + "pied_page, logo, actif, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, CODE_PREFIXE + code, "  Société " + code + "  ", "NIF-" + code, null, " ", " 1 rue A ", "Alger", "alger",
                "021 00", null, "https://s.dz", piedPage, logo, actif);
        return id;
    }

    private UUID centreComplet(String code, UUID societe, boolean actif) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, adresse, ville, wilaya, actif) VALUES (?,?,?,?,?,?,?,?)",
                id, CODE_PREFIXE + code, "Centre " + code, societe, null, "Blida", "Tipaza", actif);
        return id;
    }

    @Test
    void une_societe_sans_logo_ni_pied_de_page_ni_contact_donne_des_chaines_vides_et_pas_de_logo() throws Exception {
        UUID societe = UUID.randomUUID();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                societe, CODE_PREFIXE + "E-NUE", "Société nue");
        UUID centre = centreComplet("E-NU", societe, true);

        Map<String, Object> p = identite.parametresDeLEnTete(centre);

        assertThat(p.get("CENTRE_NOM")).isEqualTo("Centre E-NU");
        assertThat(p.get("CENTRE_ADRESSE")).isEqualTo("Blida – Tipaza");
        assertThat(p.get("CENTRE_LIGNE")).isEqualTo("Centre E-NU · Blida – Tipaza");
        assertThat(p.get("SOCIETE_NOM")).isEqualTo("Société nue");
        assertThat(p.get("SOCIETE_CONTACT")).isEqualTo("");
        assertThat(p.get("SOCIETE_LEGAL")).isEqualTo("");
        assertThat(p.get("SOCIETE_PIED_PAGE")).isEqualTo("");
        assertThat(p.get("SOCIETE_LOGO")).isNull();
        assertThat(identite.habiller("<html><body>X</body></html>", centre)).doesNotContain("<img");
    }

    @Test
    void une_societe_inactive_est_imprimee_comme_une_societe_active_avec_ses_textes_rognes() throws Exception {
        UUID societe = societeComplete("E-INACTIVE", false, new byte[]{(byte) 0xFF, 1, 2, 3, 4}, "Pied inactif");
        UUID centre = centreComplet("E-INACTIF", societe, false);

        Map<String, Object> p = identite.parametresDeLEnTete(centre);

        assertThat(p.get("SOCIETE_NOM")).isEqualTo("Société E-INACTIVE");
        assertThat(p.get("SOCIETE_ADRESSE")).isEqualTo("1 rue A, Alger");
        assertThat(p.get("SOCIETE_CONTACT")).isEqualTo("1 rue A, Alger · Tél : 021 00 · Web : https://s.dz");
        assertThat(p.get("SOCIETE_LEGAL")).isEqualTo("NIF : NIF-E-INACTIVE");
        assertThat(p.get("SOCIETE_PIED_PAGE")).isEqualTo("Pied inactif");
        assertThat(p.get("CENTRE_NOM")).isEqualTo("Centre E-INACTIF");
        assertThat(p.get("SOCIETE_LOGO")).isInstanceOf(InputStream.class);
        assertThat(identite.habiller("<html><body>X</body></html>", centre)).contains("data:image/jpeg;base64,");
        // Un flux neuf à chaque impression.
        assertThat(((InputStream) identite.parametresDeLEnTete(centre).get("SOCIETE_LOGO")).readAllBytes())
                .containsExactly((byte) 0xFF, 1, 2, 3, 4);
    }

    @Test
    void un_logo_vide_et_un_pied_de_page_blanc_donnent_pas_de_logo_et_un_pied_vide() throws Exception {
        UUID societe = societeComplete("E-VIDE", true, new byte[0], "   ");
        UUID centre = centreComplet("E-VIDE", societe, true);

        Map<String, Object> p = identite.parametresDeLEnTete(centre);

        assertThat(p.get("SOCIETE_LOGO")).isNull();
        assertThat(p.get("SOCIETE_PIED_PAGE")).isEqualTo("");
        assertThat(identite.habiller("<html><body>X</body></html>", centre)).doesNotContain("<img");
        assertThat(identite.enTeteDeLaSociete(societe)).hasValueSatisfying(s -> {
            assertThat(s.logo()).isNull();
            assertThat(s.piedDePage()).isEqualTo("");
        });
    }

    @Test
    void la_ville_identique_a_la_wilaya_sans_tenir_compte_de_la_casse_n_est_imprimee_qu_une_fois() throws Exception {
        UUID societe = societeComplete("E-VILLE", true, null, null);
        UUID centre = centreComplet("E-VILLE", societe, true);

        Map<String, Object> p = identite.parametresDeLEnTete(centre);

        assertThat(p.get("SOCIETE_ADRESSE")).isEqualTo("1 rue A, Alger");
        assertThat(p.get("CENTRE_ADRESSE")).isEqualTo("Blida – Tipaza");
    }

    @Test
    void l_identite_de_la_seule_societe_est_vide_si_la_societe_est_inconnue_ou_absente() throws Exception {
        UUID societe = societeComplete("E-SEULE", false, new byte[]{1, 2, 3, 4, 5}, "Pied seul");

        assertThat(identite.enTeteDeLaSociete(null)).isEmpty();
        assertThat(identite.enTeteDeLaSociete(UUID.randomUUID())).isEmpty();
        assertThat(identite.enTeteDeLaSociete(societe)).hasValueSatisfying(s -> {
            assertThat(s.nom()).isEqualTo("Société E-SEULE");
            assertThat(s.contact()).isEqualTo("1 rue A, Alger · Tél : 021 00 · Web : https://s.dz");
            assertThat(s.legal()).isEqualTo("NIF : NIF-E-SEULE");
            assertThat(s.piedDePage()).isEqualTo("Pied seul");
            assertThat(s.logo()).containsExactly(1, 2, 3, 4, 5);
        });
    }

    @Test
    void le_provisionnement_couvre_tous_les_centres_meme_inactifs() throws Exception {
        UUID societe = societeComplete("E-PROV", true, null, null);
        UUID inactif = centreComplet("E-PROV-I", societe, false);
        UUID actif = centreComplet("E-PROV-A", societe, true);

        provisionneur.run(null);

        int attendu = catalogue.types().size();
        for (UUID centre : List.of(inactif, actif)) {
            Integer nombre = jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ?", Integer.class, centre);
            assertThat(nombre).as("modèles du centre " + centre).isEqualTo(attendu);
        }
        provisionneur.run(null);
        Integer apres = jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ?", Integer.class, inactif);
        assertThat(apres).as("idempotent").isEqualTo(attendu);
    }

    @Test
    void un_modele_desactive_par_le_centre_reste_desactive_apres_le_provisionnement() throws Exception {
        UUID centre = centreComplet("E-PROV-D", societeComplete("E-PROVD", true, null, null), true);
        var entree = catalogue.types().get(0);
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                        + "description, active) VALUES (?,?,?,?,?,?,'PDF',?,FALSE)",
                UUID.randomUUID(), centre, entree.code(), "Libellé du centre", entree.code(), entree.gabaritParDefaut(), "d");

        provisionneur.run(null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ? AND UPPER(type_document) = ?",
                Integer.class, centre, entree.code())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT active FROM modele_document WHERE center_id = ? AND UPPER(type_document) = ?",
                Boolean.class, centre, entree.code())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM modele_document WHERE center_id = ?", Integer.class, centre))
                .isEqualTo(catalogue.types().size());
    }
}
