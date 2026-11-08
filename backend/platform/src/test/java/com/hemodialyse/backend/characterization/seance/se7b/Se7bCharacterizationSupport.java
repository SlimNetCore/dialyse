package com.hemodialyse.backend.characterization.seance.se7b;

import com.hemodialyse.backend.characterization.seance.se7.Se7CharacterizationSupport;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Harnais de la sous-slice SE7b (tableau de bord mensuel des séances : {@code GET /api/v1/seances/dashboard}, {@code /dashboard/details},
 * {@code /dashboard/export}, servis par {@code SeanceRestController} en SQL direct sur {@code seances}, {@code patients},
 * {@code center_holiday}, {@code center_closure_day} et, via {@code AbsencesDuPilotage}, {@code absence_patient}), au-dessus de SE7a.
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}), exports décodés (CSV, XLSX via POI, PDF via PDFBox). Aucun mock du code caractérisé, aucun seam. Aucun test ne lit
 * l'heure : les mois lus sont des mois passés fixes (décembre 2025, janvier 2026, février 2026, février 2024) ou 2099, jamais le mois
 * courant ({@code absencesDuPilotage.aujourdhui()} borne les absences au jour du centre : un mois entièrement échu ne dépend pas de lui).
 * Les dates sont nommées {@code <D+n>} (n depuis le 1er janvier 2026). DONNEES FICTIVES uniquement.
 */
public abstract class Se7bCharacterizationSupport extends Se7CharacterizationSupport {

    protected static final LocalDate ANCIEN = LocalDate.of(2020, 1, 1);
    protected static final String TOUS_LES_JOURS = "dimanche,lundi,mardi,mercredi,jeudi,vendredi,samedi";

    @Override
    protected String tranche() {
        return "se7b";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", centre);
        }
        super.nettoyerSe1();
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Patient du centre, entièrement décrit (rien n'est laissé au défaut du harnais SE1) : {@code jours} = jours de dialyse séparés par des
     * virgules ({@code ""} = aucun) ; {@code sexe}, {@code naissance}, {@code admission}, {@code etat}, {@code evenement} peuvent être nuls.
     */
    protected UUID patientTb(UUID centre, String alias, String nom, String prenom, String sexe, LocalDate naissance, LocalDate admission,
                             String etat, LocalDate evenement, boolean sommeil, String jours) {
        UUID id = patientSe1(centre, alias);
        List<String> choisis = List.of(jours.split(","));
        jdbc.update("UPDATE patients SET nom = ?, prenom = ?, sexe = ?, date_naissance = ?, date_admission = ?, etat_patient = ?, "
                        + "date_evenement_etat = ?, en_sommeil = ?, jour_dimanche = ?, jour_lundi = ?, jour_mardi = ?, jour_mercredi = ?, "
                        + "jour_jeudi = ?, jour_vendredi = ?, jour_samedi = ? WHERE id = ?",
                nom, prenom, sexe, sql(naissance), sql(admission), etat, sql(evenement), sommeil,
                choisis.contains("dimanche"), choisis.contains("lundi"), choisis.contains("mardi"), choisis.contains("mercredi"),
                choisis.contains("jeudi"), choisis.contains("vendredi"), choisis.contains("samedi"), id);
        return id;
    }

    private static Date sql(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    /** Séance posée en base ; l'alias de la séance est dérivé du patient et du jour. */
    protected UUID seanceTb(UUID centre, UUID patient, LocalDate jour, String statut) {
        return seanceEnBase(centre, patient, "tb-" + patient.toString().substring(0, 8) + "-" + jour, jour, statut);
    }

    protected void absenceTb(UUID centre, UUID patient, LocalDate jour, String statut) {
        jdbc.update("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), centre, patient, Date.valueOf(jour), "AUTOMATIQUE", statut);
    }

    protected void ferieTb(UUID centre, LocalDate jour) {
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", UUID.randomUUID(), centre,
                Date.valueOf(jour), "Ferie fictif");
    }

    protected void fermetureTb(UUID centre, LocalDate jour) {
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?,?,?,?)", UUID.randomUUID(), centre,
                Date.valueOf(jour), "Fermeture fictive");
    }

    /**
     * Jeu nominal de janvier 2026 (jour de référence de l'âge : 31/01/2026) : 16 fiches du centre A couvrant sexes, tranches d'âge, états
     * de sortie et de séjour, admission en cours de mois, sommeil, plus quelques séances réalisées. Aucun jour non ouvré.
     */
    protected void jeuNominalDeJanvier() {
        UUID e01 = patientTb(CENTRE_A, "E01", "Nom E01", "Pre E01", "M", LocalDate.of(2008, 2, 1), ANCIEN, "PERMANENT", null, false, "lundi,mercredi,vendredi");
        UUID e02 = patientTb(CENTRE_A, "E02", "Nom E02", "Pre E02", "F", LocalDate.of(2008, 1, 31), ANCIEN, "PERMANENT", null, false, "mardi,jeudi");
        patientTb(CENTRE_A, "E03", "Nom E03", "Pre E03", "H", LocalDate.of(1986, 1, 31), ANCIEN, "PERMANENT", null, false, "samedi");
        patientTb(CENTRE_A, "E04", "Nom E04", "Pre E04", " f ", LocalDate.of(1966, 1, 31), ANCIEN, "PERMANENT", null, false, "dimanche");
        patientTb(CENTRE_A, "E05", "Nom E05", "Pre E05", "", null, ANCIEN, "PERMANENT", null, false, "");
        patientTb(CENTRE_A, "E06", "Nom E06", "Pre E06", "X", LocalDate.of(1967, 1, 31), ANCIEN, "PERMANENT", null, true, "lundi");
        UUID e07 = patientTb(CENTRE_A, "E07", "Nom E07", "Pre E07", "m", LocalDate.of(1990, 6, 15), ANCIEN, "PERMANENT", null, true, "");
        patientTb(CENTRE_A, "E08", "Nom E08", "Pre E08", "F", LocalDate.of(2000, 2, 29), LocalDate.of(2026, 1, 20), "PERMANENT", null, false, "lundi");
        patientTb(CENTRE_A, "E09", "Nom E09", "Pre E09", "F", LocalDate.of(1980, 5, 5), LocalDate.of(2026, 2, 5), "PERMANENT", null, false, "lundi");
        patientTb(CENTRE_A, "E10", "Nom E10", "Pre E10", "M", LocalDate.of(1950, 5, 5), ANCIEN, "TRANSFERE", LocalDate.of(2026, 1, 10), false, "jeudi");
        patientTb(CENTRE_A, "E11", "Nom E11", "Pre E11", "M", LocalDate.of(1950, 5, 6), ANCIEN, "DECEDE", LocalDate.of(2026, 1, 8), false, "jeudi");
        patientTb(CENTRE_A, "E12", "Nom E12", "Pre E12", "F", LocalDate.of(1950, 5, 7), ANCIEN, "GREFFE", LocalDate.of(2026, 1, 2), false, "jeudi,vendredi");
        patientTb(CENTRE_A, "E13", "Nom E13", "Pre E13", "F", LocalDate.of(1950, 5, 8), ANCIEN, "GUERRI", null, false, TOUS_LES_JOURS);
        patientTb(CENTRE_A, "E14", "Nom E14", "Pre E14", "M", LocalDate.of(1995, 5, 8), LocalDate.of(2026, 1, 1), "OCCASIONNEL", LocalDate.of(2026, 1, 4), false,
                "jeudi,vendredi,samedi,dimanche");
        patientTb(CENTRE_A, "E15", "Nom E15", "Pre E15", "F", LocalDate.of(1995, 5, 9), ANCIEN, "VACANCIER_ETRANGER", null, false, "vendredi");
        patientTb(CENTRE_A, "E16", "Nom E16", "Pre E16", "M", LocalDate.of(1995, 5, 10), ANCIEN, null, null, false, "");
        patientTb(CENTRE_B, "B01", "Nom B01", "Pre B01", "M", LocalDate.of(1970, 1, 1), ANCIEN, "PERMANENT", null, false, TOUS_LES_JOURS);
        seanceTb(CENTRE_A, e07, fixe(11), "VALIDEE");
        seanceTb(CENTRE_A, e01, fixe(4), "SIGNEE");
        seanceTb(CENTRE_A, e01, fixe(5), "FACTUREE");
        seanceTb(CENTRE_A, e02, fixe(6), "CREE");
        absenceTb(CENTRE_A, e01, fixe(7), "NON_JUSTIFIEE");
        absenceTb(CENTRE_A, e01, fixe(8), "ANNULEE");
    }

    // ------------------------------------------------------------------ appels

    protected static String requete(UUID centre, Object annee, Object mois) {
        return "?centerId=" + centre + (annee == null ? "" : "&year=" + annee) + (mois == null ? "" : "&month=" + mois);
    }

    protected Resp tableau(UUID centre, Object annee, Object mois, RequestPostProcessor who) throws Exception {
        return doGet(SEANCES_API + "/dashboard" + requete(centre, annee, mois), who);
    }

    protected Resp details(UUID centre, Object annee, Object mois, String kind, RequestPostProcessor who) throws Exception {
        return doGet(SEANCES_API + "/dashboard/details" + requete(centre, annee, mois) + (kind == null ? "" : "&kind=" + kind), who);
    }

    protected MockHttpServletResponse exporter(UUID centre, Object annee, Object mois, String format, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(java.net.URI.create(SEANCES_API + "/dashboard/export" + requete(centre, annee, mois)
                + (format == null ? "" : "&format=" + format))).with(who)).andReturn().getResponse();
    }

    protected Resp export(UUID centre, Object annee, Object mois, String format, RequestPostProcessor who) throws Exception {
        return doGet(SEANCES_API + "/dashboard/export" + requete(centre, annee, mois) + (format == null ? "" : "&format=" + format), who);
    }

    // ------------------------------------------------------------------ lecture des réponses

    /** Les compteurs d'un tableau de bord ; les répartitions sont lues à part. */
    protected Map<String, Object> resume(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        if (r.status() != 200) {
            return m;
        }
        JsonNode c = mapper.readTree(r.raw());
        for (String cle : List.of("year", "month", "expectedSeances", "presenceCount", "absenceCount", "totalSeances", "effectifPatients")) {
            m.put(cle, c.path(cle));
        }
        m.put("periodStart", c.path("absenceDetails").path("periodStart"));
        m.put("periodEnd", c.path("absenceDetails").path("periodEnd"));
        return m;
    }

    /** Une ligne de détail condensée : date, patient, jour de la semaine, statut, indicateurs. */
    protected List<String> lignesDeDetail(Resp r) {
        List<String> lignes = new ArrayList<>();
        for (JsonNode i : mapper.readTree(r.raw()).path("items")) {
            lignes.add(i.path("dateSeance").asString() + " | " + i.path("patientNom").asString() + " " + i.path("patientPrenom").asString()
                    + " | " + i.path("weekday").asString() + " | " + i.path("status").asString() + " | scheduled=" + i.path("scheduled")
                    + " present=" + i.path("present"));
        }
        return lignes;
    }

    protected Map<String, Object> enveloppe(MockHttpServletResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("contentType", r.getContentType());
        m.put("contentDisposition", r.getHeader("Content-Disposition"));
        m.put("cacheControl", r.getHeader("Cache-Control"));
        return m;
    }

    protected Map<String, Object> feuillesDuClasseur(byte[] octets) throws Exception {
        Map<String, Object> feuilles = new LinkedHashMap<>();
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(octets))) {
            DataFormatter fmt = new DataFormatter();
            for (Sheet feuille : classeur) {
                List<String> lignes = new ArrayList<>();
                for (Row ligne : feuille) {
                    lignes.add(fmt.formatCellValue(ligne.getCell(0)) + " | " + fmt.formatCellValue(ligne.getCell(1)));
                }
                feuilles.put(feuille.getSheetName(), lignes);
            }
        }
        return feuilles;
    }

    protected List<String> texteDuPdf(byte[] octets) throws Exception {
        List<String> lignes = new ArrayList<>();
        try (PDDocument pdf = PDDocument.load(octets)) {
            for (String ligne : new PDFTextStripper().getText(pdf).split("\\R")) {
                if (!ligne.isBlank()) lignes.add(ligne.strip());
            }
            lignes.add("pages=" + pdf.getNumberOfPages());
        }
        return lignes;
    }

    protected List<String> lignesDuTexte(String texte) {
        return List.of(texte.split("\n", -1));
    }
}
