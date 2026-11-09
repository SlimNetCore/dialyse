package com.hemodialyse.backend.characterization.planification;

import com.hemodialyse.backend.contexts.planification.application.LibererLesPlacesEchues;
import com.hemodialyse.backend.contexts.planification.interfaces.jobs.LiberationDesPlacesDe0400;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.characterization.planification.JourSemaine.LUNDI;
import static com.hemodialyse.backend.characterization.planification.JourSemaine.MERCREDI;

/**
 * Harnais de la slice P7 (libération des places : cron 04:00 {@code LiberationPlacesScheduler}, {@code LiberationPlacesService},
 * {@code Patient.libererPlacement}, mouvement {@code PLACE_LIBEREE}, libération à l'enregistrement d'une fiche), au-dessus du harnais
 * de P6 (décor, générateurs, patients placés, dates nommées, diffusions, audit).
 * <p>
 * Point d'observation : le job est appelé directement ({@code LiberationPlacesScheduler.libererPlaces()}, la méthode que le cron
 * déclenche), contexte Spring complet, H2 ; puis l'état SQL des colonnes de place de {@code patients} et de
 * {@code mouvement_patient}, les lectures HTTP consommatrices et le port de lecture de la détection des absences.
 * <p>
 * Dates : famille {@code <J-4>}...{@code <J+4>} de P3 ({@link #nommerLesJours()} nomme les jours -7 à +7) ; aucun scénario ne dépend du
 * jour de la semaine (les jours de dialyse des fixtures sont figés à lundi et mercredi, sauf mention contraire), ni du jour du mois,
 * ni de l'instant (garde de proximité de minuit et scénarios ignorés entre 23:00 et 24:00 UTC, hérités de P3). Le legacy lit
 * {@code LocalDate.now(UTC)} (service et mouvement) : le scénario {@code fuseau} le verrouille sans dépendre de cette heure.
 */
public abstract class LiberationCharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String MOUVEMENTS = "/api/v1/mouvements-patients";
    protected static final String CONTRAINTE_D_ECHEC = "ZZ_P7_ECHEC";
    protected static final String TOUS_LES_JOURS = "DIMANCHE,LUNDI,MARDI,MERCREDI,JEUDI,VENDREDI,SAMEDI";

    /**
     * Même point d'appel que dans le filet du legacy : {@code scheduler.libererPlaces()}.
     */
    protected final Nuit scheduler = new Nuit();
    /**
     * Le job de 04:00 du contexte Planification (P7), appelé directement comme l'ancien {@code LiberationPlacesScheduler}.
     */
    @Autowired
    protected LiberationDesPlacesDe0400 job;
    @Autowired
    protected LibererLesPlacesEchues liberer;

    /**
     * Les centres A et B qui ont encore au moins un patient avec une place (nommés <CENTRE_A>, <CENTRE_B>).
     */
    protected java.util.List<String> centresAvecPlaces() {
        return java.util.stream.Stream.of(CENTRE_A, CENTRE_B).filter(c -> patientsPlaces(c) > 0)
                .map(c -> c.equals(CENTRE_A) ? "<CENTRE_A>" : "<CENTRE_B>").sorted().toList();
    }

    @AfterEach
    protected void apresLiberation() {
        retirerLEchec();
    }

    // ------------------------------------------------------------------ fixtures

    /** Nomme les jours -7 à +7 (les goldens n'ont jamais de date réelle : le harnais échoue si une date non nommée reste). */
    protected void nommerLesJours() {
        for (int i = -7; i <= 7; i++) jour(i);
    }

    /** Patient placé (salle, créneau, générateur, lundi et mercredi) dans l'état de sortie donné. */
    protected UUID sorti(UUID centre, String alias, String etat, LocalDate evenement) {
        boolean centreB = centre.equals(CENTRE_B);
        UUID id = centreB ? patientPlace(centre, alias, salleB, creneauB, LUNDI, MERCREDI)
                : place(centre, alias, salle1, matin, g1a, LUNDI, MERCREDI);
        etat(id, etat, evenement);
        return id;
    }

    /** Pose le garde-fou qui fait refuser par la base l'écriture d'un mouvement pour ce patient (échec contrôlé du lot). */
    protected void faireEchouerLeMouvementDe(UUID patient) {
        retirerLEchec();
        jdbc.execute("ALTER TABLE mouvement_patient ADD CONSTRAINT " + CONTRAINTE_D_ECHEC + " CHECK (patient_id <> '" + patient + "')");
    }

    protected void retirerLEchec() {
        jdbc.execute("ALTER TABLE mouvement_patient DROP CONSTRAINT IF EXISTS " + CONTRAINTE_D_ECHEC);
    }

    // ------------------------------------------------------------------ observation

    /** Colonnes de place et jours de dialyse (D L M M J V S) de tous les patients des deux centres de test. */
    protected void etatDesPatients(String name) {
        sql(name, "SELECT nom, center_id, etat_patient AS etat, date_evenement_etat AS evenement, en_sommeil, salle_id, position_id, "
                + "generateur_id, CONCAT(CASE WHEN jour_dimanche THEN 'D' ELSE '.' END, CASE WHEN jour_lundi THEN 'L' ELSE '.' END, "
                + "CASE WHEN jour_mardi THEN 'M' ELSE '.' END, CASE WHEN jour_mercredi THEN 'M' ELSE '.' END, "
                + "CASE WHEN jour_jeudi THEN 'J' ELSE '.' END, CASE WHEN jour_vendredi THEN 'V' ELSE '.' END, "
                + "CASE WHEN jour_samedi THEN 'S' ELSE '.' END) AS jours FROM patients WHERE center_id IN ('" + CENTRE_A + "','"
                + CENTRE_B + "') ORDER BY center_id, nom");
    }

    /** Mouvements des deux centres (sans l'horodatage de création, qui n'est pas verrouillé). */
    protected void etatDesMouvements(String name) {
        sql(name, "SELECT p.nom, m.center_id, m.type, m.date_effet, m.etat_precedent, m.etat_nouveau, m.salle_id, m.position_id, "
                + "m.generateur_id, m.jours_dialyse, m.automatique FROM mouvement_patient m JOIN patients p ON p.id = m.patient_id "
                + "WHERE m.center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY m.center_id, p.nom, m.type, m.date_effet, m.jours_dialyse");
    }

    protected int mouvementsDeLiberation(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mouvement_patient WHERE center_id = ? AND type = 'PLACE_LIBEREE'",
                Integer.class, centre);
    }

    protected class Nuit {
        public void libererPlaces() {
            job.libererLesPlaces();
        }
    }

    protected int patientsPlaces(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ? AND (salle_id IS NOT NULL OR position_id IS NOT NULL "
                + "OR generateur_id IS NOT NULL)", Integer.class, centre);
    }

    /** Fiche lue par {@code GET /patients/{id}} : état et place seulement. */
    protected Map<String, Object> fichePatient(UUID centre, UUID patient) throws Exception {
        Resp r = doGet(PATIENTS + "/" + patient + "?centerId=" + centre, adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        if (r.status() == 200) {
            JsonNode c = corps(r);
            m.put("etatPatient", c.path("etatPatient").asString());
            m.put("salleId", c.path("salleId").asString("null"));
            m.put("positionId", c.path("positionId").asString("null"));
            m.put("generateurId", c.path("generateurId").asString("null"));
        }
        return m;
    }
}
