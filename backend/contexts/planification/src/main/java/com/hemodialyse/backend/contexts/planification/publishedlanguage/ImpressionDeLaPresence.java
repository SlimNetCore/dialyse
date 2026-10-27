package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port consommé par la Planification (ADR 0053, slice P4), fourni par le legacy (modèles de documents et moteur d'impression) : le
 * planning de présence imprimable d'une semaine. Il LIT sans écrire : un centre sans modèle de document utilise le modèle par défaut
 * en mémoire (KB-PLN-51, décision du 27/10/2026) ; un modèle désactivé par le centre refuse l'impression.
 */
public interface ImpressionDeLaPresence {

    /**
     * @param debutDeSemaine le dimanche de la semaine voulue
     * @throws ImpressionRefusee modèle désactivé ou génération impossible
     */
    Document imprimer(UUID centre, LocalDate debutDeSemaine);

    /** Document produit et le format du modèle du centre ({@code PDF}, {@code EXCEL}, {@code HTML}, ou un autre format inconnu). */
    record Document(byte[] contenu, String format) {
    }

    /** Refus métier (code stable, message lisible), rendu en 422. */
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
