package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : l'annulation d'un lot de reprise (outil de migration historique, RP1, ADR 0169).
 * Patient et couverture supprime les patients, les assurés et les affectations que le lot a créés ; l'appelant décide de l'ordre, du
 * compte rendu des refus (« annulation impossible ») et des données des autres contextes. Chaque suppression est bornée au centre :
 * une donnée d'un autre centre n'est jamais touchée.
 */
public interface AnnulationDeLaReprise {

    /** Prises en charge du patient (tous les centres de l'identifiant, comme la lecture d'origine) : ce qu'il a déjà vécu depuis la reprise. */
    List<UUID> prisesEnChargeDuPatient(UUID patient);

    /** Attestations de droits du patient, mêmes règles que {@link #prisesEnChargeDuPatient}. */
    List<UUID> attestationsDuPatient(UUID patient);

    /**
     * Vrai si l'assuré est encore retenu par une affectation ou par un patient du centre que le lot n'a PAS créés (donc saisis depuis
     * dans la plateforme) : l'assuré ne peut plus être supprimé. Les identifiants du lot sont des UUID en minuscules.
     */
    boolean assureRetenuHorsDuLot(UUID centre, String numeroAssurance, Set<String> affectationsDuLot, Set<String> patientsDuLot);

    /** Supprime l'affectation d'assuré du centre (sans effet si elle est d'un autre centre). */
    void supprimerLAffectation(UUID centre, UUID affectation);

    /** Supprime le patient du centre et toutes ses affectations (sans effet s'il est d'un autre centre). */
    void supprimerLePatient(UUID centre, UUID patient);

    /** Supprime l'assuré du centre (sans effet s'il est d'un autre centre). */
    void supprimerLAssure(UUID centre, String numeroAssurance);
}
