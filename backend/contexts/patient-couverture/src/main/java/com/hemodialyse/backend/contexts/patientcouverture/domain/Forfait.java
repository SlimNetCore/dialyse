package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Forfait du catalogue de forfaits d'un centre (RF2) : le prix hors taxe d'une séance que l'accord de prise en charge peut
 * retenir. Le code (50 caractères au plus) est obligatoire et sert de clé naturelle dans le centre ; le libellé (255 au plus) est
 * obligatoire ; le prix est FACULTATIF : absent, il est toléré (sens métier à confirmer, questions ouvertes de la fiche RF2) ; zéro
 * est un prix ; un prix négatif ou supérieur à 99 999 999,99 est refusé ; il est arrondi au centime (demi vers le haut). Immuable :
 * une correction rend un nouveau forfait, de même identité.
 */
public final class Forfait {

    private static final int CODE_MAX = 50;
    private static final int LIBELLE_MAX = 255;
    private static final BigDecimal PRIX_MAX = new BigDecimal("99999999.99");

    private final UUID id;
    private final CentreId centre;
    private final String code;
    private final String libelle;
    private final BigDecimal prix;

    private Forfait(UUID id, CentreId centre, String code, String libelle, BigDecimal prix) {
        this.id = id;
        this.centre = centre;
        this.code = code;
        this.libelle = libelle;
        this.prix = prix;
    }

    /**
     * Référence un forfait à partir de la saisie (le prix est le texte tapé ou lu dans le fichier) : refuse la saisie avec toutes
     * ses anomalies, dans l'ordre code, libellé, prix ({@link SaisieDeForfaitInvalide}).
     */
    public static Forfait referencer(UUID id, CentreId centre, String code, String libelle, String prix) {
        String codeNettoye = nettoyer(code);
        String libelleNettoye = nettoyer(libelle);
        String prixNettoye = nettoyer(prix);
        List<AnomalieDeSaisie> anomalies = new ArrayList<>();
        controlerLeTexte("code", "Code", CODE_MAX, codeNettoye, anomalies);
        controlerLeTexte("libelle", "Libellé", LIBELLE_MAX, libelleNettoye, anomalies);
        BigDecimal prixLu = controlerLePrix(prixNettoye, anomalies);
        if (!anomalies.isEmpty()) throw new SaisieDeForfaitInvalide(anomalies);
        return new Forfait(id, centre, codeNettoye, libelleNettoye, prixLu);
    }

    /** Reconstitue un forfait déjà enregistré, tel quel (les données historiques ne sont pas revalidées). */
    public static Forfait reconstituer(UUID id, CentreId centre, String code, String libelle, BigDecimal prix) {
        return new Forfait(id, centre, code, libelle, prix);
    }

    /** Remplace le code, le libellé et le prix (le prix omis est effacé) en gardant l'identité et le centre ; la saisie est validée. */
    public Forfait corriger(String nouveauCode, String nouveauLibelle, String nouveauPrix) {
        return referencer(id, centre, nouveauCode, nouveauLibelle, nouveauPrix);
    }

    /** Clé naturelle dans le centre : le code sans casse ni espaces répétés. */
    public String cle() {
        return code.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Refuse ce forfait si un AUTRE forfait de la liste porte déjà la même clé. */
    public void verifierLUnicite(List<Forfait> duCentre) {
        String cle = cle();
        for (Forfait autre : duCentre) {
            if (!autre.id.equals(id) && autre.cle().equals(cle)) throw new ForfaitDejaExistant(code);
        }
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    /** Le prix hors taxe au centime, ou {@code null} si le forfait n'a pas de prix. */
    public BigDecimal prix() {
        return prix;
    }

    private static String nettoyer(String valeur) {
        if (valeur == null) return null;
        String propre = valeur.replace(' ', ' ').trim();
        return propre.isEmpty() ? null : propre;
    }

    private static void controlerLeTexte(String champ, String libelle, int max, String valeur, List<AnomalieDeSaisie> anomalies) {
        if (valeur == null) {
            anomalies.add(new AnomalieDeSaisie(champ, "REQUIRED", "« " + libelle + " » est obligatoire.", Map.of()));
        } else if (valeur.length() > max) {
            anomalies.add(new AnomalieDeSaisie(champ, "TOO_LONG",
                    "« " + libelle + " » dépasse " + max + " caractères (" + valeur.length() + ").",
                    Map.of("max", String.valueOf(max), "length", String.valueOf(valeur.length()))));
        }
    }

    private static BigDecimal controlerLePrix(String saisi, List<AnomalieDeSaisie> anomalies) {
        if (saisi == null) return null;
        String compact = saisi.replaceAll("[\\s\\u00A0\\u202F]", "").replace(',', '.');
        BigDecimal nombre;
        try {
            nombre = new BigDecimal(compact);
        } catch (NumberFormatException illisible) {
            anomalies.add(new AnomalieDeSaisie("prix", "INVALID_NUMBER",
                    "« Prix » : « " + saisi + " » n'est pas un nombre (ex. 5600 ou 5600,50).", Map.of("value", saisi)));
            return null;
        }
        if (nombre.signum() < 0) {
            anomalies.add(new AnomalieDeSaisie("prix", "NEGATIVE_NUMBER", "« Prix » ne peut pas être négatif.", Map.of("value", saisi)));
            return null;
        }
        BigDecimal auCentime = nombre.setScale(2, RoundingMode.HALF_UP);
        if (auCentime.compareTo(PRIX_MAX) > 0) {
            anomalies.add(new AnomalieDeSaisie("prix", "NUMBER_TOO_LARGE",
                    "« Prix » est trop grand (maximum " + PRIX_MAX.toPlainString() + ").",
                    Map.of("value", saisi, "max", PRIX_MAX.toPlainString())));
            return null;
        }
        return auCentime;
    }
}
