package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DM2, lot 1 : l'état d'impression de l'ordonnance ({@code POST /api/v1/documents/print}, type {@code ORDONNANCE}, gabarit
 * {@code reports/ordonnance.jrxml}). Le contenu du document est observé par l'export tableur (les chaînes du classeur) ; le PDF est
 * contrôlé par son en-tête. Le passage au statut IMPRIMEE ({@code PUT .../imprimer}) est indépendant de l'impression (aucun lien).
 */
class ImpressionDeLOrdonnanceCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    private static final UUID MODELE_A = UUID.fromString("99997800-0000-0000-0000-0000000d0a01");
    private static final UUID MODELE_B = UUID.fromString("99997800-0000-0000-0000-0000000d0b01");

    @Override
    protected String dossier() {
        return "impression";
    }

    @BeforeEach
    void modeles() {
        retirerLesModeles();
        for (UUID[] m : new UUID[][]{{MODELE_A, CENTRE_A}, {MODELE_B, CENTRE_B}}) {
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                            + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", m[0], m[1], "ORDONNANCE", "Ordonnance",
                    "ORDONNANCE", "reports/ordonnance.jrxml", "PDF", "Ordonnance fictive", true, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        }
    }

    @AfterEach
    void retirerLesModeles() {
        jdbc.update("DELETE FROM modele_document WHERE id IN (?, ?)", MODELE_A, MODELE_B);
    }

    private String corpsImpression(UUID centre, String format, String ordonnanceId) {
        return "{\"centerId\":\"" + centre + "\",\"typeDocument\":\"ORDONNANCE\"" + (format == null ? "" : ",\"formatOverride\":\"" + format + "\"")
                + ",\"params\":{" + (ordonnanceId == null ? "" : "\"ordonnanceId\":\"" + ordonnanceId + "\"") + "}}";
    }

    private MockHttpServletResponse imprimer(String corps, RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/api/v1/documents/print").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(corps).with(who))
                .andReturn().getResponse();
    }

    /** Chaînes du classeur (partagées et en ligne) : le texte imprimé, sans mise en forme. */
    private List<String> chainesDuClasseur(byte[] xlsx) throws Exception {
        List<String> chaines = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().equals("xl/sharedStrings.xml")) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("<t[^>]*>([^<]*)</t>").matcher(xml);
                    while (m.find()) {
                        // « Imprimé le jj/mm/aaaa hh:mm » : horodatage de l'impression, volatil, masqué.
                        chaines.add(m.group(1).replaceAll("^Imprimé le \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}$", "Imprimé le <HORODATAGE_IMPRESSION>"));
                    }
                }
            }
        }
        return chaines;
    }

    private Map<String, Object> resume(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        m.put("contentType", type);
        m.put("disposition", r.getHeader("Content-Disposition"));
        byte[] octets = r.getContentAsByteArray();
        if ("application/pdf".equals(type)) {
            m.put("entete", new String(octets, 0, Math.min(5, octets.length), StandardCharsets.US_ASCII));
        } else if (type != null && type.contains("spreadsheetml")) {
            m.put("chaines du classeur", chainesDuClasseur(octets));
        } else if (type != null && type.startsWith("text/")) {
            m.put("texte", new String(octets, StandardCharsets.UTF_8));
        }
        return m;
    }

    @Test
    void l_ordonnance_signee_s_imprime_en_pdf_et_son_contenu_vient_de_la_base() throws Exception {
        UUID patient = patientDm2(CENTRE_A, "PAT_A");
        UUID o = ordonnance(CENTRE_A, patient, "ORD_A", fixe(2), "SIGNEE", "ORD-00009");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("PDF (format du modele)", resume(imprimer(corpsImpression(CENTRE_A, null, o.toString()), as(CENTRE_A, "MEDECIN"))));
        cas.put("tableur (contenu imprime)", resume(imprimer(corpsImpression(CENTRE_A, "EXCEL", o.toString()), as(CENTRE_A, "MEDECIN"))));
        arbre("impression", cas);
    }

    @Test
    void l_etat_ne_controle_ni_le_statut_ni_le_lien_avec_l_impression_marquee() throws Exception {
        UUID patient = patientDm2(CENTRE_A, "PAT_A");
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String statut : List.of("BROUILLON", "ANNULEE", "IMPRIMEE")) {
            UUID o = ordonnance(CENTRE_A, patient, "ORD_" + statut, fixe(1), statut, "BROUILLON".equals(statut) ? null : "ORD-0001" + statut.length());
            cas.put(statut, resume(imprimer(corpsImpression(CENTRE_A, "EXCEL", o.toString()), as(CENTRE_A, "MEDECIN"))));
            assertThat(jdbc.queryForObject("SELECT statut FROM ordonnances WHERE id = ?", String.class, o)).isEqualTo(statut);
        }
        arbre("statuts", cas);
    }

    @Test
    void les_erreurs_de_l_impression_et_l_ordonnance_inconnue() throws Exception {
        UUID patient = patientDm2(CENTRE_A, "PAT_A");
        UUID o = ordonnance(CENTRE_A, patient, "ORD_A", fixe(2), "SIGNEE", "ORD-00009");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("ordonnanceId absent", resume(imprimer(corpsImpression(CENTRE_A, "EXCEL", null), as(CENTRE_A, "MEDECIN"))));
        cas.put("ordonnanceId inconnu", resume(imprimer(corpsImpression(CENTRE_A, "EXCEL", idStable("ordonnance", "INCONNUE").toString()),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("ordonnanceId illisible", resume(imprimer(corpsImpression(CENTRE_A, "EXCEL", "pas-un-uuid"), as(CENTRE_A, "MEDECIN"))));
        cas.put("centre absent", resume(imprimer("{\"typeDocument\":\"ORDONNANCE\",\"params\":{\"ordonnanceId\":\"" + o + "\"}}", as(CENTRE_A, "MEDECIN"))));
        jdbc.update("UPDATE modele_document SET active = FALSE WHERE id = ?", MODELE_A);
        cas.put("aucun modele actif", resume(imprimer(corpsImpression(CENTRE_A, "EXCEL", o.toString()), as(CENTRE_A, "MEDECIN"))));
        arbre("erreurs_nouveau_code", cas);
    }

    @Test
    void l_impression_n_est_pas_gardee_par_profil_ni_par_centre() throws Exception {
        UUID patientA = patientDm2(CENTRE_A, "PAT_A");
        UUID patientB = patientDm2(CENTRE_B, "PAT_B");
        UUID oa = ordonnance(CENTRE_A, patientA, "ORD_A", fixe(2), "SIGNEE", "ORD-00009");
        UUID ob = ordonnance(CENTRE_B, patientB, "ORD_B", fixe(3), "SIGNEE", "ORD-00004");
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm2().entrySet()) {
            MockHttpServletResponse r = imprimer(corpsImpression(CENTRE_A, "EXCEL", oa.toString()), p.getValue());
            profils.put(p.getKey(), r.getStatus() == 200 ? "200 (" + chainesDuClasseur(r.getContentAsByteArray()).contains("N° ORD-00009") + ")"
                    : r.getStatus() + " " + r.getContentAsString(StandardCharsets.UTF_8));
        }
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("profils (ordonnance du centre A, centre A dans le corps)", profils);
        cas.put("session A, centre B dans le corps, ordonnance du centre B", resume(imprimer(corpsImpression(CENTRE_B, "EXCEL", ob.toString()),
                as(CENTRE_A, "MEDECIN"))));
        cas.put("session A, centre A dans le corps, ordonnance du centre B", resume(imprimer(corpsImpression(CENTRE_A, "EXCEL", ob.toString()),
                as(CENTRE_A, "MEDECIN"))));
        arbre("garde", cas);
    }
}
