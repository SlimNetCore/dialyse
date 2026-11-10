package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.UUID;

/**
 * Agence d'une caisse d'assurance (RF3a) : le maillon intermédiaire de la chaîne des payeurs, dont relèvent des centres payeurs. Elle
 * relève d'UNE caisse du même centre (désignée par son code ou son identifiant, référencée par identité). Code (50 caractères au plus)
 * et nom (255 au plus) obligatoires ; le code est la clé naturelle dans le centre. Immuable.
 */
public final class Agence implements Payeur {

    private final UUID id;
    private final CentreId centre;
    private final String code;
    private final String nom;
    private final UUID caisse;

    private Agence(UUID id, CentreId centre, String code, String nom, UUID caisse) {
        this.id = id;
        this.centre = centre;
        this.code = code;
        this.nom = nom;
        this.caisse = caisse;
    }

    /**
     * Référence une agence à partir de la saisie ({@code caisse} est le code ou l'identifiant tapé ou lu dans le fichier ;
     * {@code caissesDuCentre} sont les caisses du centre) : refuse la saisie avec toutes ses anomalies, dans l'ordre code, nom, caisse.
     */
    public static Agence referencer(UUID id, CentreId centre, String code, String nom, String caisse,
                                    List<CaisseDAssurance> caissesDuCentre) {
        ControleDeSaisieDePayeur controle = new ControleDeSaisieDePayeur();
        String codeControle = controle.texte("code", "Code", ControleDeSaisieDePayeur.CODE_MAX, true, code);
        String nomControle = controle.texte("nom", "Nom", ControleDeSaisieDePayeur.TEXTE_MAX, true, nom);
        UUID caisseControlee = controle.parent("caisse", "Caisse (code)", NiveauDePayeur.CAISSE, caisse, caissesDuCentre);
        controle.verifier();
        return new Agence(id, centre, codeControle, nomControle, caisseControlee);
    }

    /** Reconstitue une agence déjà enregistrée, telle quelle (les données historiques ne sont pas revalidées). */
    public static Agence reconstituer(UUID id, CentreId centre, String code, String nom, UUID caisse) {
        return new Agence(id, centre, code, nom, caisse);
    }

    /** Remplace le code, le nom et la caisse en gardant l'identité et le centre ; la saisie est validée. */
    public Agence corriger(String nouveauCode, String nouveauNom, String nouvelleCaisse, List<CaisseDAssurance> caissesDuCentre) {
        return referencer(id, centre, nouveauCode, nouveauNom, nouvelleCaisse, caissesDuCentre);
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
        return NiveauDePayeur.AGENCE;
    }

    /** L'identité de la caisse dont relève l'agence. */
    public UUID caisse() {
        return caisse;
    }
}
