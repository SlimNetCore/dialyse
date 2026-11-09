package com.hemodialyse.backend.contexts.seance.domain;

import java.math.BigDecimal;

/**
 * Les dix mesures du volet paramédical d'une séance, les SEULES que le legacy persistait (ADR 0100, décision 4) : poids avant et après
 * (kg), tension avant et après (texte « sys/dia »), durée (min), débit sang (ml/min), ultrafiltration (ml), anticoagulant, type de
 * dialysat, incidents. Chacune est facultative ; une mesure absente de la saisie est ABSENTE, jamais « inchangée » (le volet est
 * remplacé en entier, DM1).
 * <p>
 * {@link #saisies} applique les règles du legacy : un poids présent est borné ({@link Poids}), une tension présente est lue et
 * normalisée ({@link TensionArterielle}) ; une tension vide ou blanche est gardée telle quelle (« », «   », reproduit) ; la durée, le
 * débit et l'ultrafiltration ne sont pas bornés (négatifs et énormes acceptés : KB non ouvert, reproduit) ; les textes ne sont pas
 * bornés ici (la colonne refuse au-delà de 100 caractères : 500 {@code DATA_ACCESS_ERROR}, reproduit). {@link #restituees} relit une
 * ligne sans la revalider (données historiques).
 */
public record MesuresDeSeance(BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String tensionAvant, String tensionApres,
                              Integer dureeMinutes, Integer debitSangMlMin, BigDecimal ultrafiltrationMl, String anticoagulant,
                              String typeDeDialysat, String incidents) {

    /** Mesures saisies par l'infirmier : validées dans l'ordre du legacy (poids avant, poids après, tension avant, tension après). */
    public static MesuresDeSeance saisies(BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String tensionAvant, String tensionApres,
                                          Integer dureeMinutes, Integer debitSangMlMin, BigDecimal ultrafiltrationMl, String anticoagulant,
                                          String typeDeDialysat, String incidents) {
        if (poidsAvantKg != null) new Poids(poidsAvantKg);
        if (poidsApresKg != null) new Poids(poidsApresKg);
        String avant = TensionArterielle.lire(tensionAvant).map(TensionArterielle::texte).orElse(tensionAvant);
        String apres = TensionArterielle.lire(tensionApres).map(TensionArterielle::texte).orElse(tensionApres);
        return new MesuresDeSeance(poidsAvantKg, poidsApresKg, avant, apres, dureeMinutes, debitSangMlMin, ultrafiltrationMl, anticoagulant,
                typeDeDialysat, incidents);
    }

    /** Mesures relues en base, telles quelles. */
    public static MesuresDeSeance restituees(BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String tensionAvant, String tensionApres,
                                             Integer dureeMinutes, Integer debitSangMlMin, BigDecimal ultrafiltrationMl, String anticoagulant,
                                             String typeDeDialysat, String incidents) {
        return new MesuresDeSeance(poidsAvantKg, poidsApresKg, tensionAvant, tensionApres, dureeMinutes, debitSangMlMin, ultrafiltrationMl,
                anticoagulant, typeDeDialysat, incidents);
    }

    public static MesuresDeSeance aucune() {
        return new MesuresDeSeance(null, null, null, null, null, null, null, null, null, null);
    }
}
