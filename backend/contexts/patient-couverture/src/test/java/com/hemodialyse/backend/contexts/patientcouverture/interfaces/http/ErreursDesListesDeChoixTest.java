package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF5 (revue complémentaire RF1-RF3d, constat 4) : les refus 400 des listes de choix (payeurs, forfaits, transports) ne renvoient plus le
 * message brut d'une {@link IllegalArgumentException} : seuls les deux messages attendus (identifiant illisible, paramètre obligatoire
 * absent) sont conservés tels quels (réponses inchangées pour les goldens), tout autre message est remplacé par un texte neutre.
 */
class ErreursDesListesDeChoixTest {

    private static final List<Function<IllegalArgumentException, ProblemDetail>> ADVICES = List.of(
            erreur -> new ErreursDesPayeursAdvice().identifiantIllisible(erreur),
            erreur -> new ErreursDesForfaitsAdvice().identifiantIllisible(erreur),
            erreur -> new ErreursDesTransportsAdvice().identifiantIllisible(erreur));

    @Test
    void un_message_interne_n_est_jamais_renvoye_au_client() {
        for (var advice : ADVICES) {
            ProblemDetail probleme = advice.apply(new IllegalArgumentException(
                    "could not execute statement [select * from caisse_assurance where code = 'x'] at com.hemodialyse.Interne"));

            assertThat(probleme.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(probleme.getProperties()).containsEntry("code", "BAD_REQUEST");
            assertThat(probleme.getDetail()).isEqualTo("Requête invalide");
        }
    }

    @Test
    void un_message_a_plusieurs_lignes_ou_sans_rapport_est_neutralise() {
        for (var advice : ADVICES) {
            assertThat(advice.apply(new IllegalArgumentException("Invalid UUID string: x\nat com.hemodialyse.Interne")).getDetail())
                    .isEqualTo("Requête invalide");
            assertThat(advice.apply(new IllegalArgumentException((String) null)).getDetail()).isEqualTo("Requête invalide");
            assertThat(advice.apply(new IllegalArgumentException("Paramètre obligatoire absent : centerId ; DROP")).getDetail())
                    .isEqualTo("Requête invalide");
        }
    }

    @Test
    void les_deux_messages_attendus_sont_conserves_tels_quels() {
        for (var advice : ADVICES) {
            assertThat(advice.apply(new IllegalArgumentException("Invalid UUID string: pas-un-uuid")).getDetail())
                    .isEqualTo("Invalid UUID string: pas-un-uuid");
            assertThat(advice.apply(new IllegalArgumentException("Paramètre obligatoire absent : centerId")).getDetail())
                    .isEqualTo("Paramètre obligatoire absent : centerId");
            assertThat(advice.apply(new IllegalArgumentException("Invalid UUID string: pas-un-uuid")).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }
}
