package com.hemodialyse.backend.characterization.documents.doc5h;

import com.hemodialyse.backend.characterization.dossiermedical.dm2.PrescriptionsCharacterizationSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DOC5h : socle commun des filets de rendu de l'attestation d'ouverture de droit et de la prise en charge imprimées (TOUT le texte du PDF),
 * observés par {@code POST /api/v1/documents/print} (par type) et {@code /print/{modeleId}} (par modèle). DONNÉES FICTIVES ; date
 * d'impression masquée.
 */
abstract class DocumentDuPatient5hSupport extends PrescriptionsCharacterizationSupport {

    protected static final UUID MODELE_A = UUID.fromString("99997800-0000-0000-0000-0000000f5a81");
    protected static final UUID MODELE_B = UUID.fromString("99997800-0000-0000-0000-0000000f5b81");

    @Override
    protected String dossier() {
        return "doc5h";
    }

    /** Le type de document imprimé (par exemple {@code PEC}). */
    protected abstract String type();

    /** Le gabarit livré (par exemple {@code reports/prise_en_charge.jrxml}). */
    protected abstract String gabarit();

    @BeforeEach
    void modeles() {
        retirerLesModeles();
        for (UUID[] m : new UUID[][]{{MODELE_A, CENTRE_A}, {MODELE_B, CENTRE_B}}) {
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                            + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", m[0], m[1], type() + "_5H", "Document 5h",
                    type(), gabarit(), "PDF", "Document fictif 5h", true, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        }
    }

    @BeforeEach
    void viderLaCouverture() {
        retirerLaCouverture();
    }

    @AfterEach
    void retirerLesModeles() {
        jdbc.update("DELETE FROM modele_document WHERE id IN (?, ?)", MODELE_A, MODELE_B);
    }

    @AfterEach
    void retirerLaCouverture() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
        }
    }

    /** Un patient fictif complet ; les colonnes d'état civil absentes restent nulles (passer {@code null}). */
    protected UUID patient(UUID centre, String alias, String nom, String prenom, String sexe, java.time.LocalDate naissance,
                           String assurance, String adresse, String telephone) {
        UUID id = idStable("patient5h", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, "
                        + "numero_assurance, adresse, tel_mobile, type_patient, etat_patient, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, centre, "P5H-" + alias, nom, prenom, sexe, naissance == null ? null : java.sql.Date.valueOf(naissance),
                java.sql.Date.valueOf(fixe(10)), assurance, adresse, telephone, "NON_VACANCIER", "ACTIF", CREATION_FIXE);
        return id;
    }

    /** Corps de la voie par type ; {@code params} : paires nom/valeur JSON déjà échappées ({@code "patientId":"…"}). */
    protected String corps(UUID centre, String params) {
        return "{\"centerId\":\"" + centre + "\",\"typeDocument\":\"" + type() + "\",\"params\":{" + params + "}}";
    }

    protected MockHttpServletResponse parType(UUID centre, String params) throws Exception {
        return mvc.perform(post("/api/v1/documents/print").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(corps(centre, params)).with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    protected MockHttpServletResponse parModele(UUID modele, UUID centre, String params) throws Exception {
        return mvc.perform(post("/api/v1/documents/print/" + modele).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"" + centre + "\",\"params\":{" + params + "}}").with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    protected static String param(String nom, Object valeur) {
        return "\"" + nom + "\":\"" + valeur + "\"";
    }

    protected Map<String, Object> resume(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        m.put("contentType", type);
        byte[] octets = r.getContentAsByteArray();
        if ("application/pdf".equals(type)) {
            try (PDDocument doc = PDDocument.load(octets)) {
                m.put("pages", doc.getNumberOfPages());
                List<String> lignes = new ArrayList<>(new PDFTextStripper().getText(doc).replace("\r\n", "\n").lines().toList());
                lignes.replaceAll(l -> joursEnFrancais(l.replaceAll("Imprimé le \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}", "Imprimé le <HORODATAGE_IMPRESSION>")));
                m.put("texte", lignes);
            }
        } else if (type != null && type.startsWith("text/")) {
            m.put("texte", new String(octets, StandardCharsets.UTF_8));
        } else {
            m.put("octets", octets.length > 0);
        }
        return m;
    }
}
