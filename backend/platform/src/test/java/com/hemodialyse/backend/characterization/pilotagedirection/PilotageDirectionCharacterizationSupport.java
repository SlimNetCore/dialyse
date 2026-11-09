package com.hemodialyse.backend.characterization.pilotagedirection;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Harnais des slices PD1 à PD4 du Pilotage de la direction : le LEGACY {@code platform/application/direction} servi par
 * {@code DirectionDashboardRestController} et {@code DirectionSnapshotRestController} ({@code /api/v1/direction/**}), ses tables
 * {@code direction_snapshot} et {@code direction_alert_history}, son temps réel ({@code DirectionRealtimeService}) et son rapport PDF.
 * Au-dessus des harnais de Planification (profils, requêtes, dates nommées, goldens), comme DM8 et DM9. Aucun mock du code
 * caractérisé, aucun seam, aucun code de production modifié ; le module parallèle gelé {@code contexts/pilotage-direction} n'est pas exercé.
 * <p>
 * Non-déterminisme : dates FIXES nommées {@code <D+n>} (2026-01-01 + n) pour toute l'activité ; mois RELATIFS nommés {@code <M-1>} (mois
 * écoulé au fuseau de la JVM, comme le legacy : {@code YearMonth.now()}) pour les instantanés, avec leurs bornes {@code <M-1.DEBUT>} et
 * {@code <M-1.FIN>} ; horodatages masqués {@code <HORODATAGE>} ; identifiants par alias. Les fixtures sont toutes antérieures au mois
 * écoulé (repère 2026-01-01, exécution à partir du 2026-11-09) : un instantané du mois écoulé ne contient jamais d'activité.
 * DONNÉES DE SANTÉ : fictives (« Patient &lt;alias&gt; », valeurs rondes).
 */
public abstract class PilotageDirectionCharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String DIRECTION = "/api/v1/direction";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    protected static final UUID AUTRE_SOCIETE = idStable("societe", "autre");
    protected static final UUID CENTRE_AUTRE = idStable("centre", "autre-societe");
    protected static final UUID SOCIETE_INACTIVE = idStable("societe", "inactive");
    protected static final UUID SOCIETE_SANS_CENTRE = idStable("societe", "sans-centre");
    protected static final UUID U_DIRECTION_AUTRE = idStable("user", "direction-autre");
    protected static final UUID U_DIRECTION_INACTIVE = idStable("user", "direction-inactive");
    protected static final UUID U_DIRECTION_DETACHEE = idStable("user", "direction-detachee");
    protected static final UUID U_DIRECTION_SOCIETE_INACTIVE = idStable("user", "direction-societe-inactive");
    protected static final UUID U_DIRECTION_SANS_CENTRE = idStable("user", "direction-sans-centre");
    private static final Pattern HEURE_FR = Pattern.compile("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");

    private final Map<UUID, Map<String, Object>> centresInitiaux = new LinkedHashMap<>();
    private final Map<String, String> moisNommes = new LinkedHashMap<>();
    private Map<String, Object> societeInitiale;
    private String scenario = "?";

    /** Sous-dossier de la slice ({@code pd1}…). */
    protected abstract String slice();

    /** Sous-domaine des goldens ({@code tableau_de_bord}, {@code instantanes}, {@code alertes}, {@code rapport}). */
    protected abstract String dossier();

    @Override
    protected final String theme() {
        return "../pilotagedirection/" + slice() + "/" + dossier() + "/" + scenario;
    }

    protected static UUID idStable(String famille, String alias) {
        return UUID.nameUUIDFromBytes(("pd-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ cycle

    @BeforeEach
    protected void capterLeScenarioPd(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpPd() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        moisNommes.clear();
        societesEtCentres();
        nettoyerPd();
    }

    @AfterEach
    protected void apresPd() {
        nettoyerPd();
        restaurerSocietesEtCentres();
    }

    private static void attendreSiProcheDeMinuitDeLaJvm() throws InterruptedException {
        ZoneId zone = ZoneId.systemDefault();
        Instant minuit = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        Duration reste = Duration.between(Instant.now(), minuit);
        if (reste.compareTo(Duration.ofMinutes(2)) < 0) {
            Thread.sleep(reste.toMillis() + 2000);
        }
    }

    /**
     * Société de test (identité complète pour l'en-tête des documents), deux centres rattachés (les lignes {@code centers} peuvent
     * préexister : rattachées puis rétablies), une autre société avec un centre, une société inactive, une société sans centre, et
     * une direction par société.
     */
    private void societesEtCentres() {
        for (UUID s : List.of(SOCIETE, AUTRE_SOCIETE, SOCIETE_INACTIVE, SOCIETE_SANS_CENTRE)) aliasNommes.put(s.toString(), "<" + nomSociete(s) + ">");
        aliasNommes.put(CENTRE_AUTRE.toString(), "<CENTRE_AUTRE_SOCIETE>");
        var existante = jdbc.queryForList("SELECT raison_sociale, actif, nif, adresse, ville, telephone, pied_page FROM societes WHERE id = ?", SOCIETE);
        societeInitiale = existante.isEmpty() ? null : existante.get(0);
        if (societeInitiale == null) {
            jdbc.update("INSERT INTO societes (id, code, raison_sociale, nif, adresse, ville, telephone, pied_page, actif, created_at) "
                    + "VALUES (?,?,?,?,?,?,?,?,TRUE,CURRENT_TIMESTAMP)", SOCIETE, "ZT-PD", "Clinique Pilotage & Fils", "099900000000001",
                    "12 rue des Tests", "Annaba", "038 00 00 00", "Pied de page personnalise PD");
        } else {
            jdbc.update("UPDATE societes SET raison_sociale = ?, actif = TRUE, nif = ?, adresse = ?, ville = ?, telephone = ?, pied_page = ? WHERE id = ?",
                    "Clinique Pilotage & Fils", "099900000000001", "12 rue des Tests", "Annaba", "038 00 00 00", "Pied de page personnalise PD", SOCIETE);
        }
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)", AUTRE_SOCIETE, "ZT-PD-AUTRE",
                "Autre Societe Confidentielle");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,FALSE,CURRENT_TIMESTAMP)", SOCIETE_INACTIVE,
                "ZT-PD-INACTIVE", "Societe Inactive");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)", SOCIETE_SANS_CENTRE,
                "ZT-PD-VIDE", "Societe Sans Centre");
        centresInitiaux.clear();
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            String nom = centre.equals(CENTRE_A) ? "Centre A" : "Centre B";
            var existants = jdbc.queryForList("SELECT code, name, societe_id, actif FROM centers WHERE id = ?", centre);
            if (existants.isEmpty()) {
                jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", centre,
                        centre.equals(CENTRE_A) ? "ZT-PD-A" : "ZT-PD-B", nom, SOCIETE);
            } else {
                centresInitiaux.put(centre, existants.get(0));
                jdbc.update("UPDATE centers SET societe_id = ?, name = ?, actif = TRUE WHERE id = ?", SOCIETE, nom, centre);
            }
        }
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE_AUTRE, "ZT-PD-X", "Centre Confidentiel", AUTRE_SOCIETE);
        utilisateur(U_ADMIN, "zt-pd-direction", true, SOCIETE);
        utilisateur(U_DIRECTION_AUTRE, "zt-pd-direction-autre", true, AUTRE_SOCIETE);
        utilisateur(U_DIRECTION_INACTIVE, "zt-pd-direction-inactive", false, SOCIETE);
        utilisateur(U_DIRECTION_DETACHEE, "zt-pd-direction-detachee", true, null);
        utilisateur(U_DIRECTION_SOCIETE_INACTIVE, "zt-pd-direction-soc-inactive", true, SOCIETE_INACTIVE);
        utilisateur(U_DIRECTION_SANS_CENTRE, "zt-pd-direction-sans-centre", true, SOCIETE_SANS_CENTRE);
    }

    private static String nomSociete(UUID s) {
        if (s.equals(SOCIETE)) return "SOCIETE";
        if (s.equals(AUTRE_SOCIETE)) return "AUTRE_SOCIETE";
        if (s.equals(SOCIETE_INACTIVE)) return "SOCIETE_INACTIVE";
        return "SOCIETE_SANS_CENTRE";
    }

    private void utilisateur(UUID id, String login, boolean actif, UUID societe) {
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", id);
        jdbc.update("DELETE FROM app_user WHERE id = ?", id);
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,?)", id, login, "x", null, "Direction PD", actif);
        if (societe != null) jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", id, societe);
    }

    private void restaurerSocietesEtCentres() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            Map<String, Object> avant = centresInitiaux.get(centre);
            if (avant == null) {
                jdbc.update("DELETE FROM centers WHERE id = ?", centre);
            } else {
                jdbc.update("UPDATE centers SET societe_id = ?, name = ?, actif = ? WHERE id = ?", avant.get("SOCIETE_ID"), avant.get("NAME"), avant.get("ACTIF"), centre);
            }
        }
        jdbc.update("DELETE FROM centers WHERE id = ?", CENTRE_AUTRE);
        for (UUID u : List.of(U_ADMIN, U_DIRECTION_AUTRE, U_DIRECTION_INACTIVE, U_DIRECTION_DETACHEE, U_DIRECTION_SOCIETE_INACTIVE, U_DIRECTION_SANS_CENTRE)) {
            jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", u);
            jdbc.update("DELETE FROM app_user WHERE id = ?", u);
        }
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-PD%'");
        if (societeInitiale == null) {
            jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
        } else {
            jdbc.update("UPDATE societes SET raison_sociale = ?, actif = ?, nif = ?, adresse = ?, ville = ?, telephone = ?, pied_page = ? WHERE id = ?",
                    societeInitiale.get("RAISON_SOCIALE"), societeInitiale.get("ACTIF"), societeInitiale.get("NIF"), societeInitiale.get("ADRESSE"),
                    societeInitiale.get("VILLE"), societeInitiale.get("TELEPHONE"), societeInitiale.get("PIED_PAGE"), SOCIETE);
        }
        jdbc.update("DELETE FROM societes WHERE id IN (?, ?, ?)", AUTRE_SOCIETE, SOCIETE_INACTIVE, SOCIETE_SANS_CENTRE);
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-PD%'");
    }

    protected void nettoyerPd() {
        jdbc.update("DELETE FROM direction_snapshot WHERE societe_id IN (?, ?, ?, ?)", SOCIETE, AUTRE_SOCIETE, SOCIETE_INACTIVE, SOCIETE_SANS_CENTRE);
        jdbc.update("DELETE FROM direction_alert_history WHERE societe_id IN (?, ?, ?, ?)", SOCIETE, AUTRE_SOCIETE, SOCIETE_INACTIVE, SOCIETE_SANS_CENTRE);
        for (UUID centre : List.of(CENTRE_A, CENTRE_B, CENTRE_AUTRE)) {
            for (String table : List.of("facture_reglements", "factures", "seances", "administrations_anemie", "lots", "articles", "resultats_analyses",
                    "alertes_observance", "serologies_patient", "bilans_pre_greffe", "patients", "centre_payeur", "agence", "caisse_assurance")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    // ------------------------------------------------------------------ profils

    /** Direction d'une société donnée (jeton sans centre, société portée par le jeton), compte {@code user}. */
    protected static RequestPostProcessor directionDe(UUID utilisateur, UUID societe) {
        return user(new UserPrincipal(utilisateur.toString(), null, "u-direction-" + utilisateur.toString().substring(0, 8), "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, societe == null ? null : societe.toString()));
    }

    protected static RequestPostProcessor directionAutre() {
        return directionDe(U_DIRECTION_AUTRE, AUTRE_SOCIETE);
    }

    // ------------------------------------------------------------------ dates et mois

    protected LocalDate fixe(int n) {
        return alias(REPERE.plusDays(n), "<D" + (n < 0 ? n : "+" + n) + ">");
    }

    /** Mois relatif au mois courant de la JVM ({@code <M-1>} = mois écoulé), avec ses bornes nommées {@code <M-1.DEBUT>}, {@code <M-1.FIN>}. */
    protected YearMonth mois(int decalage) {
        YearMonth m = YearMonth.now().plusMonths(decalage);
        String alias = "<M" + (decalage < 0 ? decalage : "+" + decalage) + ">";
        moisNommes.put(m.toString(), alias);
        alias(m.atDay(1), alias.replace(">", ".DEBUT>"));
        alias(m.atEndOfMonth(), alias.replace(">", ".FIN>"));
        return m;
    }

    /** Nomme la période de même durée précédant immédiatement le mois (comparaison de la vue d'ensemble). */
    protected void nommerLaPeriodePrecedenteDe(YearMonth m, String alias) {
        LocalDate veille = m.atDay(1).minusDays(1);
        alias(veille, alias.replace(">", ".FIN>"));
        alias(veille.minusDays(m.lengthOfMonth() - 1), alias.replace(">", ".DEBUT>"));
    }

    private String remplacerMois(String texte) {
        String r = texte;
        for (Map.Entry<String, String> e : moisNommes.entrySet()) {
            r = r.replaceAll("(?<![0-9a-fA-F])" + Pattern.quote(e.getKey()) + "(?![0-9a-fA-F-])", e.getValue());
        }
        return r;
    }

    /** Golden d'un arbre : dates nommées (toute date réelle restante fait échouer), mois nommés, horodatages masqués, alias d'identifiants. */
    protected void arbrePd(String name, Object valeur) {
        String json = mapper.writeValueAsString(mapper.valueToTree(valeur));
        goldenNode(name, mapper.readTree(remplacerMois(dates(json))));
    }

    /** Réponse HTTP complète (statut, type, cache, corps) prête pour {@link #arbrePd}. */
    protected Map<String, Object> reponse(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        m.put("contentType", r.contentType());
        m.put("cacheControl", r.cacheControl());
        m.put("body", r.raw() == null || r.raw().isBlank() ? null : mapper.readTree(r.raw()));
        return m;
    }

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

    /** Lignes SQL normalisées (clés en minuscules, valeurs en texte) : identifiants et horodatages seront masqués par le golden. */
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

    protected Resp direction(String chemin) throws Exception {
        return doGet(DIRECTION + chemin, direction());
    }

    protected MockHttpServletResponse binaire(String path, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse();
    }

    /** Texte d'un PDF (espaces normalisés), heures françaises {@code dd/MM/yyyy HH:mm} masquées ; jamais les octets. */
    protected String texte(byte[] pdf) throws Exception {
        if (pdf.length < 4 || !"%PDF".equals(new String(pdf, 0, 4, StandardCharsets.US_ASCII))) return "<pas un PDF : " + pdf.length + " octets>";
        try (PDDocument document = PDDocument.load(pdf)) {
            String brut = new PDFTextStripper().getText(document).replaceAll("\\s+", " ").trim();
            return HEURE_FR.matcher(brut).replaceAll("<HORODATAGE>");
        }
    }

    /** Découpe le texte du rapport par section (titres du legacy), pour un golden relisible. */
    protected Map<String, String> sections(String texte) {
        List<String> titres = List.of("Alertes", "Finances et activité par centre", "Évolution mensuelle", "Qualité des soins", "Stock",
                "Répartition des patients par sexe et par âge", "Caisses d'assurance par centre", "Traitement de l'anémie par centre",
                "Les effectifs de moins de");
        Map<String, String> m = new LinkedHashMap<>();
        int courant = 0;
        String titreCourant = "en-tete";
        for (String titre : titres) {
            int i = texte.indexOf(titre, courant);
            if (i < 0) continue;
            m.put(titreCourant, texte.substring(courant, i).trim());
            titreCourant = titre;
            courant = i;
        }
        m.put(titreCourant, texte.substring(courant).trim());
        return m;
    }

    // ------------------------------------------------------------------ fixtures

    protected UUID patientPd(UUID centre, String alias) {
        return patientPd(centre, alias, "M", LocalDate.of(1960, 5, 5), false, false, false, null);
    }

    /** Patient fictif admis bien avant le repère ; état PERMANENT ; sexe, naissance, KT, EPO/fer prescrits et centre payeur optionnels. */
    protected UUID patientPd(UUID centre, String alias, String sexe, LocalDate naissance, boolean sousKt, boolean epo, boolean fer, UUID payeur) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, numero_assurance, type_patient, "
                        + "etat_patient, sous_kt, epo_enabled, fer_enabled, centre_payeur_id, created_at) VALUES (?,?,?,?,?,?,?,?,?,'NON_VACANCIER','PERMANENT',?,?,?,?,?)",
                id, centre, "PD-" + alias, "Patient " + alias, "Test", sexe, naissance == null ? null : java.sql.Date.valueOf(naissance),
                java.sql.Date.valueOf(REPERE.minusDays(400)), "ASS-PD-" + alias, sousKt, epo, fer, payeur, CREATION_FIXE);
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom)).ifPresent(org.springframework.cache.Cache::clear));
        return id;
    }

    protected void sortie(UUID patient, String etat, LocalDate evenement) {
        jdbc.update("UPDATE patients SET etat_patient = ?, date_evenement_etat = ? WHERE id = ?", etat, java.sql.Date.valueOf(evenement), patient);
    }

    protected void admission(UUID patient, LocalDate jour) {
        jdbc.update("UPDATE patients SET date_admission = ? WHERE id = ?", java.sql.Date.valueOf(jour), patient);
    }

    /** Chaîne caisse → agence → centre payeur du centre ; rend [caisse, agence, payeur]. */
    protected UUID[] caisse(UUID centre, String code, String nom) {
        UUID caisse = idStable("caisse", code + "-" + centre);
        UUID agence = idStable("agence", code + "-" + centre);
        UUID payeur = idStable("payeur", code + "-" + centre);
        aliasNommes.put(caisse.toString(), "<CAISSE_" + code + ">");
        aliasNommes.put(agence.toString(), "<AGENCE_" + code + ">");
        aliasNommes.put(payeur.toString(), "<PAYEUR_" + code + ">");
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?,?,?,?)", caisse, centre, code, nom);
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?,?,?,?,?)", agence, centre, caisse, "AG-" + code, "Agence " + code);
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?,?,?,?,?)", payeur, centre, agence, "CP-" + code, "Payeur " + code);
        return new UUID[]{caisse, agence, payeur};
    }

    protected UUID seancePd(UUID centre, UUID patient, String alias, LocalDate jour, String statut) {
        UUID id = idStable("seance", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning) VALUES (?,?,?,?,?,?,FALSE)",
                id, patient, centre, java.sql.Date.valueOf(jour), statut, CREATION_FIXE);
        return id;
    }

    protected UUID facturePd(UUID centre, UUID patient, String alias, LocalDate jour, String ht, String ttc, UUID agence) {
        UUID id = idStable("facture", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        BigDecimal h = new BigDecimal(ht);
        BigDecimal t = new BigDecimal(ttc);
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, "
                        + "total_ttc, agence_id_snapshot) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", id, centre, patient, "PD-" + alias, java.sql.Date.valueOf(jour),
                java.sql.Date.valueOf(jour), java.sql.Date.valueOf(jour), new BigDecimal("19.00"), h, t.subtract(h), t, agence);
        return id;
    }

    protected void reglement(UUID centre, UUID facture, String alias, LocalDate jour, String montant) {
        UUID id = idStable("reglement", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement) VALUES (?,?,?,?,?)", id, facture, centre,
                new BigDecimal(montant), java.sql.Date.valueOf(jour));
    }

    protected void administration(UUID centre, UUID patient, String alias, String type, boolean administree, LocalDate jour) {
        UUID id = idStable("administration", alias + "-" + centre);
        jdbc.update("INSERT INTO administrations_anemie (id, patient_id, center_id, type_traitement, date_administration, administree, created_at) "
                + "VALUES (?,?,?,?,?,?,?)", id, patient, centre, type, java.sql.Date.valueOf(jour), administree, CREATION_FIXE);
    }

    protected UUID article(UUID centre, String alias, String stock, String seuil, String pmp) {
        UUID id = idStable("article", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot, active) "
                + "VALUES (?,?,?,?,?,?,?,?,TRUE,TRUE)", id, centre, "PD-" + alias, "Article " + alias, "U", new BigDecimal(stock), new BigDecimal(seuil),
                pmp == null ? null : new BigDecimal(pmp));
        return id;
    }

    protected void lot(UUID centre, UUID article, String alias, LocalDate peremption, String restante) {
        UUID id = idStable("lot", alias + "-" + centre);
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante) VALUES (?,?,?,?,?,?,?)",
                id, centre, article, "L-" + alias, peremption == null ? null : java.sql.Date.valueOf(peremption), new BigDecimal("10"), new BigDecimal(restante));
    }

    protected UUID analyseEnBase(UUID centre, UUID patient, String alias, LocalDate date, String... colonnesEtValeurs) {
        UUID id = idStable("analyse", alias + "-" + centre);
        StringBuilder cols = new StringBuilder("id, patient_id, center_id, date_prelevement, created_at, updated_at");
        StringBuilder marques = new StringBuilder("?,?,?,?,?,?");
        List<Object> args = new ArrayList<>(List.of(id, patient, centre, java.sql.Date.valueOf(date), CREATION_FIXE, CREATION_FIXE));
        for (int i = 0; i < colonnesEtValeurs.length; i += 2) {
            cols.append(", ").append(colonnesEtValeurs[i]);
            marques.append(",?");
            args.add(new BigDecimal(colonnesEtValeurs[i + 1]));
        }
        jdbc.update("INSERT INTO resultats_analyses (" + cols + ") VALUES (" + marques + ")", args.toArray());
        return id;
    }

    protected void alerteObservance(UUID centre, UUID patient, String alias, String type, boolean resolue) {
        UUID id = idStable("observance", alias + "-" + centre);
        jdbc.update("INSERT INTO alertes_observance (id, patient_id, center_id, type_traitement, type_alerte, periode_debut, periode_fin, doses_attendues, "
                        + "doses_administrees, message, unite_dose, created_at, resolved_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", id, patient, centre, "EPO", type,
                java.sql.Date.valueOf(REPERE), java.sql.Date.valueOf(REPERE.plusDays(30)), 4, 1, "retard", "UI", CREATION_FIXE, resolue ? CREATION_FIXE : null);
    }

    protected void alerteEnBase(UUID societe, UUID centre, String centreNom, String code, String severity, String valeur, OffsetDateTime premiere,
                                OffsetDateTime resolue) {
        jdbc.update("INSERT INTO direction_alert_history (id, societe_id, center_id, centre_nom, code, severity, valeur, first_seen_at, resolved_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), societe, centre, centreNom, code, severity, valeur == null ? null : new BigDecimal(valeur),
                premiere, resolue);
    }

    /**
     * Jeu complet d'une société à deux centres sur la période {@code <D+0>..<D+100>} : effectifs (A publiable, B masqué), séances, factures et
     * règlements (dont un hors période), caisses, anémie. Partagé par la vue d'ensemble, la répartition et le rapport.
     */
    protected void jeuComplet() {
        UUID[] cnas = caisse(CENTRE_A, "CNAS", "CNAS Nationale");
        UUID[] casnos = caisse(CENTRE_A, "CASNOS", "CASNOS Independants");
        List<UUID> a = new ArrayList<>();
        a.add(patientPd(CENTRE_A, "A1", "M", LocalDate.of(1960, 6, 15), true, true, false, cnas[2]));
        a.add(patientPd(CENTRE_A, "A2", "M", LocalDate.of(1990, 6, 15), true, true, false, cnas[2]));
        a.add(patientPd(CENTRE_A, "A3", "F", LocalDate.of(2015, 6, 15), false, true, true, cnas[2]));
        a.add(patientPd(CENTRE_A, "A4", "f ", null, false, true, false, cnas[2]));
        a.add(patientPd(CENTRE_A, "A5", "", LocalDate.of(2100, 1, 1), false, true, false, cnas[2]));
        a.add(patientPd(CENTRE_A, "A6", "X", LocalDate.of(1975, 6, 15), false, true, false, null));
        a.add(patientPd(CENTRE_A, "A7_SORTI_PENDANT", "F", LocalDate.of(1975, 6, 15), false, false, false, null));
        sortie(a.get(6), "TRANSFERE", fixe(50));
        UUID avant = patientPd(CENTRE_A, "A8_SORTI_AVANT", "M", LocalDate.of(1975, 6, 15), false, false, false, null);
        sortie(avant, "DECEDE", fixe(-10));
        UUID apres = patientPd(CENTRE_A, "A9_ADMIS_APRES", "M", LocalDate.of(1975, 6, 15), false, false, false, null);
        admission(apres, fixe(200));
        List<UUID> b = new ArrayList<>();
        for (int i = 1; i <= 3; i++) b.add(patientPd(CENTRE_B, "B" + i));

        seancePd(CENTRE_A, a.get(0), "S_A1", fixe(10), "VALIDEE");
        seancePd(CENTRE_A, a.get(1), "S_A2", fixe(10), "VALIDEE");
        seancePd(CENTRE_A, a.get(2), "S_A3", fixe(40), "SIGNEE");
        seancePd(CENTRE_A, a.get(5), "S_A6", fixe(70), "FACTUREE");
        seancePd(CENTRE_A, a.get(5), "S_A6_CREEE", fixe(10), "CREE");
        seancePd(CENTRE_A, a.get(0), "S_A1_AVANT", fixe(-5), "VALIDEE");
        seancePd(CENTRE_B, b.get(0), "S_B1", fixe(20), "VALIDEE");
        seancePd(CENTRE_B, a.get(0), "S_B_FICHE_A", fixe(20), "VALIDEE");

        UUID f1 = facturePd(CENTRE_A, a.get(0), "F1", fixe(10), "1000.00", "1190.00", cnas[1]);
        reglement(CENTRE_A, f1, "R1", fixe(15), "600.00");
        reglement(CENTRE_A, f1, "R1_APRES_PERIODE", fixe(200), "200.00");
        facturePd(CENTRE_A, a.get(1), "F2", fixe(70), "500.00", "595.00", cnas[1]);
        facturePd(CENTRE_A, a.get(5), "F3_SANS_CAISSE", fixe(70), "200.00", "238.00", null);
        facturePd(CENTRE_A, a.get(5), "F4_CASNOS", fixe(75), "50.00", "59.50", casnos[1]);
        UUID f0 = facturePd(CENTRE_A, a.get(0), "F0_AVANT", fixe(-20), "300.00", "357.00", cnas[1]);
        reglement(CENTRE_A, f0, "R0_AVANT", fixe(-20), "357.00");

        for (int i = 0; i < 5; i++) administration(CENTRE_A, a.get(i), "EPO_" + i, "EPO", true, fixe(30));
        administration(CENTRE_A, a.get(2), "FER_1", "FER_INJECTABLE", true, fixe(30));
        administration(CENTRE_A, a.get(2), "FER_2", "FER_INJECTABLE", true, fixe(31));
        administration(CENTRE_A, a.get(5), "EPO_NON", "EPO", false, fixe(30));
    }
}
