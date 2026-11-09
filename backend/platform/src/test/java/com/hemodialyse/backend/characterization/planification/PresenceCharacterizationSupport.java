package com.hemodialyse.backend.characterization.planification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Harnais de la slice P4 (présence des infirmiers, alertes, charge, impression, contrôle de 07:15), au-dessus du harnais de P3
 * (décor, patients placés, absences, remplacements, dates nommées) et des harnais P1 et P2.
 * <p>
 * Dates : mêmes familles d'alias que P3, qui ne se mélangent pas dans un scénario. Les scénarios qui lisent la semaine utilisent
 * la famille {@code <S1.LUN>} (semaine future entière) ; ceux qui dépendent du « aujourd'hui » du serveur (alertes, contrôle de
 * 07:15, effets des écritures de P3) utilisent {@code <J+n>} avec des patients placés sur les sept jours de la semaine, de sorte
 * qu'aucun résultat ne dépende du jour de la semaine. La charge mensuelle est lue sur un mois de référence fixe qui compte
 * exactement quatre occurrences de chaque jour de la semaine (février 2026, lundi à dimanche sans jour férié inséré par défaut) :
 * le calendrier ne dépend pas de l'horloge. Voir la note de la slice.
 */
public abstract class PresenceCharacterizationSupport extends RoulementCharacterizationSupport {

    protected static final String PRESENCE = "/api/v1/infirmiers/presence";
    /** Mois de référence de la charge : 28 jours, quatre fois chaque jour de la semaine. */
    protected static final String MOIS_REFERENCE = "2026-02";
    protected static final JourSemaine[] TOUS_LES_JOURS = JourSemaine.values();
    private static final Pattern DATE_FR = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @BeforeEach
    protected void setUpPresence() {
        nettoyerPresence();
    }

    @AfterEach
    protected void apresPresence() {
        nettoyerPresence();
    }

    protected void nettoyerPresence() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM center_closure_day WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM modele_document WHERE center_id = ?", centre);
        }
        jdbc.update("UPDATE centers SET actif = TRUE WHERE id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    // ------------------------------------------------------------------ fixtures

    /** Ratio de sécurité du centre (patients par infirmier), ligne de {@code planning_parametres} ; jours d'ouverture vides = tous. */
    protected void ratio(UUID centre, Integer patientsParInfirmier) {
        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", centre);
        jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, patients_par_poste_serie, "
                + "updated_at) VALUES (?, '', ?, NULL, CURRENT_TIMESTAMP)", centre, patientsParInfirmier);
    }

    /** Jours d'ouverture du centre (liste de noms de jours séparés par des virgules). */
    protected void joursOuverts(UUID centre, String jours) {
        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", centre);
        jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, patients_par_poste_serie, "
                + "updated_at) VALUES (?, ?, NULL, NULL, CURRENT_TIMESTAMP)", centre, jours);
    }

    protected void heuresParVacation(UUID centre, int heures) {
        jdbc.update("DELETE FROM planification_reglages WHERE center_id = ?", centre);
        jdbc.update("INSERT INTO planification_reglages (center_id, replanification_auto, heures_par_vacation, "
                + "heures_hebdo_temps_plein, repos_hebdo_min) VALUES (?, FALSE, ?, 40, 1)", centre, heures);
    }

    /** Fiche active et roulement d'une case (salle, créneau) sur les jours donnés. */
    protected UUID infirmierAffecte(UUID centre, String alias, String nom, String prenom, boolean isolement, UUID salle,
                                    UUID creneau, String jours) {
        UUID id = inf(centre, alias, nom, prenom, isolement);
        affectation(centre, "AFF_" + alias + "_" + creneau.toString().substring(30), id, salle, creneau, jours);
        return id;
    }

    protected static String tousLesJours() {
        return "DIMANCHE,LUNDI,MARDI,MERCREDI,JEUDI,VENDREDI,SAMEDI";
    }

    protected void nommerJours(int de, int a) {
        for (int i = de; i <= a; i++) jour(i);
    }

    protected void fermetureExceptionnelle(UUID centre, LocalDate jour, String motif) {
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), centre, java.sql.Date.valueOf(jour), motif);
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp semaineAu(LocalDate jour, RequestPostProcessor who) throws Exception {
        return doGet(PRESENCE + "/semaine?date=" + jour, who);
    }

    protected Resp alertes(String requete, RequestPostProcessor who) throws Exception {
        return doGet(PRESENCE + "/alertes" + requete, who);
    }

    /** Réponse brute (en-têtes et octets) pour l'impression. */
    protected MockHttpServletResponse brute(String path, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse();
    }

    // ------------------------------------------------------------------ projections lisibles

    /**
     * Projection de la semaine : comme {@link #casesDePresence(Resp)} mais sans les cases fermées sans patient (leur nombre est
     * donné à part) ; les jours fermés se lisent dans l'en-tête de la réponse.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> projection(Resp semaine) {
        Map<String, Object> p = casesDePresence(semaine);
        List<Map<String, Object>> cases = (List<Map<String, Object>>) p.get("cases");
        if (cases == null) return p;
        List<Map<String, Object>> gardees = new ArrayList<>();
        int fermees = 0;
        for (Map<String, Object> c : cases) {
            if ("FERME".equals(c.get("statut")) && Integer.valueOf(0).equals(c.get("patients"))) fermees++;
            else gardees.add(c);
        }
        p.put("casesFermeesSansPatient", fermees);
        p.put("cases", gardees);
        return p;
    }

    /** Alertes de sous-effectif : une ligne par case, lisible en revue. */
    protected List<String> lignesDAlertes(Resp r) {
        List<String> lignes = new ArrayList<>();
        for (JsonNode a : corps(r)) {
            List<String> absents = new ArrayList<>();
            a.path("absents").forEach(x -> absents.add(x.asString()));
            // le nom du jour est omis : il dépend du jour d'exécution (la date nommée le porte déjà)
            lignes.add(a.path("date").asString() + " " + a.path("salleId").asString() + " "
                    + a.path("creneauId").asString() + " patients=" + a.path("patients").asInt() + " requis=" + a.path("requis").asInt()
                    + " manque=" + a.path("manque").asInt() + " absents=" + absents);
        }
        return lignes;
    }

    /** Charge : une ligne par infirmier. */
    protected Map<String, Object> projectionDeLaCharge(Resp r) {
        JsonNode c = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("total", c.path("total").asLong());
        m.put("page", c.path("page").asInt());
        m.put("size", c.path("size").asInt());
        m.put("moyenne", c.path("moyenne").asDouble());
        List<String> lignes = new ArrayList<>();
        for (JsonNode i : c.path("items")) {
            lignes.add(i.path("nom").asString() + " : seances=" + i.path("seances").asInt() + " remplacements="
                    + i.path("remplacements").asInt() + " joursAbsence=" + i.path("joursAbsence").asInt() + " total="
                    + i.path("total").asInt());
        }
        m.put("items", lignes);
        return m;
    }

    /** Remplace les dates françaises (dd/MM/yyyy) nommées par leur alias ; toute date restante fait échouer le scénario. */
    protected String datesFr(String texte) {
        String resultat = texte;
        for (Map.Entry<String, String> e : datesNommees.entrySet()) {
            resultat = resultat.replace(LocalDate.parse(e.getKey()).format(FR), e.getValue());
        }
        if (DATE_FR.matcher(resultat).find()) {
            throw new AssertionError("Date française non nommée : " + resultat);
        }
        return resultat;
    }

    protected static String texte(byte[] octets) {
        return new String(octets, StandardCharsets.UTF_8);
    }
}
