package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Caisse d'assurance d'un centre (RF3a) : le plus haut maillon de la chaîne des payeurs, dont relèvent des agences. Le code (50
 * caractères au plus) est obligatoire et sert de clé naturelle dans le centre ; le nom (255 au plus) est obligatoire ; le type vaut
 * STANDARD (à défaut) ou VACANCIER. Immuable : une correction rend une nouvelle caisse, de même identité.
 */
public final class CaisseDAssurance implements Payeur {

    private final UUID id;
    private final CentreId centre;
    private final String code;
    private final String nom;
    private final String type;

    private CaisseDAssurance(UUID id, CentreId centre, String code, String nom, String type) {
        this.id = id;
        this.centre = centre;
        this.code = code;
        this.nom = nom;
        this.type = type;
    }

    /** Référence une caisse à partir de la saisie : refuse la saisie avec toutes ses anomalies ({@link SaisieDePayeurInvalide}). */
    public static CaisseDAssurance referencer(UUID id, CentreId centre, String code, String nom, String type) {
        ControleDeSaisieDePayeur controle = new ControleDeSaisieDePayeur();
        String codeControle = controle.texte("code", "Code", ControleDeSaisieDePayeur.CODE_MAX, true, code);
        String nomControle = controle.texte("nom", "Nom", ControleDeSaisieDePayeur.TEXTE_MAX, true, nom);
        String typeControle = controle.typeDeCaisse(type);
        controle.verifier();
        return new CaisseDAssurance(id, centre, codeControle, nomControle, typeControle);
    }

    /** Reconstitue une caisse déjà enregistrée, telle quelle (les données historiques ne sont pas revalidées). */
    public static CaisseDAssurance reconstituer(UUID id, CentreId centre, String code, String nom, String type) {
        return new CaisseDAssurance(id, centre, code, nom, type);
    }

    /** Remplace le code, le nom et le type (le type omis redevient STANDARD) en gardant l'identité et le centre ; la saisie est validée. */
    public CaisseDAssurance corriger(String nouveauCode, String nouveauNom, String nouveauType) {
        return referencer(id, centre, nouveauCode, nouveauNom, nouveauType);
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
        return NiveauDePayeur.CAISSE;
    }

    /** STANDARD ou VACANCIER ; {@code null} seulement pour une donnée historique sans type. */
    public String type() {
        return type;
    }
}
