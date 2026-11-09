package com.hemodialyse.backend.characterization.planification;

import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Harnais de la slice P3 (roulement, absences, remplacements, « Mon planning »), au-dessus du harnais des infirmiers (P2) et
 * du harnais commun de Planification.
 * <p>
 * Dates : jamais de date réelle dans un golden. Deux familles d'alias, qui ne se mélangent pas dans un même scénario
 * (une collision d'alias fait échouer le test franchement, jamais selon le jour d'exécution) :
 * <ul>
 *   <li>{@code <J>}, {@code <J-4>}, {@code <J+3>} : jours relatifs à « aujourd'hui » (scénarios de « Mon planning », dont les règles
 *   comparent à la date du jour) ;</li>
 *   <li>{@code <S1.LUN>}, {@code <S1.SAM>}, {@code <S2.DIM>}... : jours de la semaine (dimanche à samedi) numérotée par rapport à
 *   la semaine courante ({@code S0}). Les semaines S1 et suivantes sont entièrement dans le futur, quel que soit le jour
 *   d'exécution : ces scénarios ne dépendent pas du jour de la semaine.</li>
 * </ul>
 * Horloge : seul le jour du centre compte ({@code Africa/Algiers}, décision du 26/10/2026) depuis le retrait du legacy, qui lisait
 * l'UTC : aucun scénario n'est plus ignoré selon l'heure. À moins de deux minutes de minuit au fuseau du centre, le test attend
 * (l'application lit l'horloge système). Aucun scénario ne dépend de l'instant d'exécution autrement.
 * <p>
 * Observation : état SQL normalisé des trois tables ({@code infirmier_affectation}, {@code infirmier_absence},
 * {@code infirmier_remplacement}), notifications durables ({@code notification_evenement}), diffusions temps réel (canal du
 * courtier de messages), audit (filtre générique, monté à la demande).
 */
public abstract class RoulementCharacterizationSupport extends InfirmiersCharacterizationSupport {

    protected static final java.time.ZoneId FUSEAU_DU_CENTRE = ZoneId.of("Africa/Algiers");
    private static final Path GOLDENS = Path.of("src", "test", "resources", "characterization", "planification");
    private static final Pattern HORODATAGE =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}(:?\\d{2})?|Z)?");
    private static final Pattern DATE_NUE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final List<String> ABREVIATIONS = List.of("DIM", "LUN", "MAR", "MER", "JEU", "VEN", "SAM");

    protected final Map<String, String> datesNommees = new LinkedHashMap<>();
    protected final List<Map<String, Object>> diffusions = new CopyOnWriteArrayList<>();
    private AbstractSubscribableChannel canal;
    private ChannelInterceptor capteur;

    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private com.hemodialyse.backend.application.audit.AuditWriterService ecrivainDAudit;

    // Décor : un centre A complet et un centre B minimal.
    protected UUID salle1;
    protected UUID salle2;
    protected UUID salleIsolement;
    protected UUID matin;
    protected UUID apresMidi;
    protected UUID salleB;
    protected UUID creneauB;

    // ------------------------------------------------------------------ cycle

    @BeforeEach
    protected void setUpRoulement() throws Exception {
        attendreSiProcheDeMinuit();
        nettoyerRoulement();
        datesNommees.clear();
        diffusions.clear();
        canal = context.getBean("brokerChannel", AbstractSubscribableChannel.class);
        capteur = new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
                if (destination != null) {
                    Object charge = message.getPayload();
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("destination", destination);
                    d.put("message", charge instanceof byte[] octets
                            ? mapper.readTree(new String(octets, StandardCharsets.UTF_8)) : String.valueOf(charge));
                    diffusions.add(d);
                }
                return message;
            }
        };
        canal.addInterceptor(capteur);
    }

    @AfterEach
    protected void apresRoulement() {
        if (canal != null && capteur != null) {
            canal.removeInterceptor(capteur);
            canal = null;
        }
        nettoyerRoulement();
    }

    protected void nettoyerRoulement() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM infirmier_absence WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM infirmier_remplacement WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM center_holiday WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM audit_log WHERE center_id = ?", centre);
        }
    }

    /**
     * Seul le jour du centre compte depuis le retrait du legacy (revue de P9, m8) : plus de double horloge UTC, plus de scénario
     * ignoré entre 23:00 et 24:00 UTC. L'application lit l'horloge système ; on évite seulement une bascule du jour du centre
     * pendant un test, en attendant minuit (fuseau du centre) si l'on en est à moins de deux minutes.
     */
    private static void attendreSiProcheDeMinuit() throws InterruptedException {
        Instant minuit = LocalDate.now(FUSEAU_DU_CENTRE).plusDays(1).atStartOfDay(FUSEAU_DU_CENTRE).toInstant();
        Duration reste = Duration.between(Instant.now(), minuit);
        if (reste.compareTo(Duration.ofMinutes(2)) < 0) {
            Thread.sleep(reste.toMillis() + 2000);
        }
    }

    // ------------------------------------------------------------------ décor

    /**
     * Centre A : trois salles (la troisième en isolement), deux créneaux ; centre B : une salle, un créneau.
     */
    protected void decor() {
        salle1 = salle(CENTRE_A, "SALLE_1", "Salle 1", "NON");
        salle2 = salle(CENTRE_A, "SALLE_2", "Salle 2", "NON");
        salleIsolement = salle(CENTRE_A, "SALLE_ISO", "Salle isolement", "OUI");
        matin = creneau(CENTRE_A, "CRENEAU_MATIN", "Matin");
        apresMidi = creneau(CENTRE_A, "CRENEAU_APRES_MIDI", "Apres-midi");
        salleB = salle(CENTRE_B, "SALLE_B", "Salle B", "NON");
        creneauB = creneau(CENTRE_B, "CRENEAU_B", "Creneau B");
    }

    /**
     * Patient permanent placé (salle, créneau, jours), admis dans le passé fixe : compté dans les cases à venir.
     */
    protected UUID patientPlace(UUID centre, String alias, UUID salle, UUID creneau, JourSemaine... jours) {
        UUID id = UUID.nameUUIDFromBytes(("planification-p3-patient-" + alias).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        List<JourSemaine> choisis = List.of(jours);
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, salle_id, position_id, jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, "
                        + "jour_jeudi, jour_vendredi, jour_samedi, created_at) VALUES (?, ?, ?, ?, ?, 'M', DATE '2026-01-01', ?, "
                        + "'NON_VACANCIER', 'PERMANENT', ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id, centre, "P3-" + alias, "Patient " + alias, "Test", "ASS-P3-" + alias, salle, creneau,
                choisis.contains(JourSemaine.DIMANCHE), choisis.contains(JourSemaine.LUNDI), choisis.contains(JourSemaine.MARDI),
                choisis.contains(JourSemaine.MERCREDI), choisis.contains(JourSemaine.JEUDI),
                choisis.contains(JourSemaine.VENDREDI), choisis.contains(JourSemaine.SAMEDI));
        return id;
    }

    /**
     * {@code n} patients permanents placés sur la même case de roulement (mêmes jours).
     */
    protected void patientsPlaces(UUID centre, String prefixe, int n, UUID salle, UUID creneau, JourSemaine... jours) {
        for (int i = 1; i <= n; i++) patientPlace(centre, prefixe + "_" + i, salle, creneau, jours);
    }

    protected void jourFerme(UUID centre, LocalDate jour, String libelle) {
        jdbc.update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), centre, java.sql.Date.valueOf(jour), libelle);
    }

    protected void absence(UUID centre, String alias, UUID infirmier, LocalDate debut, LocalDate fin, String type, String motif) {
        UUID id = UUID.nameUUIDFromBytes(("planification-p3-absence-" + alias).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO infirmier_absence (id, center_id, infirmier_id, date_debut, date_fin, type, motif) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", id, centre, infirmier, java.sql.Date.valueOf(debut),
                java.sql.Date.valueOf(fin), type, motif);
    }

    /**
     * Absence saisie par l'administration : le compte de l'auteur est conservé (V47, KB-PLN-39).
     */
    protected void absenceParLAdministration(UUID centre, String alias, UUID infirmier, LocalDate debut, LocalDate fin, String type,
                                             String motif) {
        absence(centre, alias, infirmier, debut, fin, type, motif);
        jdbc.update("UPDATE infirmier_absence SET declaree_par = ? WHERE id = ?", UUID.nameUUIDFromBytes("un-administrateur".getBytes(
                StandardCharsets.UTF_8)), absenceId(alias));
    }

    protected UUID absenceId(String alias) {
        return UUID.nameUUIDFromBytes(("planification-p3-absence-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    protected UUID remplacement(UUID centre, String alias, LocalDate jour, UUID salle, UUID creneau, UUID infirmier, UUID remplace) {
        UUID id = UUID.nameUUIDFromBytes(("planification-p3-remplacement-" + alias).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO infirmier_remplacement (id, center_id, date_jour, salle_id, creneau_id, infirmier_id, "
                        + "remplace_infirmier_id) VALUES (?, ?, ?, ?, ?, ?, ?)", id, centre, java.sql.Date.valueOf(jour), salle,
                creneau, infirmier, remplace);
        return id;
    }

    /**
     * Fiche active sans compte, matricule = alias.
     */
    protected UUID inf(UUID centre, String alias, String nom, String prenom, boolean isolement) {
        return fiche(centre, alias, alias, nom, prenom, null, "INFIRMIER", isolement, true, null);
    }

    // ------------------------------------------------------------------ identités

    protected static RequestPostProcessor comme(UUID utilisateur, UUID centre, String... roles) {
        return user(UserPrincipal.create(utilisateur.toString(), centre == null ? null : centre.toString(),
                "u-" + String.join("-", roles).toLowerCase(), "", List.of(roles), true));
    }

    protected static RequestPostProcessor secretaire() {
        return as(CENTRE_A, "SECRETAIRE");
    }

    // ------------------------------------------------------------------ dates

    /**
     * Aujourd'hui, au fuseau du centre (celui que lit l'application) : aucun scénario n'est plus ignoré selon l'heure d'exécution.
     */
    protected static LocalDate aujourdhui() {
        return LocalDate.now(FUSEAU_DU_CENTRE);
    }

    private void nommer(LocalDate date, String alias) {
        String deja = datesNommees.putIfAbsent(date.toString(), alias);
        if (deja != null && !deja.equals(alias)) {
            throw new IllegalStateException("Collision d'alias de dates : " + date + " = " + deja + " et " + alias
                    + " (ne pas mélanger les familles J et S dans un scénario)");
        }
    }

    /**
     * Nomme une date de calendrier fixe ou dérivée (ex. {@code <S1.LUN+365>}) pour les goldens.
     */
    protected LocalDate alias(LocalDate date, String alias) {
        nommer(date, alias);
        return date;
    }

    /**
     * Jour relatif à aujourd'hui ({@code <J>}, {@code <J-4>} passé, {@code <J+3>} futur).
     */
    protected LocalDate jour(int decalage) {
        LocalDate d = aujourdhui().plusDays(decalage);
        nommer(d, decalage == 0 ? "<J>" : decalage < 0 ? "<J" + decalage + ">" : "<J+" + decalage + ">");
        return d;
    }

    /**
     * Jour d'une semaine (dimanche à samedi) numérotée depuis la semaine courante : {@code <S1.LUN>}.
     */
    protected LocalDate semaine(int n, JourSemaine jour) {
        LocalDate aujourdhui = aujourdhui();
        LocalDate dimanche = aujourdhui.minusDays(aujourdhui.getDayOfWeek().getValue() % 7).plusWeeks(n);
        LocalDate d = dimanche.plusDays(jour.ordinal());
        nommer(d, "<S" + n + "." + ABREVIATIONS.get(jour.ordinal()) + ">");
        return d;
    }

    /**
     * Nomme les sept jours d'une semaine (utile pour les réponses qui les listent toutes).
     */
    protected LocalDate semaine(int n) {
        for (JourSemaine j : JourSemaine.values()) semaine(n, j);
        return semaine(n, JourSemaine.DIMANCHE);
    }

    /**
     * Remplace les horodatages puis les dates nommées ; toute date réelle restante fait échouer le scénario.
     */
    protected String dates(String texte) {
        if (texte == null) return null;
        String resultat = HORODATAGE.matcher(texte).replaceAll("<HORODATAGE>");
        for (Map.Entry<String, String> e : datesNommees.entrySet()) {
            resultat = resultat.replace(e.getKey(), e.getValue());
        }
        if (DATE_NUE.matcher(resultat).find()) {
            throw new AssertionError("Date réelle non nommée dans le golden : " + resultat);
        }
        return resultat;
    }

    private Resp avecDates(Resp r) {
        return new Resp(r.status(), r.contentType(), dates(r.raw()), r.cacheControl());
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp appel(String methode, String path, String corps, RequestPostProcessor who) throws Exception {
        if (who == null) {
            return switch (methode) {
                case "GET" -> doGetAnonymous(path);
                default -> send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.valueOf(methode), java.net.URI.create(path))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(corps == null ? "" : json(corps)));
            };
        }
        return switch (methode) {
            case "GET" -> doGet(path, who);
            case "DELETE" -> doDelete(path, who);
            case "PUT" -> doPut(path, json(corps), who);
            default -> doPost(path, json(corps), who);
        };
    }

    protected Resp doDeleteAvecCorps(String path, String corps, RequestPostProcessor who) throws Exception {
        return send(delete(java.net.URI.create(path)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(corps).with(who));
    }

    /**
     * Lance les appels en même temps (verrou de départ commun) et rend leurs réponses dans l'ordre.
     */
    @SafeVarargs
    protected final List<Resp> enParallele(Callable<Resp>... appels) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(appels.length);
        try {
            CountDownLatch depart = new CountDownLatch(1);
            List<Future<Resp>> futurs = new ArrayList<>();
            for (Callable<Resp> a : appels) {
                futurs.add(pool.submit(() -> {
                    depart.await();
                    return a.call();
                }));
            }
            depart.countDown();
            List<Resp> reponses = new ArrayList<>();
            for (Future<Resp> f : futurs) reponses.add(f.get());
            return reponses;
        } finally {
            pool.shutdownNow();
        }
    }

    protected void monterLAudit() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter, filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    protected void viderLAudit() {
        ecrivainDAudit.flush();
    }

    // ------------------------------------------------------------------ goldens (avec dates nommées)

    private String nomDuGolden(String name) {
        return Files.exists(GOLDENS.resolve(theme()).resolve(name + "_nouveau_code.approved.json")) ? name + "_nouveau_code" : name;
    }

    /**
     * Réponse d'une lecture (avec Cache-Control).
     */
    protected void lecture(String name, Resp r) {
        golden(name, avecDates(r));
    }

    /**
     * Réponse d'une écriture (sans Cache-Control).
     */
    protected void ecriture(String name, Resp r) {
        goldenSimple(name, avecDates(r));
    }

    protected void arbre(String name, Object valeur) {
        JsonNode noeud = mapper.valueToTree(valeur);
        goldenNode(name, mapper.readTree(dates(mapper.writeValueAsString(noeud))));
    }

    protected void sql(String name, String requete, Object... args) {
        ArrayNode arr = mapper.createArrayNode();
        for (Map<String, Object> row : jdbc.queryForList(requete, args)) {
            ObjectNode o = mapper.createObjectNode();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> o.put(e.getKey().toLowerCase(), e.getValue() == null ? null : String.valueOf(e.getValue())));
            arr.add(o);
        }
        goldenNode(name, mapper.readTree(dates(mapper.writeValueAsString(arr))));
    }

    // ------------------------------------------------------------------ observation de la base

    protected void etatAffectations(String name) {
        sql(name, "SELECT infirmier_id, salle_id, creneau_id, jours, id FROM infirmier_affectation "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id, infirmier_id, creneau_id, jours, salle_id");
    }

    protected void etatAbsences(String name) {
        sql(name, "SELECT center_id, infirmier_id, date_debut, date_fin, type, motif, id FROM infirmier_absence "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id, infirmier_id, date_debut, date_fin, type, motif, id");
    }

    /** Le compte qui a saisi chaque absence (V47, KB-PLN-39) : l'auteur est celui de la session, jamais un paramètre. */
    protected void auteursDesAbsences(String name) {
        sql(name, "SELECT infirmier_id, declaree_par FROM infirmier_absence WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY infirmier_id, date_debut, id");
    }

    protected void etatRemplacements(String name) {
        sql(name, "SELECT center_id, date_jour, salle_id, creneau_id, infirmier_id, remplace_infirmier_id, id FROM infirmier_remplacement "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id, date_jour, creneau_id, salle_id, infirmier_id, id");
    }

    protected int nombreDe(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B);
    }

    /**
     * Empreinte des trois tables de la slice : dit si une requête a écrit quelque chose.
     */
    protected String empreinteP3() {
        return jdbc.queryForList("SELECT infirmier_id, salle_id, creneau_id, jours FROM infirmier_affectation WHERE center_id IN (?, ?) "
                + "ORDER BY id", CENTRE_A, CENTRE_B).toString()
                + jdbc.queryForList("SELECT infirmier_id, date_debut, date_fin, type, motif FROM infirmier_absence WHERE center_id IN (?, ?) "
                + "ORDER BY id", CENTRE_A, CENTRE_B).toString()
                + jdbc.queryForList("SELECT date_jour, salle_id, creneau_id, infirmier_id, remplace_infirmier_id FROM infirmier_remplacement "
                + "WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B).toString();
    }

    protected void notifications(String name) {
        sql(name, "SELECT type, target_roles, payload FROM notification_evenement WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY type, payload");
    }

    protected void diffusionsCapturees(String name) {
        arbre(name, new ArrayList<>(diffusions));
    }

    protected void audit(String name) {
        ecrivainDAudit.flush();
        sql(name, "SELECT action_code, entity_type, entity_id, http_method, route_template, status_code FROM audit_log "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY route_template, http_method, status_code, entity_id");
    }

    // ------------------------------------------------------------------ projections lisibles de la présence (lecture P4)

    /**
     * Projection compacte d'une semaine de présence : seules les cases non vides (patients, prévus, remplaçants ou absents),
     * ses conflits et son compteur de sous-effectif. Lisible en revue, sans les cases vides de la grille.
     */
    protected Map<String, Object> casesDePresence(Resp semaine) {
        JsonNode s = corps(semaine);
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("statut", semaine.status());
        projection.put("debut", s.path("debut").asString());
        projection.put("casesSousEffectif", s.path("casesSousEffectif").asInt());
        List<Map<String, Object>> cases = new ArrayList<>();
        for (JsonNode c : s.path("cases")) {
            boolean vide = c.path("patients").asInt() == 0 && c.path("presents").isEmpty() && c.path("absents").isEmpty()
                    && "SANS_PATIENT".equals(c.path("statut").asString());
            if (vide) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", c.path("date").asString());
            m.put("salleId", c.path("salleId").asString());
            m.put("creneauId", c.path("creneauId").asString());
            m.put("statut", c.path("statut").asString());
            m.put("patients", c.path("patients").asInt());
            m.put("requis", c.path("requis").asInt());
            m.put("manque", c.path("manque").asInt());
            m.put("surplus", c.path("surplus").asInt());
            List<String> presents = new ArrayList<>();
            c.path("presents").forEach(p -> presents.add(p.path("nom").asString() + (p.path("remplacant").asBoolean() ? " (remplacant)" : "")));
            m.put("presents", presents);
            List<String> absents = new ArrayList<>();
            c.path("absents").forEach(a -> absents.add(a.path("nom").asString() + " : " + a.path("type").asString()));
            m.put("absents", absents);
            cases.add(m);
        }
        projection.put("cases", cases);
        List<Map<String, Object>> conflits = new ArrayList<>();
        for (JsonNode c : s.path("conflits")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", c.path("type").asString());
            m.put("date", c.path("date").asString());
            m.put("salleId", c.path("salleId").asString());
            m.put("creneauId", c.path("creneauId").asString());
            m.put("infirmier", c.path("infirmier").asString());
            conflits.add(m);
        }
        projection.put("conflits", conflits);
        return projection;
    }

    protected Resp presenceDeLaSemaine(int semaine, RequestPostProcessor who) throws Exception {
        return doGet("/api/v1/infirmiers/presence/semaine?date=" + semaine(semaine, JourSemaine.MERCREDI), who);
    }

    /**
     * Charge totale (séances + remplacements - absences ...) d'un infirmier sur le mois de la date donnée, par son nom.
     */
    protected int chargeTotale(LocalDate dansLeMois, String nomComplet) throws Exception {
        Resp r = doGet("/api/v1/infirmiers/presence/charge?mois=" + dansLeMois.toString().substring(0, 7) + "&size=100", adminA());
        for (JsonNode i : corps(r).path("items")) {
            if (nomComplet.equals(i.path("nom").asString())) return i.path("total").asInt();
        }
        return -1;
    }

    protected int remplacementsDansLaCharge(LocalDate dansLeMois, String nomComplet) throws Exception {
        Resp r = doGet("/api/v1/infirmiers/presence/charge?mois=" + dansLeMois.toString().substring(0, 7) + "&size=100", adminA());
        for (JsonNode i : corps(r).path("items")) {
            if (nomComplet.equals(i.path("nom").asString())) return i.path("remplacements").asInt();
        }
        return -1;
    }

    protected static String corpsAffectation(UUID salle, UUID creneau, String... jours) {
        return "{'salleId':'" + salle + "','creneauId':'" + creneau + "','jours':[" + quoted(jours) + "]}";
    }

    protected static String quoted(String... valeurs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < valeurs.length; i++)
            sb.append(i == 0 ? "" : ",").append('\'').append(valeurs[i]).append('\'');
        return sb.toString();
    }

    protected static String corpsAbsence(UUID infirmier, LocalDate debut, LocalDate fin, String type, String motif) {
        return "{'infirmierId':'" + infirmier + "','debut':'" + debut + "','fin':'" + fin + "','type':'" + type + "'"
                + (motif == null ? "" : ",'motif':'" + motif + "'") + "}";
    }

    protected static String corpsMonAbsence(LocalDate debut, LocalDate fin, String type, String motif) {
        return "{'debut':'" + debut + "','fin':'" + fin + "','type':'" + type + "'" + (motif == null ? "" : ",'motif':'" + motif + "'") + "}";
    }

    protected static String corpsRemplacement(LocalDate date, UUID salle, UUID creneau, UUID infirmier, UUID remplace) {
        return "{'date':'" + date + "','salleId':'" + salle + "','creneauId':'" + creneau + "','infirmierId':'" + infirmier + "'"
                + (remplace == null ? "" : ",'remplaceId':'" + remplace + "'") + "}";
    }

    /**
     * Vérifie un golden déjà écrit sans passer par {@link #nomDuGolden(String)} (réservé aux tests de non-régression de l'harnais).
     */
    protected void verifierBrut(String name, String contenu) {
        GoldenMaster.verify(GOLDENS.resolve(theme()), nomDuGolden(name), contenu);
    }
}
