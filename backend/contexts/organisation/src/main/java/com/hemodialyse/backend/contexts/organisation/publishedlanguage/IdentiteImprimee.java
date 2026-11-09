package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié d'Organisation (organisation.v1, O4 lot E, ADR 0111) : l'identité imprimée d'un centre et de sa société, c'est-à-dire
 * ce que les documents (en-tête, pied de page, documents de direction) impriment de l'Organisation. Lecture seule ; aucun état.
 *
 * <p>Seules les données sont publiées : leur mise en forme (ligne d'adresse, contact, mentions légales, logo en flux ou en {@code data:}
 * URI, échappement) reste celle du consommateur qui imprime. Tous les textes sont rendus SANS blanc de bord et JAMAIS nuls : une valeur
 * absente est la chaîne vide. Le logo est {@code null} s'il est absent ou vide. Une société désactivée est imprimée comme une société
 * active : l'état d'activité n'est pas publié et ne filtre rien (comportement historique de la plateforme).
 */
public interface IdentiteImprimee {

    /**
     * L'identité imprimée d'une société : raison sociale, adresse (adresse, ville, wilaya), contacts, mentions légales (NIF, NIS, RC),
     * pied de page et logo.
     *
     * @param logo les octets du logo (image), {@code null} s'il est absent ou vide ; ne pas modifier le tableau
     */
    record IdentiteDeLaSociete(String raisonSociale, String adresse, String ville, String wilaya, String telephone, String email,
                               String siteWeb, String nif, String nis, String rc, String piedDePage, byte[] logo) {

        /** Une identité de société entièrement vide (société absente). */
        public static IdentiteDeLaSociete vide() {
            return new IdentiteDeLaSociete("", "", "", "", "", "", "", "", "", "", "", null);
        }
    }

    /**
     * L'identité imprimée d'un centre : nom, adresse (adresse, ville, wilaya), contacts, et celle de sa société.
     *
     * @param societe la société du centre ; {@link IdentiteDeLaSociete#vide()} si le centre n'en a pas (cas défensif : le rattachement
     *                est obligatoire en base)
     */
    record IdentiteDuCentre(String nom, String adresse, String ville, String wilaya, String telephone, String email, String siteWeb,
                            IdentiteDeLaSociete societe) {
    }

    /** L'identité imprimée d'un centre et de sa société ; vide si le centre est inconnu (ou {@code null}). */
    Optional<IdentiteDuCentre> duCentre(UUID centre);

    /** L'identité imprimée de la seule société (documents de direction, sans centre) ; vide si la société est inconnue (ou {@code null}). */
    Optional<IdentiteDeLaSociete> deLaSociete(UUID societe);
}
