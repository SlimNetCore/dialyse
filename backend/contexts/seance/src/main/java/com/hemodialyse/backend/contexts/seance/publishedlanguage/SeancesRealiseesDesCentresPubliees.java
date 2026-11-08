package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0099, SE7e) : les séances réalisées des centres d'une société, comptées pour le
 * pilotage de la Direction (vue d'ensemble, période précédente, évolution mensuelle, répartition par caisse, taux d'absentéisme). Agrégats
 * seulement : aucune ligne de patient n'est publiée. Lecture seule, aucune transaction propre.
 * <p>
 * « Réalisée » = statut {@code VALIDEE}, {@code SIGNEE} ou {@code FACTUREE} en casse EXACTE (KB-SEA-82, 83 : {@code validee} ou
 * {@code VALIDEE } ne comptent pas), date de séance dans {@code [du, au]} bornes incluses. {@code centres} n'est jamais vide (l'appelant
 * rend une vue vide sans appeler le port).
 */
public interface SeancesRealiseesDesCentresPubliees {

    /** Une ligne par centre ayant au moins une séance réalisée sur la période ; un centre sans séance est absent (E1, E5). */
    Map<UUID, Long> parCentre(List<UUID> centres, LocalDate du, LocalDate au);

    /** Le total des centres sur la période (E2). */
    long auTotal(List<UUID> centres, LocalDate du, LocalDate au);

    /** Le nombre de séances réalisées par centre et par mois civil, sans ordre défini (E3). */
    List<MoisRealisePublie> parMois(List<UUID> centres, LocalDate du, LocalDate au);

    /**
     * Le nombre de séances réalisées par centre et par caisse de la fiche du patient (E4). La séance n'est comptée que si la fiche du
     * patient est DANS le centre de la séance (KB-SEA-83, reproduit : une séance sur une fiche d'un autre centre disparaît) ; la chaîne
     * payeur, agence, caisse est suivie SANS contrôle de centre (KB-SEA-84). Code de caisse vide ({@code ""}) et nom nul sans caisse.
     * Ordre des lignes : celui du SGBD (aucun tri, KB-SEA-84).
     */
    List<CaisseRealiseePubliee> parCaisse(List<UUID> centres, LocalDate du, LocalDate au);

    record MoisRealisePublie(UUID centre, int annee, int mois, long nombre) {
    }

    record CaisseRealiseePubliee(UUID centre, String codeCaisse, String nomCaisse, long nombre) {
    }
}
