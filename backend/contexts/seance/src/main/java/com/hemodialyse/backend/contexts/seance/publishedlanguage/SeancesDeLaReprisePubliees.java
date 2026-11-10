package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0099, SE7e) : les séances historiques reprises de l'ancien logiciel (import d'un lot
 * de reprise de données) et leur retrait à l'annulation du lot. SEUL port, hors de la Séance, qui ÉCRIT et SUPPRIME des séances ; réservé
 * aux deux adapters de la reprise (règle ArchUnit). N'ouvre aucune transaction : il participe à celle de l'import ou de l'annulation.
 * <p>
 * Champs acceptés (liste blanche, identique à l'ancien descripteur de table) : {@code patientId}, {@code dateSeance}, {@code statut}
 * (traduits en {@code patient_id}, {@code date_seance}, {@code statut}) ; tout autre champ lève {@link IllegalArgumentException}
 * (« Champ non autorisé pour seances : … »). Aucune valeur venant d'un fichier n'est concaténée à une requête.
 */
public interface SeancesDeLaReprisePubliees {

    /**
     * Nature des données « séances » telle qu'imprimée dans un bloquant d'annulation de lot (« patient « P1 » (seances) ») et telle
     * qu'enregistrée par la reprise : libellé observable (golden {@code reprise/*}), égal au nom historique de la table. Ce n'est pas un
     * nom de table offert aux appelants : aucune requête ne doit le concaténer.
     */
    String NATURE = "seances";

    /**
     * La première séance (par date de création) du centre qui a ces valeurs de clé, QUEL QUE SOIT son statut SAUF {@code ANNULEE} : une
     * séance annulée n'est pas une séance tenue et n'empêche plus la reprise (KB-SEA-89 corrigé le 2026-11-10, ADR 0117) ; une valeur
     * nulle se compare par {@code IS NULL} (E12).
     */
    Optional<UUID> seanceDejaPresente(UUID centre, Map<String, Object> cle);

    /** Crée la séance reprise dans le centre (identifiant tiré au hasard, date de création = maintenant) et rend son identifiant (E13). */
    UUID reprendre(UUID centre, Map<String, Object> valeurs);

    /**
     * Modifie la séance reprise du centre (jamais le patient). Inatteignable aujourd'hui : une séance déjà présente n'est jamais modifiée
     * par la reprise ({@code HistoryRules.Seances.updatable() = false}) ; conservé pour l'équivalence avec l'ancien adapter.
     */
    void modifier(UUID centre, UUID seance, Map<String, Object> valeurs);

    /**
     * Les identifiants (texte) des séances du patient, de N'IMPORTE QUEL centre (KB-SEA-90, reproduit : une séance d'un autre centre bloque
     * l'annulation), sans ordre défini (E14).
     */
    List<String> seancesDuPatient(UUID patient);

    /** L'identifiant (texte) de la facture de la séance s'il y en a une, recherché sans le centre (E15). */
    List<String> factureDeLaSeance(UUID seance);

    /** Supprime la séance reprise, seulement dans le centre du lot (E16). */
    void retirer(UUID centre, UUID seance);
}
