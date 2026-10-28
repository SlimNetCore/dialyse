package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.UUID;

/**
 * Port consommé par la Planification (slice P8), fourni par le legacy (modèles de documents et moteur d'impression) : le planning
 * calendaire figé d'une proposition d'optimisation, imprimé avec le modèle de document du centre. Le modèle lit le calendrier figé à la
 * fin du calcul (table de la Planification) : il ne dépend pas de l'état courant du centre.
 */
public interface ImpressionDuCalendrierDeLaProposition {

    /**
     * @param titre  ligne d'en-tête (date, périmètre, auteur, état d'application)
     * @param resume indicateurs avant / après, vide si le calcul n'en a pas
     * @throws ImpressionRefusee modèle désactivé ou génération impossible
     */
    Document imprimer(UUID centre, UUID execution, String titre, String resume);

    /**
     * Document produit et le format du modèle du centre ({@code PDF}, {@code EXCEL}, {@code HTML}, ou un autre format inconnu).
     */
    record Document(byte[] contenu, String format) {
    }

    /**
     * Refus métier (code stable, message lisible), rendu en 422.
     */
    class ImpressionRefusee extends RuntimeException {
        private final String code;

        public ImpressionRefusee(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
