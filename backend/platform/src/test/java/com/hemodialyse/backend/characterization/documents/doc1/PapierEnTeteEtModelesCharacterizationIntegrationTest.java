package com.hemodialyse.backend.characterization.documents.doc1;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * DOC1 : le papier en-tete (ex {@code DocumentIdentityProvider.paramsFor / decorateHtml / societeIdentity / stripReservedParams}, desormais
 * {@code documents.v1.PapierEnTeteDuCentre.parametresDeLEnTete / habiller / enTeteDeLaSociete / ecarterLIdentiteDeLAppelant}) et
 * l'impression par type de document ({@code ModeleDocumentPrinter.print}, la porte utilisee par les contextes consommateurs :
 * provisionnement a la premiere impression, refus de modele desactive, type inconnu).
 */
class PapierEnTeteEtModelesCharacterizationIntegrationTest extends Doc1Support {

    private static final String HTML = "<html><head></head><body><h1>Doc</h1><p>Contenu</p></body></html>";

    @Autowired
    private PapierEnTeteDuCentre identite;
    @Autowired
    private MoteurDeRendu imprimeur;

    private Map<String, Object> parametres(UUID centre) throws Exception {
        Map<String, Object> p = new TreeMap<>(identite.parametresDeLEnTete(centre));
        Map<String, Object> vue = new LinkedHashMap<>();
        for (var e : p.entrySet()) {
            if (e.getValue() instanceof InputStream in) {
                vue.put(e.getKey(), "<flux de " + in.readAllBytes().length + " octets>");
            } else {
                vue.put(e.getKey(), e.getValue());
            }
        }
        return vue;
    }

    @Test
    void les_parametres_d_identite_sont_composes_depuis_la_societe_et_le_centre() throws Exception {
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("centre D (societe, logo, adresse, contact)", parametres(CENTRE_D));
        cas.put("centre E (sans societe ni contact)", parametres(CENTRE_E));
        cas.put("centre inconnu", parametres(UUID.fromString("99998150-0000-0000-0000-0000000009aa")));
        cas.put("centre null", parametres(null));
        golden("parametres_d_identite", cas);
    }

    @Test
    void la_ville_identique_a_la_wilaya_n_est_pas_repetee_et_les_champs_vides_sont_omis() throws Exception {
        jdbc.update("UPDATE centers SET ville = 'Blida', wilaya = 'blida', telephone = NULL, email = '', site_web = 'https://c.x.dz' WHERE id = ?", CENTRE_D);
        jdbc.update("UPDATE societes SET nif = NULL, nis = '', rc = 'RC-9', pied_page = NULL WHERE id = ?", SOCIETE);
        Map<String, Object> p = parametres(CENTRE_D);
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String k : List.of("CENTRE_ADRESSE", "CENTRE_CONTACT", "CENTRE_LIGNE", "SOCIETE_LEGAL", "SOCIETE_PIED_PAGE", "SOCIETE_CONTACT")) {
            cas.put(k, p.get(k));
        }
        golden("champs_vides_et_ville_egale_wilaya", cas);
    }

    @Test
    void les_parametres_reserves_du_client_sont_retires_quelle_que_soit_la_casse() {
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("retires", identite.ecarterLIdentiteDeLAppelant(new LinkedHashMap<String, Object>(Map.of(
                "patientId", "p1", "SOCIETE_NOM", "x", "centre_nom", "y", "Centre_Ligne", "z", "societe_logo", "w",
                "CENTERID", "garde", "MON_CENTRE", "garde"))).entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue()).toList());
        golden("parametres_reserves", cas);
    }

    @Test
    void le_html_recoit_un_en_tete_et_un_pied_de_page_echappes_avec_le_logo_en_data_uri() {
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("centre D", identite.habiller(HTML, CENTRE_D));
        cas.put("centre E (sans societe)", identite.habiller(HTML, CENTRE_E));
        cas.put("centre inconnu", identite.habiller(HTML, UUID.fromString("99998150-0000-0000-0000-0000000009aa")));
        cas.put("centre null", identite.habiller(HTML, null));
        golden("decorate_html", cas);
    }

    @Test
    void le_html_sans_balise_body_est_rendu_tel_quel_et_les_noms_sont_echappes() {
        jdbc.update("UPDATE societes SET raison_sociale = ?, pied_page = ? WHERE id = ?", "<b>Soc</b> & \"Cie\" 'x'", "<script>1</script>", SOCIETE);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("sans body", identite.habiller("<html><p>x</p></html>", CENTRE_D));
        cas.put("body avec attribut (non reconnu)", identite.habiller("<html><body class='a'>x</body></html>", CENTRE_D));
        cas.put("noms echappes", identite.habiller(HTML, CENTRE_D));
        golden("decorate_html_cas_limites", cas);
    }

    @Test
    void le_logo_jpeg_est_reconnu_par_son_premier_octet_et_tout_autre_contenu_est_traite_comme_png() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
        jdbc.update("UPDATE societes SET logo = ?, logo_content_type = 'image/jpeg' WHERE id = ?", jpeg, SOCIETE);
        String html = identite.habiller(HTML, CENTRE_D);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("jpeg (0xFF en tete)", html.contains("data:image/jpeg;base64,/9j/4AAQ"));
        byte[] autre = {1, 2, 3, 4, 5};
        jdbc.update("UPDATE societes SET logo = ?, logo_content_type = 'image/webp' WHERE id = ?", autre, SOCIETE);
        cas.put("autre contenu annonce webp : servi comme png", identite.habiller(HTML, CENTRE_D).contains("data:image/png;base64,AQIDBAU="));
        jdbc.update("UPDATE societes SET logo = NULL WHERE id = ?", SOCIETE);
        cas.put("sans logo : aucune balise img", !identite.habiller(HTML, CENTRE_D).contains("<img"));
        golden("logo_type_detecte", cas);
    }

    @Test
    void l_identite_de_la_seule_societe_sert_aux_documents_de_direction() {
        Map<String, Object> cas = new LinkedHashMap<>();
        var s = identite.enTeteDeLaSociete(SOCIETE).orElseThrow();
        Map<String, Object> vue = new LinkedHashMap<>();
        vue.put("nom", s.nom());
        vue.put("contact", s.contact());
        vue.put("legal", s.legal());
        vue.put("piedPage", s.piedDePage());
        vue.put("logo (octets)", s.logo() == null ? null : s.logo().length);
        cas.put("societe connue", vue);
        cas.put("societe inconnue", identite.enTeteDeLaSociete(UUID.fromString("99998150-0000-0000-0000-0000000009aa")).isPresent());
        cas.put("societe null", identite.enTeteDeLaSociete(null).isPresent());
        golden("identite_de_la_societe", cas);
    }

    @Test
    void imprimer_par_type_provisionne_le_modele_du_centre_a_la_premiere_impression() throws Exception {
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("modeles avant", modelesDuCentre(CENTRE_D));
        MoteurDeRendu.Rendu doc = imprimeur.rendreParType(CENTRE_D, "LISTE_PATIENTS", Map.of(), null);
        cas.put("format du document", doc.format());
        cas.put("document", pdf(doc.octets()));
        cas.put("modeles apres", modelesDuCentre(CENTRE_D));
        MoteurDeRendu.Rendu encore = imprimeur.rendreParType(CENTRE_D, "liste_patients", Map.of(), null);
        cas.put("seconde impression (casse differente) : modeles", modelesDuCentre(CENTRE_D).size());
        cas.put("seconde impression : meme texte", pdf(encore.octets()).equals(pdf(doc.octets())));
        golden("provisionnement", cas);
    }

    @Test
    void imprimer_par_type_utilise_le_format_du_modele_et_ecarte_l_identite_du_client() throws Exception {
        modele(CENTRE_D, "LISTE_PATIENTS", "reports/liste_patients.jrxml", "EXCEL", true);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("SOCIETE_NOM", "Usurpation SA");
        params.put("centre_ligne", "Faux");
        params.put("CENTER_ID", CENTRE_E.toString());
        MoteurDeRendu.Rendu doc = imprimeur.rendreParType(CENTRE_D, "LISTE_PATIENTS", params, null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("format", doc.format());
        cas.put("tableur", xlsx(doc.octets()));
        golden("format_du_modele", cas);
    }

    @Test
    void imprimer_par_type_refuse_un_type_inconnu_et_un_modele_desactive_avec_un_code_metier() {
        modele(CENTRE_D, "LISTE_PATIENTS", "reports/liste_patients.jrxml", "PDF", false);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("type inconnu", erreur("INCONNU", CENTRE_D));
        cas.put("type null", erreurNull());
        cas.put("modele desactive", erreur("LISTE_PATIENTS", CENTRE_D));
        cas.put("modele non provisionne mais desactive reste desactive (modeles)", modelesDuCentre(CENTRE_D));
        golden("refus_metier", cas);
    }

    @Test
    void imprimer_par_type_enveloppe_l_echec_de_generation_dans_un_code_metier() {
        modele(CENTRE_D, "LISTE_PATIENTS", "reports/inexistant.jrxml");
        Map<String, Object> cas = new LinkedHashMap<>();
        RenduRefuse e = null;
        try {
            imprimeur.rendreParType(CENTRE_D, "LISTE_PATIENTS", Map.of(), null);
        } catch (RenduRefuse ex) {
            e = ex;
        } catch (Exception ex) {
            cas.put("exception inattendue", ex.getClass().getSimpleName());
        }
        cas.put("code", e == null ? null : e.code());
        cas.put("debut du message", e == null ? null : e.getMessage().substring(0, Math.min(55, e.getMessage().length())));
        golden("echec_de_generation", cas);
    }

    private Map<String, Object> erreur(String type, UUID centre) {
        try {
            imprimeur.rendreParType(centre, type, Map.of(), null);
            return Map.of("resultat", "document rendu");
        } catch (RenduRefuse e) {
            return Map.of("code", e.code(), "message", e.getMessage());
        } catch (Exception e) {
            return Map.of("exception", e.getClass().getSimpleName());
        }
    }

    private Map<String, Object> erreurNull() {
        try {
            imprimeur.rendreParType(CENTRE_D, null, Map.of(), null);
            return Map.of("resultat", "document rendu");
        } catch (RenduRefuse e) {
            return Map.of("code", e.code(), "message", e.getMessage());
        } catch (Exception e) {
            return Map.of("exception", e.getClass().getSimpleName());
        }
    }

    private List<String> modelesDuCentre(UUID centre) {
        return jdbc.queryForList("SELECT code, libelle, type_document, chemin_jrxml, format_impression, active FROM modele_document "
                        + "WHERE center_id = ? ORDER BY code", centre).stream()
                .map(m -> m.get("CODE") + "|" + m.get("LIBELLE") + "|" + m.get("TYPE_DOCUMENT") + "|" + m.get("CHEMIN_JRXML") + "|"
                        + m.get("FORMAT_IMPRESSION") + "|" + m.get("ACTIVE")).toList();
    }
}
