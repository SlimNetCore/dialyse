package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.UUID;

/**
 * Centre payeur d'une agence (RF3a) : le dernier maillon de la chaîne des payeurs, dont relèvent des patients. Il relève d'UNE agence
 * du même centre (désignée par son code ou son identifiant, référencée par identité). Code (50 caractères au plus) et nom (255 au plus)
 * obligatoires ; l'adresse (255 au plus) est facultative ; le code est la clé naturelle dans le centre. Immuable.
 */
public final class CentrePayeur implements Payeur {

    private final UUID id;
    private final CentreId centre;
    private final String code;
    private final String nom;
    private final String adresse;
    private final UUID agence;

    private CentrePayeur(UUID id, CentreId centre, String code, String nom, String adresse, UUID agence) {
        this.id = id;
        this.centre = centre;
        this.code = code;
        this.nom = nom;
        this.adresse = adresse;
        this.agence = agence;
    }

    /**
     * Référence un centre payeur à partir de la saisie ({@code agence} est le code ou l'identifiant tapé ou lu dans le fichier ;
     * {@code agencesDuCentre} sont les agences du centre) : refuse la saisie avec toutes ses anomalies, dans l'ordre code, nom, adresse,
     * agence.
     */
    public static CentrePayeur referencer(UUID id, CentreId centre, String code, String nom, String adresse, String agence,
                                          List<Agence> agencesDuCentre) {
        ControleDeSaisieDePayeur controle = new ControleDeSaisieDePayeur();
        String codeControle = controle.texte("code", "Code", ControleDeSaisieDePayeur.CODE_MAX, true, code);
        String nomControle = controle.texte("nom", "Nom", ControleDeSaisieDePayeur.TEXTE_MAX, true, nom);
        String adresseControlee = controle.texte("adresse", "Adresse", ControleDeSaisieDePayeur.TEXTE_MAX, false, adresse);
        UUID agenceControlee = controle.parent("agence", "Agence (code)", NiveauDePayeur.AGENCE, agence, agencesDuCentre);
        controle.verifier();
        return new CentrePayeur(id, centre, codeControle, nomControle, adresseControlee, agenceControlee);
    }

    /** Reconstitue un centre payeur déjà enregistré, tel quel (les données historiques ne sont pas revalidées). */
    public static CentrePayeur reconstituer(UUID id, CentreId centre, String code, String nom, String adresse, UUID agence) {
        return new CentrePayeur(id, centre, code, nom, adresse, agence);
    }

    /** Remplace le code, le nom, l'adresse (omise : effacée) et l'agence en gardant l'identité et le centre ; la saisie est validée. */
    public CentrePayeur corriger(String nouveauCode, String nouveauNom, String nouvelleAdresse, String nouvelleAgence,
                                 List<Agence> agencesDuCentre) {
        return referencer(id, centre, nouveauCode, nouveauNom, nouvelleAdresse, nouvelleAgence, agencesDuCentre);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public CentreId centre() {
        return centre;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String nom() {
        return nom;
    }

    @Override
    public NiveauDePayeur niveau() {
        return NiveauDePayeur.CENTRE_PAYEUR;
    }

    /** L'adresse du centre payeur, ou {@code null} si elle n'est pas renseignée. */
    public String adresse() {
        return adresse;
    }

    /** L'identité de l'agence dont relève le centre payeur. */
    public UUID agence() {
        return agence;
    }
}
