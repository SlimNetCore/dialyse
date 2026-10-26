package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (écriture) : ce que l'administration des utilisateurs (Identité et accès, legacy) demande au référentiel des
 * infirmiers quand un compte change. Le legacy n'écrit plus jamais la table {@code infirmier}. Les deux opérations jouent dans la
 * transaction de l'appelant si elle est ouverte.
 */
public interface FichesDesInfirmiers {

    /**
     * Le compte a été activé ou désactivé : les fiches liées suivent (RG-INF-003).
     *
     * @param centre le centre de l'administrateur : seules ses fiches suivent ; {@code null} pour un accès transverse (toutes)
     */
    void suivreLEtatDuCompte(UUID compte, boolean actif, UUID centre);

    /**
     * Prépare la suppression du compte : délie ses fiches inactives (tous centres).
     *
     * @throws CompteLieAUneFicheActive une fiche active est liée au compte : le compte ne peut pas être supprimé
     */
    void delierAvantLaSuppressionDuCompte(UUID compte);

    /**
     * Prend, pour la transaction de l'appelant, le verrou qui sérialise la création des identifiants de connexion (aucune unicité en
     * base PostgreSQL, KB-PLN-24) : à appeler avant le contrôle d'existence d'un identifiant.
     *
     * @throws EcritureConcurrenteDesFiches le verrou n'est pas obtenu dans le délai
     */
    void verrouillerLesIdentifiantsDeConnexion();

    /**
     * Prend, pour la transaction de l'appelant, le verrou de ces fiches d'infirmiers (par identifiant croissant) avant qu'il écrive
     * leur roulement ou leurs remplacements : les écritures de la Planification (slice P3) sur les mêmes infirmiers l'attendent. À
     * appeler AVANT de lire le roulement à modifier (application d'une proposition d'optimisation).
     *
     * @throws EcritureConcurrenteDesFiches le verrou n'est pas obtenu dans le délai
     */
    void verrouillerLesFiches(UUID centre, java.util.Collection<UUID> infirmiers);

    /**
     * Une autre écriture tient les fiches ou les identifiants (verrou non obtenu dans le délai, ou interblocage) : 409
     * {@code PLANIFICATION_ECRITURE_CONCURRENTE} ; le client relit puis recommence. Rendu en 409 par l'advice du contexte pour toute route.
     */
    class EcritureConcurrenteDesFiches extends RuntimeException {
        public EcritureConcurrenteDesFiches(String message) {
            super(message);
        }
    }

    /** Le compte est lié à une fiche d'infirmier active (422 {@code COMPTE_LIE_A_UNE_FICHE_ACTIVE} côté administration). */
    class CompteLieAUneFicheActive extends RuntimeException {
        public CompteLieAUneFicheActive(String message) {
            super(message);
        }
    }
}
