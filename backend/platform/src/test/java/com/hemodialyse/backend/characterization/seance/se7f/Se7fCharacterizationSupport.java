package com.hemodialyse.backend.characterization.seance.se7f;

import com.hemodialyse.backend.characterization.seance.se7e.Se7eCharacterizationSupport;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la sous-slice SE7f (retrait de {@code bc-seance} : derniers chemins VIVANTS du module legacy et de ses adaptateurs de la
 * plateforme), au-dessus de SE7e (donc SE7d, SE7b, SE7a à SE1 et Planification).
 * <p>
 * Chemins observés : {@code PUT /api/v1/seances/{id}/volet-paramedical} ({@code VoletParamedicalRestController} → {@code VoletParamedicalDomainService}
 * → {@code SeanceRepositoryAdapter.findById}, {@code VoletParamedicalRepositoryAdapter}, puis {@code SeanceDomainService.getDetails}) ;
 * {@code GET /api/v1/seances/{id}} (volets relus par {@code VoletsFournisLegacy}, bornés au centre) ; {@code POST /api/v1/seances/scan}
 * (jours fériés et fermetures lus par {@code SeancePlanningJdbcAdapter} via {@code PlanningFourniLegacy}) ;
 * {@code GET /api/v1/patients/{id}/greffe/kdigo} (dernière créatinine lue par {@code ResultatAnalyseRepositoryAdapter}, bornée au centre).
 * <p>
 * Points d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, filtre d'écart de 2 ms, H2
 * {@code test}), état SQL des tables réelles {@code volet_paramedical}, {@code volet_medical}, {@code resultats_analyses}, notifications
 * durables. Aucun mock du code caractérisé, aucun seam, aucun code de production modifié.
 * <p>
 * Dates : FIXES ({@code <D+n>} = 2026-01-01 + n) sauf le scan, qui n'a pas de date : il prend le jour du centre ({@code <J>}, comme SE1),
 * seul jour posé en férié ; aucun test ne lit l'heure de la JVM. L'âge du patient de l'évaluation KDIGO est calculé par le code de
 * production depuis sa date de naissance : le golden ne fige que le STADE (stable sur des décennies pour les créatinines choisies), jamais
 * la valeur. DONNÉES FICTIVES uniquement.
 */
public abstract class Se7fCharacterizationSupport extends Se7eCharacterizationSupport {

    protected static final String SCAN = SEANCES_API + "/scan";
    protected static final String COMPLET = "\"poidsAvantKg\":70.5,\"poidsApresKg\":68.0,\"taAvant\":\"130/80\",\"taApres\":\"120/70\","
            + "\"dureeMinutes\":240,\"debitSangMlMin\":300,\"ultrafiltrationMl\":2000,\"anticoagulant\":\"HEPARINE\","
            + "\"typeDialysat\":\"STANDARD\",\"incidents\":\"RAS\"";

    @Override
    protected String tranche() {
        return "se7f";
    }

    @Override
    protected void nettoyerSe1() {
        // volets posés sous un autre centre que leur séance (cloisonnement) : retirés par séance avant la suppression des séances
        jdbc.update("DELETE FROM volet_paramedical WHERE seance_id IN (SELECT id FROM seances WHERE center_id IN (?, ?))", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM volet_medical WHERE seance_id IN (SELECT id FROM seances WHERE center_id IN (?, ?))", CENTRE_A, CENTRE_B);
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", centre);
        }
        super.nettoyerSe1();
    }

    // ------------------------------------------------------------------ fixtures

    /** Volet médical posé en base dans {@code centreDuVolet} (qui peut différer du centre de la séance). */
    protected void voletMedical(UUID centreDuVolet, UUID seance, String prescription) {
        jdbc.update("INSERT INTO volet_medical (id, center_id, seance_id, prescription, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                UUID.nameUUIDFromBytes(("se7f-volet-medical-" + seance).getBytes(StandardCharsets.UTF_8)), centreDuVolet, seance, prescription,
                CREATION_FIXE, CREATION_FIXE);
    }

    /** Résultat d'analyse (créatinine seule) du patient, dans le centre donné, à la date de prélèvement donnée. */
    protected void analyse(UUID centre, UUID patient, String alias, LocalDate prelevement, String creatinine) {
        UUID id = UUID.nameUUIDFromBytes(("se7f-analyse-" + alias).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, creatinine_mg_dl, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?)", id, patient, centre, Date.valueOf(prelevement),
                creatinine == null ? null : new BigDecimal(creatinine), CREATION_FIXE, CREATION_FIXE);
    }

    /** Fiche : homme né le 1er janvier 1950 (âge calculé par le code ; seul le stade est figé). */
    protected void naissance(UUID patient, LocalDate naissance) {
        jdbc.update("UPDATE patients SET date_naissance = ?, sexe = 'M' WHERE id = ?", Date.valueOf(naissance), patient);
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom)).ifPresent(org.springframework.cache.Cache::clear));
    }

    protected void jourFerie(UUID centre, LocalDate jour, String libelle) {
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?, ?, ?, ?)", UUID.randomUUID(), centre,
                Date.valueOf(jour), libelle);
    }

    // ------------------------------------------------------------------ appels

    protected Resp saisirLeVolet(UUID seance, UUID centreDuCorps, String champs, RequestPostProcessor who) throws Exception {
        return doPut(SEANCES_API + "/" + seance + "/volet-paramedical", "{\"centerId\":\"" + centreDuCorps + "\"" + (champs == null || champs.isEmpty()
                ? "" : "," + champs) + "}", who);
    }

    protected Resp scanner(String qr, String motif, String precision, RequestPostProcessor who) throws Exception {
        return doPost(SCAN, corps("centerId", CENTRE_A, "qrCode", qr, "motifHorsPlanning", motif, "precisionHorsPlanning", precision), who);
    }

    protected Resp kdigo(UUID patient, RequestPostProcessor who) throws Exception {
        return doGet(PATIENTS_API + "/" + patient + "/greffe/kdigo", who);
    }

    // ------------------------------------------------------------------ lecture des réponses et de la base

    /** Volets paramédicaux d'une séance, tous centres (le cloisonnement se lit dans la colonne). */
    protected List<Map<String, Object>> voletsParamedicaux(UUID seance) {
        return lignes("SELECT center_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, duree_minutes, debit_sang_ml_min, ultrafiltration_ml, "
                + "anticoagulant, type_dialysat, incidents FROM volet_paramedical WHERE seance_id = ? ORDER BY center_id", seance);
    }

    /** Notifications durables des deux centres : type, cibles, charge (sans identifiant volatil). */
    protected List<Map<String, Object>> notificationsLues() {
        return lignes("SELECT type, target_roles, payload FROM notification_evenement WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY type, payload");
    }

    /** Le stade de la fonction rénale et le risque (sans la valeur d'eGFR, qui dépend de l'âge donc du jour). */
    protected Map<String, Object> evaluationKdigo(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        if (r.status() != 200) {
            m.put("refus", refus(r));
            return m;
        }
        var c = corps(r);
        m.put("stade", c.path("fonctionRenale").path("stade").asString());
        m.put("egfrRenseigne", !c.path("fonctionRenale").path("egfrMlMin173m2").isNull());
        m.put("risque", c.path("risqueImmunologique").path("niveau").asString());
        return m;
    }
}
