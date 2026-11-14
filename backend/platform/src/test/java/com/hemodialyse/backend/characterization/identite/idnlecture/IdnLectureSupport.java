package com.hemodialyse.backend.characterization.identite.idnlecture;

import com.hemodialyse.backend.characterization.organisation.OrganisationCharacterizationSupport;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Filet IDN-lecture : les lectures de l'Organisation (centres et sociétés) faites par l'Identité et accès de la plateforme
 * (connexion, sélection société/centre, rattachements, comptes direction et administrateurs de société, amorçage de développement,
 * session de la direction du Pilotage). Au-dessus du harnais de l'Organisation (MockMvc, sécurité réelle, H2, aucun mock du code
 * caractérisé). Chaque scénario construit un journal ordonné (étape numérotée -> statut, type, corps) verrouillé par UN golden relisible.
 * <p>
 * Non-déterminisme : identifiants de fixtures par alias nommés, identifiants générés {@code <UUID_GENERE_n>}, horodatages
 * {@code <HORODATAGE>}. Aucun jeton, aucun hash, aucune date du jour n'est enregistré ; les mots de passe de test sont fictifs. Les
 * listes qui peuvent contenir des lignes d'autres tests de la même base ne sont enregistrées que filtrées sur les fixtures de ce filet.
 */
public abstract class IdnLectureSupport extends OrganisationCharacterizationSupport {

    /** Mot de passe fictif de tous les comptes de test (conforme à la politique : 12 caractères, lettres et chiffres). */
    protected static final String MDP = "Mdp-Fictif-Idn-2026";
    protected static final String PREFIXE_COMPTE = "idn-";

    @Autowired
    protected PasswordEncoder encodeur;

    /** Langage publié d'Organisation, injecté dans les objets construits à la main par les scénarios (IDN-lecture phase 2, ADR 0174). */
    @Autowired
    protected com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres annuaireDesCentres;

    @Autowired
    protected com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes annuaireDesSocietes;

    protected ObjectNode journal;
    private int etape;

    protected abstract String dossier();

    /** Dossier racine des goldens sous {@code characterization/identite} (le filet IDN-licence réutilise ce harnais). */
    protected String racineDesGoldens() {
        return "idn-lecture";
    }

    @Override
    protected final String theme() {
        return "../identite/" + racineDesGoldens() + "/" + dossier();
    }

    @BeforeEach
    void preparerIdn() {
        nettoyerIdn();
        journal = mapper.createObjectNode();
        etape = 0;
    }

    /** Exécuté avant le nettoyage du harnais parent : les comptes de test sont retirés avant leurs centres. */
    @AfterEach
    void nettoyerApresIdn() {
        nettoyerIdn();
    }

    protected void nettoyerIdn() {
        String comptes = "(SELECT id FROM app_user WHERE username LIKE '" + PREFIXE_COMPTE + "%')";
        jdbc.update("DELETE FROM auth_refresh_token WHERE user_id IN " + comptes);
        jdbc.update("DELETE FROM app_user_center WHERE user_id IN " + comptes);
        jdbc.update("DELETE FROM app_user_societe WHERE user_id IN " + comptes);
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN " + comptes);
        jdbc.update("DELETE FROM app_user WHERE username LIKE '" + PREFIXE_COMPTE + "%'");
    }

    // ------------------------------------------------------------------ fixtures

    protected UUID societeNommee(String alias, String code, String raisonSociale, boolean actif) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id, CODE_PREFIXE + code, raisonSociale, actif);
        return id;
    }

    protected UUID centreNomme(String alias, UUID societe, String code, String nom, boolean actif) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, ?, ?, ?, ?)",
                id, CODE_PREFIXE + code, nom, societe, actif);
        return id;
    }

    protected void role(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM app_role WHERE code = ?", Integer.class, code);
        if (n == null || n == 0) {
            jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", UUID.randomUUID(), code, code, code);
        }
    }

    protected UUID compte(String alias, String identifiant, boolean actif, String... roles) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,?)",
                id, PREFIXE_COMPTE + identifiant, encodeur.encode(MDP), null, "Compte " + alias, actif);
        for (String r : roles) {
            role(r);
            jdbc.update("INSERT INTO app_user_role (user_id, role_id) SELECT ?, id FROM app_role WHERE code = ?", id, r);
        }
        return id;
    }

    protected void rattacher(UUID compte, UUID centre) {
        jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, ?)", compte, centre);
    }

    protected void rattacherALaSociete(UUID compte, UUID societe) {
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", compte, societe);
    }

    // ------------------------------------------------------------------ sessions et requêtes

    /** Session de centre ou de direction (jeton déjà décodé) : le compte est celui de la fixture, le centre et la société ceux du jeton. */
    protected static RequestPostProcessor session(UUID compte, UUID centre, UUID societe, String... roles) {
        List<SimpleGrantedAuthority> droits = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        return user(new UserPrincipal(compte.toString(), centre == null ? null : centre.toString(), PREFIXE_COMPTE + "session", "", droits,
                true, societe == null ? null : societe.toString()));
    }

    /** Réponse HTTP d'une connexion ou d'un rafraîchissement, avec les cookies posés ({@code Resp} n'est pas constructible hors de son package). */
    protected record Connexion(int status, String contentType, String raw, Cookie acces, Cookie rafraichissement) {
    }

    private static Connexion lire(MockHttpServletResponse r) throws Exception {
        String ct = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Connexion(r.getStatus(), ct, r.getContentAsString(StandardCharsets.UTF_8), r.getCookie("HEMO_AUTH"),
                r.getCookie("HEMO_REFRESH"));
    }

    protected Connexion connecter(UUID societe, UUID centre, String identifiant, String motDePasse) throws Exception {
        ObjectNode corps = mapper.createObjectNode();
        if (societe != null) corps.put("societeId", societe.toString());
        if (centre != null) corps.put("centerId", centre.toString());
        corps.put("username", identifiant);
        corps.put("password", motDePasse);
        return lire(mvc.perform(post("/api/v1/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corps))).andReturn().getResponse());
    }

    protected Connexion rafraichir(Cookie rafraichissement) throws Exception {
        return lire(mvc.perform(post("/api/v1/auth/refresh").cookie(rafraichissement)).andReturn().getResponse());
    }

    // ------------------------------------------------------------------ journal

    protected void trace(String libelle, Resp r) {
        ObjectNode n = journal.putObject(String.format("%02d %s", ++etape, libelle));
        remplir(n, r.status(), r.contentType(), r.raw());
    }

    private void remplir(ObjectNode n, int status, String contentType, String raw) {
        n.put("status", status);
        n.put("contentType", contentType);
        if (raw == null || raw.isBlank()) {
            n.putNull("body");
        } else {
            try {
                n.set("body", trierLesCentres(mapper.readTree(raw)));
            } catch (RuntimeException e) {
                n.put("bodyText", raw);
            }
        }
    }

    /** Une connexion : la réponse et les NOMS des cookies posés (jamais leurs valeurs). */
    protected void trace(String libelle, Connexion c) {
        ObjectNode n = journal.putObject(String.format("%02d %s", ++etape, libelle));
        remplir(n, c.status(), c.contentType(), c.raw());
        ArrayNode cookies = n.putArray("cookiesPoses");
        if (c.acces() != null && !c.acces().getValue().isBlank()) cookies.add(c.acces().getName());
        if (c.rafraichissement() != null && !c.rafraichissement().getValue().isBlank()) cookies.add(c.rafraichissement().getName());
    }

    /** Une liste JSON dont seuls les éléments retenus par {@code garde} sont enregistrés (l'ordre de l'API est conservé). */
    protected void traceFiltre(String libelle, Resp r, Predicate<JsonNode> garde) {
        ObjectNode n = journal.putObject(String.format("%02d %s", ++etape, libelle));
        n.put("status", r.status());
        n.put("contentType", r.contentType());
        if (r.raw() == null || r.raw().isBlank()) {
            n.putNull("body");
            return;
        }
        JsonNode corps = mapper.readTree(r.raw());
        JsonNode liste = corps.isArray() ? corps : corps.path("items");
        if (!liste.isArray()) {
            n.set("body", corps);
            return;
        }
        ArrayNode retenus = n.putArray("elementsDeLaFixture");
        liste.forEach(e -> {
            if (garde.test(e)) retenus.add(trierLesCentres(e));
        });
    }

    /**
     * Les rattachements d'un compte ({@code centers}) sortent d'un ensemble ou d'une jointure SANS ordre garanti : ils sont triés par nom
     * pour la comparaison (l'ordre n'est pas un comportement observé). Les listes {@code centres} des comptes administrateurs
     * de société, elles, sont triées par l'API (nom du centre) et ne sont pas retouchées.
     */
    protected JsonNode trierLesCentres(JsonNode n) {
        if (n.isObject()) {
            ObjectNode o = (ObjectNode) n;
            for (Map.Entry<String, JsonNode> e : List.copyOf(o.properties())) {
                JsonNode v = trierLesCentres(e.getValue());
                if (("centers".equals(e.getKey()) || "roles".equals(e.getKey())) && v.isArray()) {
                    String cle = "centers".equals(e.getKey()) ? "name" : "code";
                    List<JsonNode> elements = new java.util.ArrayList<>();
                    v.forEach(elements::add);
                    elements.sort(java.util.Comparator.comparing((JsonNode x) -> x.path(cle).asString("")));
                    ArrayNode tries = mapper.createArrayNode();
                    elements.forEach(tries::add);
                    v = tries;
                }
                o.set(e.getKey(), v);
            }
        } else if (n.isArray()) {
            ArrayNode a = (ArrayNode) n;
            for (int i = 0; i < a.size(); i++) a.set(i, trierLesCentres(a.get(i)));
        }
        return n;
    }

    /** Les éléments {@code {id, ...}} dont l'identifiant est un alias de ce filet. */
    protected Predicate<JsonNode> identifiantsDeLaFixture() {
        return e -> aliasNommes.containsKey(e.path("id").asString(""));
    }

    /** Les comptes de ce filet (identifiant de connexion préfixé). */
    protected static Predicate<JsonNode> comptesDeLaFixture() {
        return e -> e.path("username").asString("").startsWith(PREFIXE_COMPTE);
    }

    protected void traceSql(String libelle, String sql, Object... args) {
        ArrayNode arr = journal.putObject(String.format("%02d %s", ++etape, libelle)).putArray("lignes");
        for (Map<String, Object> row : jdbc.queryForList(sql, args)) {
            ObjectNode o = arr.addObject();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> o.put(e.getKey().toLowerCase(), e.getValue() == null ? null : String.valueOf(e.getValue())));
        }
    }

    /** Ouvre une étape numérotée du journal (pour les filets qui construisent leur propre entrée, p. ex. IDN-licence). */
    protected ObjectNode nouvelleEtape(String libelle) {
        return journal.putObject(String.format("%02d %s", ++etape, libelle));
    }

    protected void traceNote(String libelle, String valeur) {
        journal.putObject(String.format("%02d %s", ++etape, libelle)).put("valeur", valeur);
    }

    protected void verrouiller(String nom) {
        goldenNode(nom, journal);
    }
}
