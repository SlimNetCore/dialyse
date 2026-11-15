package com.hemodialyse.backend.characterization.documents.doc5d;

import com.hemodialyse.backend.characterization.dossiermedical.dm2.PrescriptionsCharacterizationSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
 * DOC5d : filet de rendu de l'ordonnance imprimée, TOUT le texte du PDF, avant la conversion du gabarit {@code ordonnance.jrxml} de son
 * SQL embarqué (quatre tables : {@code ordonnances} et {@code lignes_ordonnance} du Dossier médical, {@code patients} de Patient et
 * couverture, {@code centers} d'Organisation) vers un jeu de données fourni par le Dossier médical. Observé par
 * {@code POST /api/v1/documents/print} (par type) et {@code /print/{modeleId}} (par modèle) : une ordonnance riche (code affiché ou
 * libellé, lignes sans voie, durée ni quantité, instructions longues sur plusieurs lignes, accents, plusieurs pages), une ordonnance
 * minimale (patient sans naissance ni assurance, sans prescripteur ni numéro), puis les cas vides (ordonnance inconnue, d'un autre
 * centre, dont le patient est d'un autre centre) et les identifiants absents ou illisibles de la voie par modèle. Le texte doit rester
 * identique après la conversion. Date d'impression masquée. DONNÉES FICTIVES.
 */
class OrdonnanceImprimee5dCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    private static final UUID MODELE_A = UUID.fromString("99997800-0000-0000-0000-0000000d5a01");
    private static final UUID MODELE_B = UUID.fromString("99997800-0000-0000-0000-0000000d5b01");

    @Override
    protected String dossier() {
        return "doc5d";
    }

    @BeforeEach
    void modeles() {
        retirerLesModeles();
        for (UUID[] m : new UUID[][]{{MODELE_A, CENTRE_A}, {MODELE_B, CENTRE_B}}) {
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                            + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", m[0], m[1], "ORDONNANCE_5D", "Ordonnance",
                    "ORDONNANCE", "reports/ordonnance.jrxml", "PDF", "Ordonnance fictive 5d", true,
                    OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        }
    }

    @AfterEach
    void retirerLesModeles() {
        jdbc.update("DELETE FROM modele_document WHERE id IN (?, ?)", MODELE_A, MODELE_B);
    }

    private void ligne(UUID ordonnance, String alias, String codeAffiche, String libelle, String posologie, String voie, Integer duree,
                       Integer quantite, String instructions) {
        jdbc.update("INSERT INTO lignes_ordonnance (id, ordonnance_id, medicament_code_system, medicament_code, medicament_code_display, "
                        + "libelle, posologie, voie, duree_jours, quantite, instructions) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                idStable("ligne5d", alias), ordonnance, codeAffiche == null ? null : "ATC", codeAffiche == null ? null : "A" + alias.length(),
                codeAffiche, libelle, posologie, voie, duree, quantite, instructions);
    }

    private UUID ordonnanceVide(UUID centre, UUID patient, String alias, String medecin, String statut, String numero) {
        UUID id = idStable("ordonnance", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO ordonnances (id, patient_id, center_id, medecin_id, date_prescription, statut, numero, created_at, "
                        + "updated_at) VALUES (?,?,?,?,?,?,?,?,?)", id, patient, centre, medecin, java.sql.Date.valueOf(fixe(3)), statut,
                numero, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    private static String corps(UUID centre, UUID ordonnance) {
        return "{\"centerId\":\"" + centre + "\",\"typeDocument\":\"ORDONNANCE\",\"params\":{"
                + (ordonnance == null ? "" : "\"ordonnanceId\":\"" + ordonnance + "\"") + "}}";
    }

    private static String corpsParModele(UUID centre, String ordonnanceId) {
        return "{\"centerId\":\"" + centre + "\",\"params\":{" + (ordonnanceId == null ? "" : "\"ordonnanceId\":\"" + ordonnanceId + "\"")
                + "}}";
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
                lignes.replaceAll(l -> l.replaceAll("Imprimé le \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}", "Imprimé le <HORODATAGE_IMPRESSION>"));
                m.put("texte", lignes);
            }
        } else if (type != null && type.startsWith("text/")) {
            m.put("texte", new String(octets, StandardCharsets.UTF_8));
        } else {
            m.put("octets", octets.length > 0);
        }
        return m;
    }

    private MockHttpServletResponse parType(String json) throws Exception {
        return mvc.perform(post("/api/v1/documents/print").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json)
                .with(as(CENTRE_A, "MEDECIN"))).andReturn().getResponse();
    }

    private MockHttpServletResponse parModele(UUID modele, String json) throws Exception {
        return mvc.perform(post("/api/v1/documents/print/" + modele).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json).with(as(CENTRE_A, "MEDECIN"))).andReturn().getResponse();
    }

    @Test
    void l_ordonnance_riche_imprime_tout_le_texte_de_ses_lignes_sur_plusieurs_pages() throws Exception {
        UUID patient = patientDm2(CENTRE_A, "RICHE");
        jdbc.update("UPDATE patients SET date_naissance = ?, nom = ?, prenom = ? WHERE id = ?", java.sql.Date.valueOf(LocalDate.of(1961, 3, 7)),
                "Éléonore-Dupré", "Zoé", patient);
        UUID autrePatient = patientDm2(CENTRE_A, "AUTRE");
        UUID o = ordonnanceVide(CENTRE_A, patient, "RICHE", "dr-fictif-é", "SIGNEE", "ORD-00042");
        // lignes écrites dans le désordre (ni alphabétique ni par identifiant) : l'ordre imprimé est celui que la base rend
        ligne(o, "L01", null, "Zébra fictif", "2 fois par jour", "orale", 10, 20, "À prendre pendant les repas");
        ligne(o, "L02", "Libellé du code affiché", "Libellé ignoré car le code affiché prime", "1 comprimé le matin", null, null, null, null);
        ligne(o, "L03", null, "Alpha fictif", "1 sachet par semaine", "sous-cutanée", 90, null,
                "Instruction très longue qui dépasse largement la largeur de la page de cinq cent trente-cinq points et qui continue encore "
                        + "sur plusieurs lignes pour vérifier l'étirement de la ligne : surveiller la tension, éviter l'exposition au soleil, "
                        + "ne pas associer avec d'autres médicaments sans avis médical, conserver au réfrigérateur entre 2 et 8 degrés.");
        ligne(o, "L04", null, "Milieu fictif", "à la demande", "", 5, 1, "");
        ligne(o, "L05", "Code seul sans libellé", null, "3 gouttes", "ophtalmique", null, 2, null);
        ligne(o, "L06", null, null, "posologie sans médicament nommé", "orale", null, null, null);
        for (int i = 7; i <= 14; i++) {
            ligne(o, String.format("L%02d", i), null, "Médicament numéro " + (21 - i), "1 par jour", "orale", i, i, i % 2 == 0 ? "Note " + i : null);
        }
        // autres ordonnances à ne pas imprimer : même patient, autre patient du centre
        UUID autre = ordonnanceVide(CENTRE_A, patient, "AUTRE_MEME_PATIENT", "autre-medecin", "SIGNEE", "ORD-00043");
        ligne(autre, "X01", null, "A NE PAS IMPRIMER", "x", "orale", 1, 1, null);
        UUID autreDuCentre = ordonnanceVide(CENTRE_A, autrePatient, "AUTRE_PATIENT", "autre-medecin", "SIGNEE", "ORD-00044");
        ligne(autreDuCentre, "X02", null, "A NE PAS IMPRIMER NON PLUS", "x", "orale", 1, 1, null);

        Map<String, Object> cas = new LinkedHashMap<>();
        MockHttpServletResponse reponse = parType(corps(CENTRE_A, o));
        assertEquals(200, reponse.getStatus());
        cas.put("par type", resume(reponse));
        cas.put("par modele", resume(parModele(MODELE_A, corpsParModele(CENTRE_A, o.toString()))));
        arbre("ordonnance_doc5d_riche_texte", cas);
    }

    @Test
    void l_ordonnance_minimale_imprime_les_valeurs_absentes_comme_avant() throws Exception {
        UUID patient = patientDm2(CENTRE_A, "NU");
        jdbc.update("UPDATE patients SET numero_assurance = '', date_naissance = NULL WHERE id = ?", patient);
        UUID o = ordonnanceVide(CENTRE_A, patient, "MINIMALE", null, "BROUILLON", null);
        ligne(o, "M01", null, "Seul médicament", "1 par jour", null, null, null, null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type", resume(parType(corps(CENTRE_A, o))));
        arbre("ordonnance_doc5d_minimale_texte", cas);
    }

    @Test
    void les_cas_vides_et_les_identifiants_de_la_voie_par_modele() throws Exception {
        UUID patientA = patientDm2(CENTRE_A, "PAT_A");
        UUID patientB = patientDm2(CENTRE_B, "PAT_B");
        UUID sansLigne = ordonnanceVide(CENTRE_A, patientA, "SANS_LIGNE", "medecin-fictif", "SIGNEE", "ORD-00001");
        UUID duCentreB = ordonnanceVide(CENTRE_B, patientB, "CENTRE_B", "medecin-fictif", "SIGNEE", "ORD-00002");
        ligne(duCentreB, "B01", null, "Medicament du centre B", "1 par jour", "orale", 1, 1, null);
        // ordonnance du centre A dont le patient est d'un autre centre : la jointure sur le patient du centre l'écarte
        UUID patientEtranger = ordonnanceVide(CENTRE_A, patientB, "PATIENT_ETRANGER", "medecin-fictif", "SIGNEE", "ORD-00003");
        ligne(patientEtranger, "E01", null, "Medicament du patient etranger", "1 par jour", "orale", 1, 1, null);

        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("sans aucune ligne", resume(parType(corps(CENTRE_A, sansLigne))));
        cas.put("identifiant inconnu", resume(parType(corps(CENTRE_A, idStable("ordonnance", "INCONNUE")))));
        cas.put("ordonnance du centre B demandee pour le centre A", resume(parType(corps(CENTRE_A, duCentreB))));
        cas.put("patient d'un autre centre", resume(parType(corps(CENTRE_A, patientEtranger))));
        cas.put("par modele : identifiant absent", resume(parModele(MODELE_A, corpsParModele(CENTRE_A, null))));
        cas.put("par modele : identifiant illisible", resume(parModele(MODELE_A, corpsParModele(CENTRE_A, "pas-un-uuid"))));
        cas.put("par modele : identifiant vide", resume(parModele(MODELE_A, corpsParModele(CENTRE_A, ""))));
        cas.put("par modele : identifiant inconnu", resume(parModele(MODELE_A, corpsParModele(CENTRE_A, idStable("ordonnance", "INCONNUE").toString()))));
        arbre("ordonnance_doc5d_cas_vides_texte", cas);
    }
}
