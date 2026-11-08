package com.hemodialyse.backend.characterization.seance.se7;

import com.hemodialyse.backend.characterization.seance.se6.Se6CharacterizationSupport;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la sous-slice SE7a (routes HTTP résiduelles de Séance, servies par la plateforme et non par {@code contexts/seance} :
 * {@code GET /api/v1/seances/journal}, {@code GET|POST|DELETE /api/v1/seances/calendar/...}, {@code PUT /api/v1/seances/{id}/volet-medical},
 * {@code GET /api/v1/seances/patient/{id}/recentes}), au-dessus du harnais SE6 (donc SE5 à SE1).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}) et état SQL des tables réelles ({@code seances}, {@code center_holiday}, {@code center_closure_day},
 * {@code volet_medical}, {@code volet_paramedical}). Aucun mock du code caractérisé, aucun seam. Aucun test ne lit l'heure : les
 * séances relatives sont posées au jour du centre ({@code <J>}), les autres à des dates fixes. DONNEES FICTIVES uniquement.
 */
public abstract class Se7CharacterizationSupport extends Se6CharacterizationSupport {

    @Override
    protected String tranche() {
        return "se7";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM center_holiday WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM bons_sortie WHERE center_id = ?", centre);
        }
        super.nettoyerSe1();
    }

    /** Appel sans session (ni jeton) : la sécurité réelle répond. */
    protected Resp doGetAnonyme(String path) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(java.net.URI.create(path)));
    }

    protected Resp doSansSession(String methode, String path, String json) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(methode), java.net.URI.create(path))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json == null ? "" : json));
    }

    protected void bonDeSortie(UUID centre, UUID seance, UUID patient, java.time.LocalDate jour, UUID article, UUID lot, String quantite) {
        UUID bon = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_sortie (id, center_id, reference, seance_id, patient_id, date_sortie, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'test', ?)", bon, centre, "BS-" + bon, seance, patient, java.sql.Date.valueOf(jour), CREATION_FIXE);
        jdbc.update("INSERT INTO bons_sortie_lignes (id, bon_sortie_id, article_id, lot_id, quantite, pmp_applique) VALUES (?, ?, ?, ?, ?, 10)",
                UUID.randomUUID(), bon, article, lot, new BigDecimal(quantite));
    }

    protected Resp journal(UUID centre, Object date, RequestPostProcessor who) throws Exception {
        return doGet(SEANCES_API + "/journal?centerId=" + centre + (date == null ? "" : "&dateSeance=" + date), who);
    }

    protected Resp calendrier(UUID centre, String annee, String mois, RequestPostProcessor who) throws Exception {
        return doGet(SEANCES_API + "/calendar?centerId=" + centre + (annee == null ? "" : "&year=" + annee)
                + (mois == null ? "" : "&month=" + mois), who);
    }

    protected Resp recentes(UUID patient, String requete, RequestPostProcessor who) throws Exception {
        return doGet(SEANCES_API + "/patient/" + patient + "/recentes" + (requete == null ? "" : "?" + requete), who);
    }

    protected Resp voletMedical(UUID seance, String json, RequestPostProcessor who) throws Exception {
        return doPut(SEANCES_API + "/" + seance + "/volet-medical", json, who);
    }

    protected Resp ajouterJour(String type, UUID centre, Object date, String libelle, RequestPostProcessor who) throws Exception {
        return doPost(SEANCES_API + "/calendar/" + type, corps("centerId", centre, "dayDate", date, "labelOrReason", libelle), who);
    }

    protected Resp supprimerJour(String type, Object id, UUID centre, RequestPostProcessor who) throws Exception {
        return doDelete(SEANCES_API + "/calendar/" + type + "/" + id + (centre == null ? "" : "?centerId=" + centre), who);
    }

    /** Volet paramédical complet, pour les séances récentes. */
    protected void voletParamedicalComplet(UUID centre, UUID seance, String poidsAvant, String poidsApres, String taAvant, String taApres,
                                           int duree, String uf) {
        jdbc.update("INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, "
                        + "duree_minutes, ultrafiltration_ml, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre, seance, new BigDecimal(poidsAvant), new BigDecimal(poidsApres), taAvant, taApres, duree,
                new BigDecimal(uf), CREATION_FIXE, CREATION_FIXE);
    }

    protected List<Map<String, Object>> joursFeries(UUID centre) {
        return lignes("SELECT day_date, label FROM center_holiday WHERE center_id = ? ORDER BY day_date, label", centre);
    }

    protected List<Map<String, Object>> joursDeFermeture(UUID centre) {
        return lignes("SELECT day_date, reason FROM center_closure_day WHERE center_id = ? ORDER BY day_date, reason", centre);
    }

    /** Le volet médical d'une séance, horodatages masqués. */
    protected List<Map<String, Object>> voletsMedicaux(UUID seance) {
        return lignes("SELECT center_id, prescription, tolerance_seance, examen_clinique, resultats_biologiques, ajustements_therapeutiques, "
                + "conclusion_medicale, (CASE WHEN created_at IS NULL THEN 'null' ELSE 'renseigne' END) AS created_at, "
                + "(CASE WHEN updated_at IS NULL THEN 'null' ELSE 'renseigne' END) AS updated_at FROM volet_medical WHERE seance_id = ?", seance);
    }

}
