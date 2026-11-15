package com.hemodialyse.backend.characterization.documents.doc5i;

import com.hemodialyse.backend.characterization.dossiermedical.dm2.PrescriptionsCharacterizationSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DOC5i : filet de rendu de la fiche signalétique du patient imprimée ({@code FICHE_PATIENT}), TOUT le texte du PDF, avant la conversion du
 * gabarit {@code fiche_patient.jrxml} de son SQL embarqué (patients jointe à centers par {@code LEFT JOIN}, filtre sur le patient ET le centre)
 * vers un jeu de données fourni par Patient et couverture (le nom du centre par l'annuaire d'Organisation). Par type et par modèle ; patient
 * riche, patient aux valeurs absentes ou vides, patient inconnu ou d'un autre centre, identifiants absents, vides ou illisibles de la voie par
 * modèle. DONNÉES FICTIVES ; date d'impression masquée.
 */
class FichePatient5iCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    private static final UUID MODELE_A = UUID.fromString("99997800-0000-0000-0000-0000000f5a91");
    private static final UUID MODELE_B = UUID.fromString("99997800-0000-0000-0000-0000000f5b91");

    @Override
    protected String dossier() {
        return "doc5i";
    }

    @BeforeEach
    void modeles() {
        retirerLesModeles();
        for (UUID[] m : new UUID[][]{{MODELE_A, CENTRE_A}, {MODELE_B, CENTRE_B}}) {
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                            + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", m[0], m[1], "FICHE_PATIENT_5I", "Document 5i",
                    "FICHE_PATIENT", "reports/fiche_patient.jrxml", "PDF", "Document fictif 5i", true,
                    OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        }
    }

    @AfterEach
    void retirerLesModeles() {
        jdbc.update("DELETE FROM modele_document WHERE id IN (?, ?)", MODELE_A, MODELE_B);
    }

    /** Un patient fictif ; {@code colonnes} : paires nom de colonne / valeur de l'état civil imprimé. */
    private UUID patient(UUID centre, String alias, Object... colonnes) {
        UUID id = idStable("patient5i", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("id", id);
        valeurs.put("center_id", centre);
        valeurs.put("code_patient", "P5I-" + alias);
        valeurs.put("nom", "Nom");
        valeurs.put("prenom", "Prenom");
        valeurs.put("sexe", "M");
        valeurs.put("numero_assurance", "ASS");
        valeurs.put("date_admission", java.sql.Date.valueOf(fixe(10)));
        valeurs.put("type_patient", "NON_VACANCIER");
        valeurs.put("created_at", CREATION_FIXE);
        for (int i = 0; i < colonnes.length; i += 2) valeurs.put((String) colonnes[i], colonnes[i + 1]);
        jdbc.update("INSERT INTO patients (" + String.join(", ", valeurs.keySet()) + ") VALUES ("
                + String.join(", ", valeurs.keySet().stream().map(k -> "?").toList()) + ")", valeurs.values().toArray());
        return id;
    }

    private static java.sql.Date jour(java.time.LocalDate d) {
        return d == null ? null : java.sql.Date.valueOf(d);
    }

    private MockHttpServletResponse parType(UUID centre, String params) throws Exception {
        return mvc.perform(post("/api/v1/documents/print").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"" + centre + "\",\"typeDocument\":\"FICHE_PATIENT\",\"params\":{" + params + "}}")
                .with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    private MockHttpServletResponse parModele(UUID modele, UUID centre, String params) throws Exception {
        return mvc.perform(post("/api/v1/documents/print/" + modele).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"" + centre + "\",\"params\":{" + params + "}}").with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    private static String param(String nom, Object valeur) {
        return "\"" + nom + "\":\"" + valeur + "\"";
    }

    private Map<String, Object> resume(MockHttpServletResponse r) throws Exception {
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

    @Test
    void la_fiche_riche_s_imprime_par_type_et_par_modele() throws Exception {
        UUID eloi = patient(CENTRE_A, "ELOI", "civilite", "Mme", "nom", "Éléonore-Dupré", "prenom", "Zoé-Émilie", "sexe", "F",
                "groupe_sanguin", "O+", "date_naissance", jour(fixe(-9000)), "lieu_naissance", "Béjaïa", "date_admission", jour(fixe(-400)),
                "nombre_enfants", 3, "situation_familiale", "Mariée", "profession1", "Institutrice", "adresse", "12 rue de l'Été, Alger",
                "tel_personnel", "021 00 00 01", "tel_mobile", "0550 00 00 01", "tel_bureau", "021 00 00 02", "email", "eleonore@exemple.test",
                "numero_assurance", "1 61 03 75 123 456 78", "etat_patient", "ACTIF", "qualite_assure", "Conjoint",
                "observation", "Allergie à l'iode ; à revoir.\nDeuxième ligne d'observation", "sous_kt", true, "assure_nom", "Dupré",
                "assure_prenom", "Émile", "code_patient", "ELOI-001");
        UUID kt = patient(CENTRE_A, "SANS_KT", "sous_kt", false, "nom", "Sans", "prenom", "Kt");

        Map<String, Object> cas = new LinkedHashMap<>();
        MockHttpServletResponse reponse = parType(CENTRE_A, param("patientId", eloi));
        assertEquals(200, reponse.getStatus());
        cas.put("par type, patient riche", resume(reponse));
        cas.put("par type, patient hors KT", resume(parType(CENTRE_A, param("patientId", kt))));
        cas.put("par modele, patient riche", resume(parModele(MODELE_A, CENTRE_A, param("patientId", eloi))));
        arbre("fiche_doc5i_riche_texte", cas);
    }

    @Test
    void les_valeurs_absentes_ou_vides_s_impriment_comme_avant() throws Exception {
        UUID nu = patient(CENTRE_A, "NU", "code_patient", null, "numero_assurance", "");
        UUID vide = patient(CENTRE_A, "VIDE", "nom", "", "prenom", "", "sexe", "", "civilite", "", "adresse", "", "observation", "",
                "sous_kt", null, "nombre_enfants", 0, "email", "");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type, patient aux valeurs absentes", resume(parType(CENTRE_A, param("patientId", nu))));
        cas.put("par type, patient aux valeurs vides", resume(parType(CENTRE_A, param("patientId", vide))));
        cas.put("par modele, patient aux valeurs absentes", resume(parModele(MODELE_A, CENTRE_A, param("patientId", nu))));
        arbre("fiche_doc5i_minimale_texte", cas);
    }

    @Test
    void les_cas_vides_et_les_identifiants_de_la_voie_par_modele() throws Exception {
        UUID patientA = patient(CENTRE_A, "PAT_A", "nom", "Alpha");
        UUID patientB = patient(CENTRE_B, "PAT_B", "nom", "Beta");

        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type, patient inconnu", resume(parType(CENTRE_A, param("patientId", idStable("patient5i", "INCONNU")))));
        cas.put("par type, patient du centre B demande pour le centre A", resume(parType(CENTRE_A, param("patientId", patientB))));
        cas.put("par type, aucun identifiant", resume(parType(CENTRE_A, "")));
        cas.put("par type, identifiant illisible", resume(parType(CENTRE_A, param("patientId", "pas-un-uuid"))));
        cas.put("par modele : aucun identifiant", resume(parModele(MODELE_A, CENTRE_A, "")));
        cas.put("par modele : identifiant vide", resume(parModele(MODELE_A, CENTRE_A, param("patientId", ""))));
        cas.put("par modele : identifiant illisible", resume(parModele(MODELE_A, CENTRE_A, param("patientId", "pas-un-uuid"))));
        cas.put("par modele : patient d'un autre centre", resume(parModele(MODELE_A, CENTRE_A, param("patientId", patientB))));
        cas.put("par modele : patient du centre", resume(parModele(MODELE_A, CENTRE_A, param("patientId", patientA))));
        cas.put("par modele : patient du centre B imprime au centre B", resume(parModele(MODELE_B, CENTRE_B, param("patientId", patientB))));
        arbre("fiche_doc5i_cas_vides_texte", cas);
    }
}
