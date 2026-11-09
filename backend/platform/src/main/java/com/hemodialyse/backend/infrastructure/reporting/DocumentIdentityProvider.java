package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee.IdentiteDeLaSociete;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee.IdentiteDuCentre;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fournit l'identité de la société et du centre (raison sociale, logo, adresse, téléphone, email, site web,
 * mentions légales, pied de page) sous forme de paramètres Jasper, pour l'en-tête et le pied de page de tous les
 * documents imprimés.
 * <p>
 * Ces paramètres sont posés <b>par le serveur</b>, à partir du centre de l'impression : un paramètre de même nom
 * envoyé par le client est ignoré (voir {@link #stripReservedParams}), sinon un utilisateur pourrait imprimer un
 * document sous l'identité d'une autre société. Toutes les chaînes sont non nulles (vides si absentes) ; seul le
 * logo peut être {@code null}.
 */
@Component
public class DocumentIdentityProvider {

    public static final String RESERVED_PREFIX_SOCIETE = "SOCIETE_";
    public static final String RESERVED_PREFIX_CENTRE = "CENTRE_";

    private final IdentiteImprimee identiteImprimee;

    /** Les données d'identité sont lues par le langage publié d'Organisation (O4 lot E, ADR 0111) ; la mise en forme reste ici. */
    public DocumentIdentityProvider(IdentiteImprimee identiteImprimee) {
        this.identiteImprimee = identiteImprimee;
    }

    private static String line(Map<String, Object> p, String key, String style) {
        String value = esc(p.get(key));
        return value.isEmpty() ? "" : "<div style='" + style + "'>" + value + "</div>";
    }

    private static String logoImg(Object logo) {
        if (!(logo instanceof InputStream in)) return "";
        try {
            byte[] bytes = in.readAllBytes();
            String type = bytes.length > 3 && bytes[0] == (byte) 0xFF ? "image/jpeg" : "image/png";
            return "<img style='max-width:64px;max-height:52px' src='data:" + type + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(bytes) + "'/>";
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static String esc(Object value) {
        if (value == null) return "";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Retire des paramètres du client tout nom réservé à l'identité (SOCIETE_*, CENTRE_*).
     */
    public static Map<String, String> stripReservedParams(Map<String, String> clientParams) {
        Map<String, String> safe = new HashMap<>();
        clientParams.forEach((k, v) -> {
            String key = k == null ? "" : k.toUpperCase();
            if (!key.startsWith(RESERVED_PREFIX_SOCIETE) && !key.startsWith(RESERVED_PREFIX_CENTRE)) {
                safe.put(k, v);
            }
        });
        return safe;
    }

    private static Map<String, Object> blank() {
        Map<String, Object> params = new HashMap<>();
        List.of("SOCIETE_NOM", "SOCIETE_ADRESSE", "SOCIETE_TELEPHONE", "SOCIETE_EMAIL", "SOCIETE_SITE_WEB",
                        "SOCIETE_CONTACT", "SOCIETE_LEGAL", "SOCIETE_PIED_PAGE", "CENTRE_NOM", "CENTRE_ADRESSE",
                        "CENTRE_TELEPHONE", "CENTRE_EMAIL", "CENTRE_SITE_WEB", "CENTRE_CONTACT", "CENTRE_LIGNE")
                .forEach(k -> params.put(k, ""));
        params.put("SOCIETE_LOGO", null);
        return params;
    }

    private static String place(String ville, String wilaya) {
        return ville.equalsIgnoreCase(wilaya) ? ville : join(" – ", ville, wilaya);
    }

    private static String label(String name, String value) {
        return value.isEmpty() ? "" : name + " : " + value;
    }

    private static String contact(String tel, String email, String web) {
        return join(" · ", tel.isEmpty() ? "" : "Tél : " + tel, email.isEmpty() ? "" : "Email : " + email,
                web.isEmpty() ? "" : "Web : " + web);
    }

    private static String join(String separator, String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(separator));
    }

    /**
     * Paramètres Jasper d'identité pour le centre donné (jamais {@code null}).
     */
    public Map<String, Object> paramsFor(UUID centerId) {
        Map<String, Object> params = blank();
        if (centerId == null) return params;

        Optional<IdentiteDuCentre> lue = identiteImprimee.duCentre(centerId);
        if (lue.isEmpty()) return params;
        IdentiteDuCentre c = lue.get();
        IdentiteDeLaSociete s = c.societe();

        String centreAdresse = join(", ", c.adresse(), place(c.ville(), c.wilaya()));
        String centreContact = contact(c.telephone(), c.email(), c.siteWeb());
        String societeAdresse = join(", ", s.adresse(), place(s.ville(), s.wilaya()));
        String societeContact = join(" · ", societeAdresse, contact(s.telephone(), s.email(), s.siteWeb()));
        String centreNom = c.nom();

        params.put("CENTRE_NOM", centreNom);
        params.put("CENTRE_ADRESSE", centreAdresse);
        params.put("CENTRE_TELEPHONE", c.telephone());
        params.put("CENTRE_EMAIL", c.email());
        params.put("CENTRE_SITE_WEB", c.siteWeb());
        params.put("CENTRE_CONTACT", centreContact);
        params.put("CENTRE_LIGNE", join(" · ", centreNom, centreAdresse, centreContact));

        params.put("SOCIETE_NOM", s.raisonSociale());
        params.put("SOCIETE_ADRESSE", societeAdresse);
        params.put("SOCIETE_TELEPHONE", s.telephone());
        params.put("SOCIETE_EMAIL", s.email());
        params.put("SOCIETE_SITE_WEB", s.siteWeb());
        params.put("SOCIETE_CONTACT", societeContact);
        params.put("SOCIETE_LEGAL", legal(s));
        params.put("SOCIETE_PIED_PAGE", s.piedDePage());
        if (s.logo() != null) {
            // Un flux neuf à chaque impression : Jasper le consomme.
            params.put("SOCIETE_LOGO", new ByteArrayInputStream(s.logo()));
        }
        return params;
    }

    private static String legal(IdentiteDeLaSociete s) {
        return Stream.of(label("NIF", s.nif()), label("NIS", s.nis()), label("RC", s.rc()))
                .filter(x -> !x.isEmpty()).collect(Collectors.joining(" · "));
    }

    /**
     * Identité de la seule société (documents de direction, sans centre) : raison sociale, ligne de contact, mentions
     * légales, pied de page et logo. Vide si la société est inconnue.
     */
    public Optional<SocieteIdentity> societeIdentity(UUID societeId) {
        if (societeId == null) return Optional.empty();
        return identiteImprimee.deLaSociete(societeId).map(s -> {
            String adresse = join(", ", s.adresse(), place(s.ville(), s.wilaya()));
            String contact = join(" · ", adresse, contact(s.telephone(), s.email(), s.siteWeb()));
            return new SocieteIdentity(s.raisonSociale(), contact, legal(s), s.piedDePage(), s.logo());
        });
    }

    /**
     * Identité d'une société pour l'en-tête et le pied de page d'un document ({@code logo} peut être nul).
     */
    public record SocieteIdentity(String nom, String contact, String legal, String piedPage, byte[] logo) {
    }

    /**
     * Ajoute l'en-tête (logo, société, centre) et le pied de page d'identité à un document HTML destiné à être
     * converti en PDF (documents qui ne passent pas par Jasper : dossier de greffe, statistiques patient…).
     * Tout le contenu est échappé ; le logo est intégré en {@code data:} URI (jamais d'URL externe).
     */
    public String decorateHtml(String html, UUID centerId) {
        Map<String, Object> p = paramsFor(centerId);
        String header = "<table style='width:100%;border-collapse:collapse;margin-bottom:6px;border:0'><tr>"
                + "<td style='width:70px;vertical-align:top;border:0;padding:0'>" + logoImg(p.get("SOCIETE_LOGO")) + "</td>"
                + "<td style='vertical-align:top;border:0;padding:0 8px;font-size:9px;color:#333'>"
                + line(p, "SOCIETE_NOM", "font-size:13px;font-weight:bold;color:#111")
                + line(p, "SOCIETE_CONTACT", "") + line(p, "SOCIETE_LEGAL", "") + "</td>"
                + "<td style='width:38%;vertical-align:top;text-align:right;border:0;padding:0;font-size:9px;color:#333'>"
                + line(p, "CENTRE_NOM", "font-size:11px;font-weight:bold;color:#111")
                + line(p, "CENTRE_ADRESSE", "") + line(p, "CENTRE_CONTACT", "") + "</td></tr></table>"
                + "<hr style='border:0;border-top:1px solid #111;margin:0 0 10px 0'/>";
        String footer = "<hr style='border:0;border-top:1px solid #111;margin:14px 0 4px 0'/>"
                + "<div style='text-align:center;font-size:8px;color:#444'>" + esc(p.get("CENTRE_LIGNE"))
                + "<br/>" + esc(p.get("SOCIETE_PIED_PAGE")) + "</div>";
        int body = html.indexOf("<body>");
        int end = html.lastIndexOf("</body>");
        if (body < 0 || end < 0) return html;
        return html.substring(0, body + 6) + header + html.substring(body + 6, end) + footer + html.substring(end);
    }
}
