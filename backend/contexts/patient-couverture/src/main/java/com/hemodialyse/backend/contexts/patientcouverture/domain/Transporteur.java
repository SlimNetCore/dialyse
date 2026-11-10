package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Transporteur d'un centre (RF1) : l'entreprise qui emmène un patient à la séance (aller) et le ramène (retour). Le nom est
 * obligatoire (255 caractères au plus) et sert de clé naturelle dans le centre ; le téléphone est facultatif (chiffres, signes et
 * séparateurs, 6 caractères au moins, 50 au plus). Immuable : une correction rend un nouveau transporteur, de même identité.
 */
public final class Transporteur {

    private static final int NOM_MAX = 255;
    private static final int TELEPHONE_MAX = 50;
    private static final Pattern TELEPHONE = Pattern.compile("^[+0-9 ().\\-/]{6,}$");

    private final UUID id;
    private final CentreId centre;
    private final String nom;
    private final String telephone;

    private Transporteur(UUID id, CentreId centre, String nom, String telephone) {
        this.id = id;
        this.centre = centre;
        this.nom = nom;
        this.telephone = telephone;
    }

    /** Référence un transporteur : refuse la saisie avec toutes ses anomalies ({@link SaisieDeTransporteurInvalide}). */
    public static Transporteur referencer(UUID id, CentreId centre, String nom, String telephone) {
        String nomNettoye = nettoyer(nom);
        String telephoneNettoye = nettoyer(telephone);
        List<AnomalieDeSaisie> anomalies = new ArrayList<>();
        controlerLeNom(nomNettoye, anomalies);
        controlerLeTelephone(telephoneNettoye, anomalies);
        if (!anomalies.isEmpty()) throw new SaisieDeTransporteurInvalide(anomalies);
        return new Transporteur(id, centre, nomNettoye, telephoneNettoye);
    }

    /** Reconstitue un transporteur déjà enregistré, tel quel (les données historiques ne sont pas revalidées). */
    public static Transporteur reconstituer(UUID id, CentreId centre, String nom, String telephone) {
        return new Transporteur(id, centre, nom, telephone);
    }

    /** Remplace le nom et le téléphone en gardant l'identité et le centre ; la nouvelle saisie est validée. */
    public Transporteur corriger(String nouveauNom, String nouveauTelephone) {
        return referencer(id, centre, nouveauNom, nouveauTelephone);
    }

    /** Clé naturelle dans le centre : le nom sans casse ni espaces répétés. */
    public String cle() {
        return nom.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Refuse ce transporteur si un AUTRE transporteur de la liste porte déjà la même clé. */
    public void verifierLUnicite(List<Transporteur> duCentre) {
        String cle = cle();
        for (Transporteur autre : duCentre) {
            if (!autre.id.equals(id) && autre.cle().equals(cle)) throw new TransporteurDejaExistant(nom);
        }
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public String nom() {
        return nom;
    }

    public String telephone() {
        return telephone;
    }

    private static String nettoyer(String valeur) {
        if (valeur == null) return null;
        String propre = valeur.replace(' ', ' ').trim();
        return propre.isEmpty() ? null : propre;
    }

    private static void controlerLeNom(String nom, List<AnomalieDeSaisie> anomalies) {
        if (nom == null) {
            anomalies.add(new AnomalieDeSaisie("nom", "REQUIRED", "« Nom » est obligatoire.", Map.of()));
        } else if (nom.length() > NOM_MAX) {
            anomalies.add(tropLong("nom", "Nom", NOM_MAX, nom));
        }
    }

    private static void controlerLeTelephone(String telephone, List<AnomalieDeSaisie> anomalies) {
        if (telephone == null) return;
        if (!TELEPHONE.matcher(telephone).matches()) {
            anomalies.add(new AnomalieDeSaisie("telephone", "INVALID_PHONE",
                    "« Téléphone » : « " + telephone + " » n'est pas un numéro de téléphone valide.", Map.of("value", telephone)));
        } else if (telephone.length() > TELEPHONE_MAX) {
            anomalies.add(tropLong("telephone", "Téléphone", TELEPHONE_MAX, telephone));
        }
    }

    private static AnomalieDeSaisie tropLong(String champ, String libelle, int max, String valeur) {
        return new AnomalieDeSaisie(champ, "TOO_LONG", "« " + libelle + " » dépasse " + max + " caractères (" + valeur.length() + ").",
                Map.of("max", String.valueOf(max), "length", String.valueOf(valeur.length())));
    }
}
