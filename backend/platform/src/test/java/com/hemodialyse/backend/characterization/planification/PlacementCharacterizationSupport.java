package com.hemodialyse.backend.characterization.planification;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

/**
 * Harnais de la slice P5 (placement des patients : propositions de place, contrôle d'un placement enregistré sur la fiche patient,
 * réaffectation en isolement après sérologie, capacité des salles, alerte « générateur indisponible »), au-dessus du harnais de P3
 * (décor, patients placés, dates nommées, diffusions, audit) et des harnais P1 et P2.
 * <p>
 * Fixtures : générateurs de dialyse dans {@code gmao_equipements} (insérés en base, jamais par le contrôleur GMAO sauf pour les
 * scénarios qui observent l'écouteur d'événements), sérologies dans {@code serologies_patient}, patients permanents placés (salle,
 * créneau, générateur, jours). Les sérologies sont datées à des dates fixes lointaines jamais écrites dans un golden.
 * <p>
 * Dates : les alias sont ceux de P3 ({@code <J+3>}...) ; les scénarios de propositions et de placement ne dépendent pas du jour de la
 * semaine ni de l'instant (les fermetures datées sont lues à 90 jours glissants ; le nom du jour imposé est masqué quand il en dérive).
 */
public abstract class PlacementCharacterizationSupport extends RoulementCharacterizationSupport {

    protected static final String AFFECTATIONS = PLANNING + "/affectations";
    protected static final String SALLES = PLANNING + "/salles";
    protected static final String PATIENTS = "/api/v1/patients";
    protected static final String GMAO = "/api/v1/gmao/equipements";
    protected static final UUID U_GMAO = UUID.fromString("99997800-0000-0000-0000-0000000000c2");

    /** Générateurs du décor standard : deux en salle 1, un en salle 2, un en salle d'isolement. */
    protected UUID g1a;
    protected UUID g1b;
    protected UUID g2a;
    protected UUID giso;

    @BeforeEach
    protected void setUpPlacement() {
        nettoyerPlacement();
    }

    @AfterEach
    protected void apresPlacement() {
        nettoyerPlacement();
    }

    protected void nettoyerPlacement() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM gmao_equipement_statut_historique WHERE centre_id = ?", centre);
            jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", centre);
            jdbc.update("DELETE FROM center_closure_day WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", centre);
        }
    }

    // ------------------------------------------------------------------ fixtures

    /** Décor de P3 (trois salles dont une d'isolement, deux créneaux) et un générateur de dialyse en service par place. */
    protected void decorDePlacement() {
        decor();
        g1a = generateur(CENTRE_A, "G1A", salle1, "EN_SERVICE");
        g1b = generateur(CENTRE_A, "G1B", salle1, "EN_SERVICE");
        g2a = generateur(CENTRE_A, "G2A", salle2, "EN_SERVICE");
        giso = generateur(CENTRE_A, "GISO", salleIsolement, "EN_SERVICE");
    }

    protected UUID generateur(UUID centre, String code, UUID salle, String statut) {
        return equipement(centre, code, "GENERATEUR_DIALYSE", salle, statut);
    }

    protected UUID equipement(UUID centre, String code, String type, UUID salle, String statut) {
        UUID id = UUID.nameUUIDFromBytes(("planification-p5-equipement-" + centre + "-" + code).getBytes());
        aliasNommes.put(id.toString(), "<" + code + ">");
        OffsetDateTime maintenant = OffsetDateTime.of(2026, 1, 5, 9, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "date_creation, cree_par, salle_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, "Equipement " + code, type, centre, statut, maintenant, maintenant, U_GMAO, salle);
        return id;
    }

    /** Patient permanent placé sur un générateur précis. */
    protected UUID place(UUID centre, String alias, UUID salle, UUID creneau, UUID generateur, JourSemaine... jours) {
        UUID id = patientPlace(centre, alias, salle, creneau, jours);
        jdbc.update("UPDATE patients SET generateur_id = ? WHERE id = ?", generateur, id);
        return id;
    }

    /** Fixe l'état du patient (sorti, sommeil...) et sa date d'événement (relative à aujourd'hui, nommée). */
    protected void etat(UUID patient, String etat, LocalDate evenement) {
        jdbc.update("UPDATE patients SET etat_patient = ?, date_evenement_etat = ? WHERE id = ?", etat,
                evenement == null ? null : java.sql.Date.valueOf(evenement), patient);
    }

    protected void serologie(UUID centre, UUID patient, String marqueur, String resultat, String datePrelevement) {
        OffsetDateTime maintenant = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO serologies_patient (id, center_id, date_prelevement, marqueur, patient_id, resultat, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), centre,
                java.sql.Date.valueOf(datePrelevement), marqueur, patient, resultat, maintenant, maintenant);
    }

    protected void joursOuvertsDuCentre(UUID centre, String jours) {
        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", centre);
        jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, patients_par_poste_serie, "
                + "updated_at) VALUES (?, ?, NULL, NULL, CURRENT_TIMESTAMP)", centre, jours);
    }

    // ------------------------------------------------------------------ corps de requête

    /**
     * Fiche patient complète minimale (création ou modification) avec le placement demandé ; {@code jours} = noms de
     * {@link JourSemaine} en majuscules.
     */
    protected static String fiche(UUID centre, String nom, String numeroAssurance, UUID salle, UUID creneau, UUID generateur,
                                  String... jours) {
        List<String> choisis = List.of(jours);
        StringBuilder b = new StringBuilder("{");
        b.append("'centerId':'").append(centre).append("','nom':'").append(nom).append("','prenom':'Test','sexe':'M',");
        b.append("'dateAdmission':'2026-01-01','dateNaissance':'1970-01-01','nombreEnfants':0,'etatPatient':'PERMANENT',");
        b.append("'attestationDebut':'2026-01-01','attestationFin':'2036-01-01',");
        b.append("'sousKt':false,'epoEnabled':false,'ferEnabled':false,'enSommeil':false,");
        b.append("'qualiteAssure':'ASSURE_LUI_MEME','numeroAssurance':'").append(numeroAssurance).append("',");
        b.append("'salleId':").append(salle == null ? "null" : "'" + salle + "'").append(',');
        b.append("'positionId':").append(creneau == null ? "null" : "'" + creneau + "'").append(',');
        b.append("'generateurId':").append(generateur == null ? "null" : "'" + generateur + "'").append(',');
        for (JourSemaine j : JourSemaine.values()) {
            String n = j.name().charAt(0) + j.name().substring(1).toLowerCase();
            b.append("'jour").append(n).append("':").append(choisis.contains(j.name())).append(',');
        }
        b.setLength(b.length() - 1);
        return json(b.append('}').toString());
    }

    /** Fiche d'un patient fixture (nom et numéro d'assurance de {@link #patientPlace}). */
    protected static String ficheDe(String alias, UUID centre, UUID salle, UUID creneau, UUID generateur, String... jours) {
        return fiche(centre, "Patient " + alias, "ASS-P3-" + alias, salle, creneau, generateur, jours);
    }

    protected static String corpsSerologie(UUID centre, String marqueur, String resultat) {
        return json("{'centerId':'" + centre + "','marqueur':'" + marqueur + "','resultat':'" + resultat
                + "','datePrelevement':'2026-03-01','conduiteATenir':'Isoler le patient'}");
    }

    // ------------------------------------------------------------------ requêtes et attente des événements

    protected static RequestPostProcessor medecin() {
        return as(CENTRE_A, "MEDECIN");
    }

    protected static RequestPostProcessor gmao() {
        return comme(U_GMAO, CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor reformeur() {
        return comme(U_GMAO, CENTRE_A, "ADMIN", "GMAO_REFORME");
    }

    /** Les écouteurs d'événements GMAO sont livrés après commit, en asynchrone : on attend que le registre soit vide. */
    protected void attendreLesEvenements() {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(
                () -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class) == 0);
    }

    // ------------------------------------------------------------------ observation

    /** Place d'un patient : salle, créneau, générateur et jours, lisible (alias). */
    protected void etatDesPlaces(String name) {
        sql(name, "SELECT nom, center_id, salle_id, position_id, generateur_id, "
                + "jour_dimanche AS dim, jour_lundi AS lun, jour_mardi AS mar, jour_mercredi AS mer, jour_jeudi AS jeu, "
                + "jour_vendredi AS ven, jour_samedi AS sam FROM patients WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY center_id, nom");
    }

    /** Le code patient d'une création est aléatoire ({@code PAT-xxxxxxxx}) : masqué. */
    protected Resp sansCodeAleatoire(Resp r) {
        return new Resp(r.status(), r.contentType(),
                r.raw() == null ? null : r.raw().replaceAll("PAT-[0-9A-F]{8}", "<CODE_PATIENT>"), r.cacheControl());
    }

    /** Projection d'une réponse de propositions : une ligne par proposition, plus alertes et grille résumée. */
    protected Map<String, Object> projectionDesPropositions(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        if (r.status() != 200) {
            m.put("corps", statutEtCode(r));
            return m;
        }
        JsonNode c = corps(r);
        List<String> lignes = new ArrayList<>();
        for (JsonNode p : c.path("propositions")) {
            List<String> raisons = new ArrayList<>();
            p.path("raisons").forEach(x -> raisons.add(x.asString()));
            List<String> jours = new ArrayList<>();
            p.path("jours").forEach(x -> jours.add(x.asString()));
            List<String> autres = new ArrayList<>();
            p.path("generateursAlternatifs").forEach(x -> autres.add(x.path("code").asString()));
            List<String> fermetures = new ArrayList<>();
            p.path("fermetures").forEach(x -> fermetures.add(x.path("date").asString() + " " + x.path("motif").asString()));
            lignes.add("score=" + p.path("score").asInt() + " " + p.path("salle").path("nom").asString() + " / "
                    + p.path("creneau").path("libelle").asString() + " / " + p.path("generateur").path("code").asString()
                    + " autres=" + autres + " jours=" + jours + " raisons=" + raisons
                    + (fermetures.isEmpty() ? "" : " fermetures=" + fermetures));
        }
        m.put("propositions", lignes);
        List<String> alertes = new ArrayList<>();
        c.path("alertes").forEach(x -> alertes.add(x.asString()));
        m.put("alertes", alertes);
        m.put("patientARisque", c.path("patientARisque").asBoolean());
        return m;
    }

    /** Grille résumée : cases occupées seulement, ou capacité d'une case. */
    protected List<String> grilleOccupee(Resp r) {
        List<String> l = new ArrayList<>();
        for (JsonNode k : corps(r).path("grille")) {
            if (k.path("occupes").asInt() > 0) {
                l.add(k.path("salleId").asString() + " " + k.path("creneauId").asString() + " " + k.path("jour").asString()
                        + " " + k.path("occupes").asInt() + "/" + k.path("capacite").asInt());
            }
        }
        return l;
    }
}
