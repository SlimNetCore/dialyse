package com.hemodialyse.backend.characterization.dossiermedical.dm7;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Harnais de la slice DM7 (bilan pré-greffe : bilan du receveur et décisions de RCP, checklist d'étapes, donneurs vivants,
 * évaluations KDIGO, export PDF), au-dessus des harnais de Planification (décor, dates nommées, diffusions, audit).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}), état SQL des tables REELLES {@code bilans_pre_greffe}, {@code decisions_rcp}, {@code etapes_bilan_pre_greffe} et
 * {@code donneurs_vivants}, audit, et lecteurs observés sans les modifier (sérologie, antécédents, analyses). Aucun mock du code
 * caractérisé, aucun code de production modifié.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (« Patient &lt;alias&gt; », « Donneur &lt;alias&gt; », valeurs rondes). Dates
 * explicites FIXES nommées {@code <D+n>} ; le domaine lit {@code LocalDate.now()} (fuseau de la JVM) pour les dates de statut et la limite
 * « pas dans le futur » d'une décision de RCP : dates {@code <J>} / {@code <J+1>}, avec garde de minuit.
 */
public abstract class Dm7CharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String CENTRES = "('" + CENTRE_A + "','" + CENTRE_B + "')";
    private static final String CREATION = "(CASE WHEN %1$s.created_at < TIMESTAMP WITH TIME ZONE '2026-01-02 10:00:00+00:00' "
            + "THEN 'fixe' ELSE 'recent' END) AS creation";
    // Une ligne créée par l'application et modifiée dans la même requête a un écart created_at / updated_at de l'ordre de la
    // microseconde (selon l'horloge de la plate-forme) : la modification n'est donc dite que pour les lignes posées par le harnais.
    private static final String MODIFICATION = "(CASE WHEN %1$s.created_at > TIMESTAMP WITH TIME ZONE '2026-01-02 10:00:00+00:00' THEN 'ligne creee par l application' "
            + "WHEN %1$s.updated_at > %1$s.created_at THEN 'modifiee' ELSE 'jamais modifiee' END) AS modification";

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test (ex. {@code bilan/etat}). */
    protected abstract String dossier();

    @Override
    protected final String theme() {
        return "../dossiermedical/dm7/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpDm7() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerDm7();
    }

    @AfterEach
    protected void apresDm7() {
        nettoyerDm7();
    }

    protected void nettoyerDm7() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM decisions_rcp WHERE bilan_id IN (SELECT id FROM bilans_pre_greffe WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM bilans_pre_greffe WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM etapes_bilan_pre_greffe WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM donneurs_vivants WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM antecedents_medicaux WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
    }

    private static void attendreSiProcheDeMinuitDeLaJvm() throws InterruptedException {
        ZoneId zone = ZoneId.systemDefault();
        Instant minuit = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        Duration reste = Duration.between(Instant.now(), minuit);
        if (reste.compareTo(Duration.ofMinutes(2)) < 0) {
            Thread.sleep(reste.toMillis() + 2000);
        }
    }

    // ------------------------------------------------------------------ dates

    protected LocalDate fixe(int n) {
        return alias(REPERE.plusDays(n), "<D" + (n < 0 ? n : "+" + n) + ">");
    }

    protected LocalDate relatif(int n) {
        return alias(LocalDate.now().plusDays(n), n == 0 ? "<J>" : n < 0 ? "<J" + n + ">" : "<J+" + n + ">");
    }

    // ------------------------------------------------------------------ fixtures

    protected static UUID idStable(String famille, String alias) {
        return UUID.nameUUIDFromBytes(("dm7-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    protected UUID patientDm7(UUID centre, String alias) {
        return patientDm7(centre, alias, "M", null, null);
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}) ; sexe, date de naissance et groupe sanguin déclarés optionnels. */
    protected UUID patientDm7(UUID centre, String alias, String sexe, LocalDate naissance, String groupeSanguin) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, groupe_sanguin, date_admission, "
                        + "numero_assurance, type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, "DM7-" + alias, "Patient " + alias, "Test", sexe, naissance == null ? null : java.sql.Date.valueOf(naissance),
                groupeSanguin, java.sql.Date.valueOf(REPERE.minusDays(400)), "ASS-DM7-" + alias, CREATION_FIXE);
        // La lecture d'un patient est mise en cache par identifiant : les identifiants de fixtures étant stables d'un cas à l'autre
        // d'une boucle, on vide les caches pour que chaque cas relise sa propre ligne.
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom)).ifPresent(org.springframework.cache.Cache::clear));
        return id;
    }

    /** Bilan posé en base (créé et modifié à l'instant fixe). Les dates et valeurs absentes sont nulles. */
    protected UUID bilanEnBase(UUID centre, UUID patient, String alias, String statut, LocalDate debut, LocalDate inscription,
                               LocalDate greffe, String groupe, String hla, String praI, String praII, String contre, String conclusion) {
        UUID id = idStable("bilan", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO bilans_pre_greffe (id, patient_id, center_id, statut, date_debut_bilan, date_inscription_liste_attente, "
                        + "date_greffe, groupe_sanguin_confirme, typage_hla, pra_classe_i, pra_classe_ii, contre_indications, "
                        + "conclusion_nephrologue, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", id, patient, centre, statut,
                date(debut), date(inscription), date(greffe), groupe, hla, praI == null ? null : new BigDecimal(praI),
                praII == null ? null : new BigDecimal(praII), contre, conclusion, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    protected UUID bilanSimple(UUID centre, UUID patient, String alias, String statut) {
        return bilanEnBase(centre, patient, alias, statut, null, null, null, null, null, null, null, null, null);
    }

    protected UUID decisionEnBase(UUID bilan, String alias, LocalDate reunion, String avis, String compteRendu, LocalDate revue) {
        UUID id = idStable("decision", alias);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO decisions_rcp (id, bilan_id, date_reunion, avis, compte_rendu, prochaine_date_revue) VALUES (?,?,?,?,?,?)",
                id, bilan, java.sql.Date.valueOf(reunion), avis, compteRendu, date(revue));
        return id;
    }

    protected UUID etapeEnBase(UUID centre, UUID patient, String alias, String categorie, String libelle, String statut,
                               LocalDate realisation, String resultat) {
        UUID id = idStable("etape", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO etapes_bilan_pre_greffe (id, patient_id, center_id, categorie, libelle, statut, date_realisation, resultat, "
                        + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)", id, patient, centre, categorie, libelle, statut,
                date(realisation), resultat, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    /** Donneur posé en base ; {@code minute} décale la création (le tri de la liste est la création décroissante). */
    protected UUID donneurEnBase(UUID centre, UUID patient, String alias, String nom, String lien, String statut, String crossmatch, int minute) {
        UUID id = idStable("donneur", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO donneurs_vivants (id, patient_id, center_id, nom, prenom, lien_parente, statut_bilan, crossmatch_resultat, "
                        + "created_at, updated_at) VALUES (?,?,?,?,'Test',?,?,?,?,?)", id, patient, centre, nom, lien, statut, crossmatch,
                CREATION_FIXE.plusMinutes(minute), CREATION_FIXE.plusMinutes(minute));
        return id;
    }

    protected void serologieEnBase(UUID centre, UUID patient, String alias, String marqueur, String resultat, LocalDate prelevement) {
        UUID id = idStable("serologie", alias + "-" + centre);
        jdbc.update("INSERT INTO serologies_patient (id, center_id, patient_id, marqueur, resultat, date_prelevement, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?)", id, centre, patient, marqueur, resultat, java.sql.Date.valueOf(prelevement), CREATION_FIXE, CREATION_FIXE);
    }

    protected void antecedentEnBase(UUID centre, UUID patient, String alias, String libelle, String statut) {
        UUID id = idStable("antecedent", alias + "-" + centre);
        jdbc.update("INSERT INTO antecedents_medicaux (id, center_id, patient_id, type_antecedent, libelle_libre, date_debut, statut_clinique, "
                        + "created_at, updated_at) VALUES (?,?,?,'MEDICAL',?,?,?,?,?)", id, centre, patient, libelle,
                java.sql.Date.valueOf(fixe(5)), statut, CREATION_FIXE, CREATION_FIXE);
    }

    protected void creatinineEnBase(UUID centre, UUID patient, String alias, LocalDate prelevement, String creatinine) {
        UUID id = idStable("analyse", alias + "-" + centre);
        jdbc.update("INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, creatinine_mg_dl, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?)", id, patient, centre, java.sql.Date.valueOf(prelevement),
                creatinine == null ? null : new BigDecimal(creatinine), CREATION_FIXE, CREATION_FIXE);
    }

    private static java.sql.Date date(LocalDate d) {
        return d == null ? null : java.sql.Date.valueOf(d);
    }

    // ------------------------------------------------------------------ chemins et corps

    protected String corps(Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            if (paires[i + 1] != null) m.put((String) paires[i], paires[i + 1] instanceof UUID || paires[i + 1] instanceof LocalDate
                    ? paires[i + 1].toString() : paires[i + 1]);
        }
        return mapper.writeValueAsString(m);
    }

    protected String greffe(UUID patient, String suite) {
        return PATIENTS_API + "/" + patient + "/greffe/" + suite;
    }

    protected String bilanApi(UUID patient) {
        return greffe(patient, "bilan");
    }

    protected String etapesApi(UUID patient) {
        return greffe(patient, "etapes");
    }

    protected String etapeApi(UUID patient, UUID etape) {
        return greffe(patient, "etapes/" + etape);
    }

    protected String donneursApi(UUID patient) {
        return greffe(patient, "donneurs");
    }

    protected String donneurApi(UUID patient, UUID donneur) {
        return greffe(patient, "donneurs/" + donneur);
    }

    protected RequestPostProcessor medecinA() {
        return as(CENTRE_A, "MEDECIN");
    }

    protected RequestPostProcessor infirmierA() {
        return as(CENTRE_A, "INFIRMIER");
    }

    protected RequestPostProcessor adminA2() {
        return as(CENTRE_A, "ADMIN");
    }

    /** Les onze profils d'accès de Planification, un rôle personnalisé inconnu du serveur et deux cumuls de rôles. */
    protected Map<String, RequestPostProcessor> profilsDm7() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>(profils());
        p.put("role personnalise (CHEF_DE_SALLE)", as(CENTRE_A, "CHEF_DE_SALLE"));
        p.put("INFIRMIER + SECRETAIRE", as(CENTRE_A, "INFIRMIER", "SECRETAIRE"));
        p.put("MEDECIN + ADMIN", as(CENTRE_A, "MEDECIN", "ADMIN"));
        return p;
    }

    protected String idDe(Resp r) {
        return corps(r).path("id").asString();
    }

    // ------------------------------------------------------------------ lecture des réponses

    /** Statut, code et détail (sauf 5xx : le détail technique n'est pas un comportement métier). */
    protected String refus(Resp r) {
        if (r.status() == 200) return "200 ok";
        if (r.raw() == null || r.raw().isBlank()) return String.valueOf(r.status());
        try {
            JsonNode c = mapper.readTree(r.raw());
            String code = c.has("code") ? " " + c.path("code").asString() : "";
            String detail = r.status() < 500 && c.has("detail") ? " | " + c.path("detail").asString() : "";
            return r.status() + code + detail;
        } catch (RuntimeException e) {
            return String.valueOf(r.status());
        }
    }

    /** Une réponse lisible : statut et corps (pour une erreur 5xx, le seul code). */
    protected Map<String, Object> reponse(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        if (r.raw() == null || r.raw().isBlank()) return m;
        try {
            JsonNode c = mapper.readTree(r.raw());
            if (r.status() >= 500) {
                m.put("code", c.path("code").asString());
            } else {
                m.put("corps", c);
            }
        } catch (RuntimeException e) {
            m.put("texte", r.raw());
        }
        return m;
    }

    /** Les champs demandés d'un élément joints par « | » (absent ou nul : {@code -}). */
    protected String resume(JsonNode n, String... champs) {
        List<String> v = new ArrayList<>();
        for (String champ : champs) {
            JsonNode f = n.path(champ);
            v.add(f.isMissingNode() || f.isNull() ? "-" : f.asString());
        }
        return String.join(" | ", v);
    }

    protected String resume(Resp r, String... champs) {
        return r.status() == 200 ? "200 " + resume(corps(r), champs) : refus(r);
    }

    /** Les éléments d'un tableau JSON résumés par les champs demandés. */
    protected List<String> liste(Resp r, String... champs) {
        List<String> sortie = new ArrayList<>();
        if (r.status() != 200) {
            sortie.add(refus(r));
            return sortie;
        }
        corps(r).forEach(n -> sortie.add(resume(n, champs)));
        return sortie;
    }

    /** Réponse binaire (PDF) : l'octet-flux n'est pas lisible en UTF-8. */
    protected MockHttpServletResponse binaire(String path, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse();
    }

    // ------------------------------------------------------------------ état SQL

    protected List<Map<String, Object>> lignes(String requete, Object... args) {
        List<Map<String, Object>> sortie = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(requete, args)) {
            Map<String, Object> m = new LinkedHashMap<>();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> m.put(e.getKey().toLowerCase(), e.getValue() == null ? null : String.valueOf(e.getValue())));
            sortie.add(m);
        }
        return sortie;
    }

    protected List<Map<String, Object>> etatDesBilans() {
        return lignes("SELECT b.center_id, b.patient_id, b.statut, b.date_debut_bilan, b.date_inscription_liste_attente, b.date_greffe, "
                + "b.groupe_sanguin_confirme, b.typage_hla, b.pra_classe_i, b.pra_classe_ii, b.contre_indications, b.conclusion_nephrologue, b.id, "
                + CREATION.formatted("b") + ", " + MODIFICATION.formatted("b") + " FROM bilans_pre_greffe b WHERE b.center_id IN "
                + CENTRES + " ORDER BY b.center_id, b.patient_id, b.statut, b.id");
    }

    protected List<Map<String, Object>> etatDesDecisions() {
        return lignes("SELECT d.bilan_id, d.date_reunion, d.avis, d.compte_rendu, d.prochaine_date_revue, d.id FROM decisions_rcp d "
                + "WHERE d.bilan_id IN (SELECT id FROM bilans_pre_greffe WHERE center_id IN " + CENTRES + ") "
                + "ORDER BY d.date_reunion, d.avis, d.compte_rendu, d.id");
    }

    protected List<Map<String, Object>> etatDesEtapes() {
        return lignes("SELECT e.center_id, e.patient_id, e.categorie, e.libelle, e.statut, e.date_realisation, e.resultat, e.date_expiration, "
                + "e.demande_examen_id, e.serologie_id, e.id, " + CREATION.formatted("e") + ", " + MODIFICATION.formatted("e")
                + " FROM etapes_bilan_pre_greffe e WHERE e.center_id IN " + CENTRES
                + " ORDER BY e.center_id, e.patient_id, e.categorie, e.libelle, e.statut, e.id");
    }

    protected List<Map<String, Object>> etatDesDonneurs() {
        return lignes("SELECT d.center_id, d.patient_id, d.nom, d.prenom, d.date_naissance, d.lien_parente, d.telephone, d.groupe_sanguin, "
                + "d.typage_hla, d.statut_bilan, d.crossmatch_resultat, d.date_crossmatch, d.bilan_realise, d.contre_indications, "
                + "d.decision_finale, d.date_decision, d.id, " + CREATION.formatted("d") + ", " + MODIFICATION.formatted("d")
                + " FROM donneurs_vivants d WHERE d.center_id IN " + CENTRES + " ORDER BY d.center_id, d.patient_id, d.nom, d.id");
    }

    /** Empreinte brute des quatre tables : dit si une requête a écrit quelque chose. */
    protected String empreinteGreffe() {
        return jdbc.queryForList("SELECT * FROM bilans_pre_greffe WHERE center_id IN " + CENTRES + " ORDER BY id").toString()
                + jdbc.queryForList("SELECT * FROM decisions_rcp WHERE bilan_id IN (SELECT id FROM bilans_pre_greffe WHERE center_id IN "
                + CENTRES + ") ORDER BY id").toString()
                + jdbc.queryForList("SELECT * FROM etapes_bilan_pre_greffe WHERE center_id IN " + CENTRES + " ORDER BY id").toString()
                + jdbc.queryForList("SELECT * FROM donneurs_vivants WHERE center_id IN " + CENTRES + " ORDER BY id").toString();
    }

    protected int nombreDe(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    protected List<Map<String, Object>> journalDAudit() {
        viderLAudit();
        List<Map<String, Object>> lignes = new ArrayList<>(lignes("SELECT action_code, entity_type, entity_id, http_method, route_template, "
                + "status_code, COUNT(*) AS nombre FROM audit_log WHERE center_id IN " + CENTRES + " GROUP BY action_code, entity_type, "
                + "entity_id, http_method, route_template, status_code"));
        lignes.sort(java.util.Comparator.comparing((Map<String, Object> l) -> String.valueOf(l.get("route_template")))
                .thenComparing(l -> String.valueOf(l.get("http_method")))
                .thenComparing(l -> String.valueOf(l.get("status_code")))
                .thenComparing(l -> aliasNommes.getOrDefault(String.valueOf(l.get("entity_id")), String.valueOf(l.get("entity_id")))));
        return lignes;
    }
}
