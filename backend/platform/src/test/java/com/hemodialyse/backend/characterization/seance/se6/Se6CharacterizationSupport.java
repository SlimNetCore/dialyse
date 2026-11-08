package com.hemodialyse.backend.characterization.seance.se6;

import com.hemodialyse.backend.characterization.seance.se5.Se5CharacterizationSupport;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice SE6 (modification de la date et suppression d'une séance : {@code PUT /api/v1/seances/{id}} et
 * {@code DELETE /api/v1/seances/{id}?centerId=}), au-dessus du harnais SE5 (donc SE4 à SE1).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}), état SQL des tables réelles ({@code seances}, {@code seance_suppression}, volets, administrations, consommations et
 * stock), diffusions, notifications, audit. Aucun mock du code caractérisé, aucun seam. Les séances relatives sont posées au jour du
 * centre ({@code <J>}) ; aucun test ne lit l'heure.
 * <p>
 * DONNEES FICTIVES uniquement.
 */
public abstract class Se6CharacterizationSupport extends Se5CharacterizationSupport {

    @Override
    protected String tranche() {
        return "se6";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM seance_suppression WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM volet_medical WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM administrations_anemie WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", centre);
        }
        super.nettoyerSe1();
    }

    // ------------------------------------------------------------------ chemins et corps

    protected String suppressionApi(UUID seance, UUID centre) {
        return SEANCES_API + "/" + seance + (centre == null ? "" : "?centerId=" + centre);
    }

    protected Resp supprimer(UUID seance, String motif, RequestPostProcessor who) throws Exception {
        return doDeleteAvecCorps(suppressionApi(seance, CENTRE_A), corps("motif", motif), who);
    }

    protected Resp changerLaDate(UUID seance, LocalDate date, RequestPostProcessor who) throws Exception {
        return doPut(SEANCES_API + "/" + seance, corps("centerId", CENTRE_A, "dateSeance", date), who);
    }

    // ------------------------------------------------------------------ fixtures

    protected void voletParamedical(UUID centre, UUID seance) {
        jdbc.update("INSERT INTO volet_paramedical (id, seance_id, center_id, poids_avant_kg) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), seance, centre, new BigDecimal("72.5"));
    }

    protected void voletMedical(UUID centre, UUID seance) {
        jdbc.update("INSERT INTO volet_medical (id, seance_id, center_id, prescription) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), seance, centre, "Hemodialyse 4h");
    }

    protected void administration(UUID centre, UUID patient, UUID seance, LocalDate jour) {
        jdbc.update("INSERT INTO administrations_anemie (id, patient_id, center_id, type_traitement, date_administration, seance_id, "
                        + "administree, created_at) VALUES (?, ?, ?, 'EPO', ?, ?, TRUE, ?)", UUID.randomUUID(), patient, centre,
                java.sql.Date.valueOf(jour), seance, CREATION_FIXE);
    }

    /** Une séance complète : volets, administration et deux consommables imputés (avec leurs lots). */
    protected UUID seanceComplete(UUID centre, UUID patient, String alias, LocalDate jour, String statut) throws Exception {
        UUID seance = seanceValidee(centre, patient, alias, jour, statut);
        voletParamedical(centre, seance);
        voletMedical(centre, seance);
        administration(centre, patient, seance, jour);
        return seance;
    }

    // ------------------------------------------------------------------ état SQL

    /** Ce qui reste autour d'une séance : lignes de séance, volets, administrations, consommations, bons. */
    protected Map<String, Object> traces(UUID seance) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seances", compter("SELECT COUNT(*) FROM seances WHERE id = ?", seance));
        m.put("volets paramedicaux", compter("SELECT COUNT(*) FROM volet_paramedical WHERE seance_id = ?", seance));
        m.put("volets medicaux", compter("SELECT COUNT(*) FROM volet_medical WHERE seance_id = ?", seance));
        m.put("administrations", compter("SELECT COUNT(*) FROM administrations_anemie WHERE seance_id = ?", seance));
        m.put("consommations", compter("SELECT COUNT(*) FROM consommations_de_seance WHERE seance_id = ?", seance));
        m.put("imputations", compter("SELECT COUNT(*) FROM consommations_de_seance_imputations WHERE seance_id = ?", seance));
        m.put("mouvements de stock", compter("SELECT COUNT(*) FROM stock_movements WHERE seance_id = ?", seance));
        return m;
    }

    protected int compter(String requete, Object... args) {
        Integer n = jdbc.queryForObject(requete, Integer.class, args);
        return n == null ? 0 : n;
    }

    /** Quantités restantes des lots des deux centres. */
    protected List<Map<String, Object>> lotsRestants() {
        return lignes("SELECT numero_lot, quantite_initiale, quantite_restante FROM lots WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY numero_lot");
    }

    /** Journal des suppressions des deux centres ; l'instant est masqué, l'identifiant propre du journal omis. */
    protected List<Map<String, Object>> journalDesSuppressions() {
        return lignes("SELECT center_id, seance_id, patient_id, date_seance, statut, motif, supprime_par, "
                + "(CASE WHEN supprime_le IS NULL THEN 'null' ELSE 'renseigne' END) AS supprime_le FROM seance_suppression "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY motif, statut, seance_id");
    }

    protected int nombreDeSuppressions() {
        return compter("SELECT COUNT(*) FROM seance_suppression WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    protected List<Map<String, Object>> notificationsEnregistrees() {
        return lignes("SELECT type, target_roles, payload FROM notification_evenement WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY type, payload");
    }

    /** La ligne brute d'une séance (valeurs en texte), pour comparer avant et après une écriture. */
    protected Map<String, String> ligneBrute(UUID seance) {
        Map<String, String> sortie = new LinkedHashMap<>();
        List<Map<String, Object>> l = jdbc.queryForList("SELECT * FROM seances WHERE id = ?", seance);
        if (l.isEmpty()) return sortie;
        l.getFirst().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sortie.put(e.getKey().toLowerCase(), e.getValue() == null ? null : String.valueOf(e.getValue())));
        return sortie;
    }

    /** Noms des colonnes dont la valeur a changé entre deux lectures brutes (valeurs non publiées : seuls les noms comptent). */
    protected List<String> colonnesModifiees(Map<String, String> avant, Map<String, String> apres) {
        List<String> noms = new ArrayList<>();
        for (String colonne : avant.keySet()) {
            String a = avant.get(colonne);
            String b = apres.get(colonne);
            if (a == null ? b != null : !a.equals(b)) noms.add(colonne);
        }
        return noms;
    }

    /** Date, statut et facture d'une séance, lisibles. */
    protected List<Map<String, Object>> dateEtStatut(UUID seance) {
        return lignes("SELECT date_seance, statut, facture_id FROM seances WHERE id = ?", seance);
    }

    /** Réponse de refus ou de succès, plus la date et le statut en base. */
    protected String issueEtDate(Resp r, UUID seance) {
        return refus(r) + " ; " + dateEtStatut(seance);
    }
}
