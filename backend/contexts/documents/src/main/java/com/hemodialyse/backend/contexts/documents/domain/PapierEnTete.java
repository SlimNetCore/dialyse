package com.hemodialyse.backend.contexts.documents.domain;

import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable.Societe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Le papier en-tête : composition de l'identité imprimée du centre et de sa société en paramètres de gabarit, en en-tête de société seule
 * (documents de direction), et en habillage d'un document HTML (en-tête, pied de page). Reprise à l'identique de
 * {@code DocumentIdentityProvider} (DOC1) : aucune règle nouvelle, y compris les défauts d'affichage consignés (KB-DOC1-5 f : un
 * {@code <body>} qui porte un attribut n'est pas reconnu).
 */
public final class PapierEnTete {

    private PapierEnTete() {
    }

    /** En-tête d'une société seule : raison sociale, ligne de contact, mentions légales, pied de page, logo ({@code null} si absent). */
    public record EnTeteDeSociete(String nom, String contact, String legal, String piedDePage, byte[] logo) {
    }

    /** Paramètres de gabarit d'identité pour ce centre ; textes vides et logo nul si le centre est inconnu ({@code null}). Jamais nul. */
    public static Map<String, Object> parametres(IdentiteImprimable c) {
        Map<String, Object> params = vides();
        if (c == null) return params;
        Societe s = c.societe();

        String centreAdresse = joindre(", ", c.adresse(), lieu(c.ville(), c.wilaya()));
        String centreContact = contact(c.telephone(), c.email(), c.siteWeb());
        String societeAdresse = joindre(", ", s.adresse(), lieu(s.ville(), s.wilaya()));
        String societeContact = joindre(" · ", societeAdresse, contact(s.telephone(), s.email(), s.siteWeb()));

        params.put("CENTRE_NOM", c.nom());
        params.put("CENTRE_ADRESSE", centreAdresse);
        params.put("CENTRE_TELEPHONE", c.telephone());
        params.put("CENTRE_EMAIL", c.email());
        params.put("CENTRE_SITE_WEB", c.siteWeb());
        params.put("CENTRE_CONTACT", centreContact);
        params.put("CENTRE_LIGNE", joindre(" · ", c.nom(), centreAdresse, centreContact));

        params.put("SOCIETE_NOM", s.raisonSociale());
        params.put("SOCIETE_ADRESSE", societeAdresse);
        params.put("SOCIETE_TELEPHONE", s.telephone());
        params.put("SOCIETE_EMAIL", s.email());
        params.put("SOCIETE_SITE_WEB", s.siteWeb());
        params.put("SOCIETE_CONTACT", societeContact);
        params.put("SOCIETE_LEGAL", mentionsLegales(s));
        params.put("SOCIETE_PIED_PAGE", s.piedDePage());
        if (s.logo() != null) {
            // Un flux neuf à chaque composition : le moteur le consomme.
            params.put("SOCIETE_LOGO", new ByteArrayInputStream(s.logo()));
        }
        return params;
    }

    /** L'en-tête d'une société seule ; vide si la société est inconnue ({@code null}). */
    public static Optional<EnTeteDeSociete> enTeteDeLaSociete(Societe s) {
        if (s == null) return Optional.empty();
        String adresse = joindre(", ", s.adresse(), lieu(s.ville(), s.wilaya()));
        String contact = joindre(" · ", adresse, contact(s.telephone(), s.email(), s.siteWeb()));
        return Optional.of(new EnTeteDeSociete(s.raisonSociale(), contact, mentionsLegales(s), s.piedDePage(), s.logo()));
    }

    /**
     * Ajoute l'en-tête (logo, société, centre) et le pied de page d'identité à un document HTML destiné à être converti en PDF. Tout le
     * contenu est échappé ; le logo est intégré en {@code data:} URI (jamais d'URL externe). Rendu tel quel sans {@code <body>} nu.
     */
    public static String habiller(String html, Map<String, Object> p) {
        String entete = "<table style='width:100%;border-collapse:collapse;margin-bottom:6px;border:0'><tr>"
                + "<td style='width:70px;vertical-align:top;border:0;padding:0'>" + image(p.get("SOCIETE_LOGO")) + "</td>"
                + "<td style='vertical-align:top;border:0;padding:0 8px;font-size:9px;color:#333'>"
                + ligne(p, "SOCIETE_NOM", "font-size:13px;font-weight:bold;color:#111")
                + ligne(p, "SOCIETE_CONTACT", "") + ligne(p, "SOCIETE_LEGAL", "") + "</td>"
                + "<td style='width:38%;vertical-align:top;text-align:right;border:0;padding:0;font-size:9px;color:#333'>"
                + ligne(p, "CENTRE_NOM", "font-size:11px;font-weight:bold;color:#111")
                + ligne(p, "CENTRE_ADRESSE", "") + ligne(p, "CENTRE_CONTACT", "") + "</td></tr></table>"
                + "<hr style='border:0;border-top:1px solid #111;margin:0 0 10px 0'/>";
        String pied = "<hr style='border:0;border-top:1px solid #111;margin:14px 0 4px 0'/>"
                + "<div style='text-align:center;font-size:8px;color:#444'>" + echapper(p.get("CENTRE_LIGNE"))
                + "<br/>" + echapper(p.get("SOCIETE_PIED_PAGE")) + "</div>";
        int corps = html.indexOf("<body>");
        int fin = html.lastIndexOf("</body>");
        if (corps < 0 || fin < 0) return html;
        return html.substring(0, corps + 6) + entete + html.substring(corps + 6, fin) + pied + html.substring(fin);
    }

    private static Map<String, Object> vides() {
        Map<String, Object> params = new HashMap<>();
        List.of("SOCIETE_NOM", "SOCIETE_ADRESSE", "SOCIETE_TELEPHONE", "SOCIETE_EMAIL", "SOCIETE_SITE_WEB",
                        "SOCIETE_CONTACT", "SOCIETE_LEGAL", "SOCIETE_PIED_PAGE", "CENTRE_NOM", "CENTRE_ADRESSE",
                        "CENTRE_TELEPHONE", "CENTRE_EMAIL", "CENTRE_SITE_WEB", "CENTRE_CONTACT", "CENTRE_LIGNE")
                .forEach(k -> params.put(k, ""));
        params.put("SOCIETE_LOGO", null);
        return params;
    }

    private static String ligne(Map<String, Object> p, String cle, String style) {
        String valeur = echapper(p.get(cle));
        return valeur.isEmpty() ? "" : "<div style='" + style + "'>" + valeur + "</div>";
    }

    private static String image(Object logo) {
        if (!(logo instanceof InputStream in)) return "";
        try {
            byte[] octets = in.readAllBytes();
            String type = octets.length > 3 && octets[0] == (byte) 0xFF ? "image/jpeg" : "image/png";
            return "<img style='max-width:64px;max-height:52px' src='data:" + type + ";base64,"
                    + Base64.getEncoder().encodeToString(octets) + "'/>";
        } catch (IOException e) {
            return "";
        }
    }

    private static String echapper(Object valeur) {
        if (valeur == null) return "";
        return valeur.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String lieu(String ville, String wilaya) {
        return ville.equalsIgnoreCase(wilaya) ? ville : joindre(" – ", ville, wilaya);
    }

    private static String etiquette(String nom, String valeur) {
        return valeur.isEmpty() ? "" : nom + " : " + valeur;
    }

    private static String contact(String telephone, String email, String web) {
        return joindre(" · ", telephone.isEmpty() ? "" : "Tél : " + telephone, email.isEmpty() ? "" : "Email : " + email,
                web.isEmpty() ? "" : "Web : " + web);
    }

    private static String mentionsLegales(Societe s) {
        return Stream.of(etiquette("NIF", s.nif()), etiquette("NIS", s.nis()), etiquette("RC", s.rc()))
                .filter(x -> !x.isEmpty()).collect(Collectors.joining(" · "));
    }

    private static String joindre(String separateur, String... parties) {
        return Stream.of(parties).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(separateur));
    }
}
