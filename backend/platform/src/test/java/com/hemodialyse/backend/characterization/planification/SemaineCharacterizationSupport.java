package com.hemodialyse.backend.characterization.planification;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice P6 (planning de la semaine {@code GET /api/v1/planning/semaine}, capacité des centres lue par la Direction,
 * lectures des colonnes de place par la Séance), au-dessus du harnais de P5 (décor, générateurs, patients placés, sérologies).
 * <p>
 * Dates : famille {@code <S1.LUN>} de P3 uniquement ({@link #semaine(int)} nomme les sept jours d'une semaine). Un scénario nomme
 * explicitement chaque date que sa réponse peut contenir ({@link #nommerLaSemaine(int)} nomme la semaine et le dimanche suivant,
 * que {@code libereLe} atteint). Aucun scénario ne dépend du jour de la semaine, du jour du mois ni de l'instant (garde de
 * proximité de minuit héritée du harnais de P3).
 */
public abstract class SemaineCharacterizationSupport extends PlacementCharacterizationSupport {

    protected static final String SEMAINE = PLANNING + "/semaine";
    protected static final String CAPACITE_DIRECTION = "/api/v1/direction/capacite";

    // ------------------------------------------------------------------ fixtures

    /** Nomme les sept jours de la semaine {@code n} et le dimanche suivant ; rend le dimanche de la semaine. */
    protected LocalDate nommerLaSemaine(int n) {
        LocalDate dimanche = semaine(n);
        semaine(n + 1, JourSemaine.DIMANCHE);
        return dimanche;
    }

    /** Lecture de la semaine {@code n} (date demandée = le mercredi), avec le profil donné. */
    protected Resp lireLaSemaine(int n, org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        nommerLaSemaine(n);
        return doGet(SEMAINE + "?date=" + semaine(n, JourSemaine.MERCREDI), who);
    }

    protected Resp lireLaSemaine(int n) throws Exception {
        return lireLaSemaine(n, adminA());
    }

    /** Un centre minimal : une salle, un créneau, un générateur en service, rien d'autre. */
    protected void petitDecor() {
        salle1 = salle(CENTRE_A, "SALLE_1", "Salle 1", "NON");
        matin = creneau(CENTRE_A, "CRENEAU_MATIN", "Matin");
        g1a = generateur(CENTRE_A, "G1A", salle1, "EN_SERVICE");
    }

    protected void fermetureExceptionnelle(UUID centre, LocalDate jour, String motif) {
        jdbc.update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?, ?, ?, ?)", UUID.randomUUID(),
                centre, java.sql.Date.valueOf(jour), motif);
    }

    protected void jourFerieSansLibelle(UUID centre, LocalDate jour) {
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?, ?, ?, NULL)", UUID.randomUUID(),
                centre, java.sql.Date.valueOf(jour));
    }

    /** Déplacement temporaire lu par la semaine (écrit par l'application d'une optimisation, hors de ce filet). */
    protected void deplacementTemporaire(UUID centre, UUID patient, LocalDate jour, UUID salle, UUID creneau, UUID generateur) {
        jdbc.update("INSERT INTO deplacement_temporaire (id, center_id, patient_id, date_seance, salle_id, creneau_id, generateur_id, "
                        + "motif, cree_le) VALUES (?, ?, ?, ?, ?, ?, ?, 'MAINTENANCE', CURRENT_TIMESTAMP)", UUID.randomUUID(), centre,
                patient, java.sql.Date.valueOf(jour), salle, creneau, generateur);
    }

    /**
     * Séance d'un patient ; la place figée (salle, créneau, générateur) peut être nulle (séance validée avant que la place soit
     * mémorisée).
     */
    protected UUID seance(UUID centre, UUID patient, LocalDate jour, String statut, UUID salle, UUID creneau, UUID generateur) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning, salle_id, "
                        + "creneau_id, generateur_id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE, ?, ?, ?)", id, patient, centre,
                java.sql.Date.valueOf(jour), statut, salle, creneau, generateur);
        return id;
    }

    protected void admission(UUID patient, LocalDate jour) {
        jdbc.update("UPDATE patients SET date_admission = ? WHERE id = ?", java.sql.Date.valueOf(jour), patient);
    }

    protected void sommeil(UUID patient) {
        jdbc.update("UPDATE patients SET en_sommeil = TRUE WHERE id = ?", patient);
    }

    protected void nommerPatient(UUID patient, String nom, String prenom) {
        jdbc.update("UPDATE patients SET nom = ?, prenom = ? WHERE id = ?", nom, prenom, patient);
    }

    protected void nettoyerSemaine() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM seances WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM deplacement_temporaire WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM center_holiday WHERE center_id = ?", centre);
        }
    }

    @org.junit.jupiter.api.BeforeEach
    protected void setUpSemaine() {
        nettoyerSemaine();
    }

    @org.junit.jupiter.api.AfterEach
    protected void apresSemaine() {
        nettoyerSemaine();
    }

    // ------------------------------------------------------------------ projection lisible d'une semaine

    /**
     * Projection compacte : jours (ouverture, motif de fermeture), cases non vides (occupants avec générateur, drapeaux), capacité
     * par salle, conflits, séances sans case. Les cases vides de la grille ne sont pas listées (leur nombre l'est).
     */
    protected Map<String, Object> projectionDeLaSemaine(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        if (r.status() != 200) {
            m.put("corps", statutEtCode(r));
            return m;
        }
        JsonNode s = corps(r);
        Map<String, String> salles = new LinkedHashMap<>();
        s.path("salles").forEach(x -> salles.put(x.path("id").asString(), x.path("nom").asString()));
        Map<String, String> creneaux = new LinkedHashMap<>();
        s.path("creneaux").forEach(x -> creneaux.put(x.path("id").asString(), x.path("libelle").asString()));
        m.put("debut", s.path("debut").asString());
        m.put("fin", s.path("fin").asString());
        m.put("patientsAReplanifier", s.path("patientsAReplanifier").asInt());
        List<String> jours = new ArrayList<>();
        s.path("jours").forEach(j -> jours.add(j.path("jour").asString() + " " + j.path("date").asString()
                + (j.path("ouvertHebdomadaire").asBoolean() ? "" : " NON_OUVERT")
                + (j.path("fermetureMotif").isNull() ? "" : " FERMETURE=\"" + j.path("fermetureMotif").asString() + "\"")));
        m.put("jours", jours);
        m.put("salles", new ArrayList<>(salles.values()));
        m.put("creneaux", new ArrayList<>(creneaux.values()));
        m.put("nbCellules", s.path("cellules").size());
        Map<String, Integer> capacites = new LinkedHashMap<>();
        List<String> cases = new ArrayList<>();
        for (JsonNode c : s.path("cellules")) {
            String salle = salles.getOrDefault(c.path("salleId").asString(), "?");
            String creneau = creneaux.getOrDefault(c.path("creneauId").asString(), "?");
            capacites.merge(salle + " / " + c.path("jour").asString(), c.path("capacite").asInt(), Math::max);
            if (c.path("occupants").isEmpty()) continue;
            List<String> occupants = new ArrayList<>();
            for (JsonNode o : c.path("occupants")) {
                StringBuilder b = new StringBuilder(o.path("nom").asString());
                b.append(" (").append(o.path("generateurCode").isNull() ? "sans generateur" : o.path("generateurCode").asString());
                if (o.path("aRisque").asBoolean()) b.append(", risque");
                if (o.path("temporaire").asBoolean()) b.append(", temporaire");
                if (o.path("realiseeHorsPlanning").asBoolean()) b.append(", realisee hors planning");
                if (!o.path("libereLe").isNull()) b.append(", libere le ").append(o.path("libereLe").asString());
                if (!o.path("dejaRealiseeLe").isNull()) b.append(", deja realisee le ").append(o.path("dejaRealiseeLe").asString());
                occupants.add(b.append(')').toString());
            }
            cases.add(salle + " / " + creneau + " / " + c.path("jour").asString() + " cap=" + c.path("capacite").asInt()
                    + " " + occupants);
        }
        m.put("capaciteParSalleEtJour", capacites);
        m.put("cases", cases);
        List<String> conflits = new ArrayList<>();
        for (JsonNode c : s.path("conflits")) {
            conflits.add(c.path("type").asString() + " "
                    + (c.path("salleId").isNull() ? "-" : salles.getOrDefault(c.path("salleId").asString(), "?")) + " / "
                    + (c.path("creneauId").isNull() ? "-" : creneaux.getOrDefault(c.path("creneauId").asString(), "?")) + " / "
                    + (c.path("jour").isNull() ? "-" : c.path("jour").asString()) + " " + toListe(c.path("patients")));
        }
        m.put("conflits", conflits);
        List<String> sansCase = new ArrayList<>();
        s.path("seancesSansCase").forEach(x -> sansCase.add(x.path("nom").asString() + " " + x.path("date").asString() + " "
                + x.path("jour").asString()));
        m.put("seancesSansCase", sansCase);
        return m;
    }

    protected List<String> toListe(JsonNode tableau) {
        List<String> l = new ArrayList<>();
        tableau.forEach(x -> l.add(x.asString()));
        return l;
    }

    /** Empreinte des tables lues par la semaine : dit si une lecture a écrit quelque chose. */
    protected String empreinteDesPlaces() {
        return jdbc.queryForList("SELECT id, salle_id, position_id, generateur_id, jour_lundi, etat_patient FROM patients WHERE center_id IN (?, ?) "
                + "ORDER BY id", CENTRE_A, CENTRE_B).toString()
                + jdbc.queryForList("SELECT * FROM deplacement_temporaire WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B)
                .toString()
                + jdbc.queryForList("SELECT id, statut, salle_id, creneau_id, generateur_id FROM seances WHERE center_id IN (?, ?) ORDER BY id",
                CENTRE_A, CENTRE_B).toString()
                + jdbc.queryForList("SELECT * FROM planning_parametres WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B).toString();
    }
}
