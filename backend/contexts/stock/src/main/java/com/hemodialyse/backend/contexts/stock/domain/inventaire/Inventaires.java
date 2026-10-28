package com.hemodialyse.backend.contexts.stock.domain.inventaire;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Port sortant : les inventaires d'un centre (tables {@code inventaires} et {@code inventaire_lignes}, migration V4). Toutes les
 * opérations s'exécutent dans la transaction de l'appelant ; {@link #verrouillerLaPeriode} sérialise les opérations « centre
 * entier » (ouverture, clôture, annulation).
 */
public interface Inventaires {

    /**
     * Crée un inventaire : la référence {@code INV-nnnnn} est prise au compteur du centre dans la transaction, puis donnée à la
     * fabrique (un refus de la fabrique n'en consomme aucune).
     */
    Inventaire creer(CentreId centre, Function<String, Inventaire> fabrique);

    Optional<Inventaire> parId(CentreId centre, UUID id);

    /**
     * L'inventaire en cours du centre, s'il y en a un.
     */
    Optional<Inventaire> enCours(CentreId centre);

    /**
     * Verrouille l'inventaire ({@code FOR UPDATE}) puis le relit : deux écritures sur le même inventaire se sérialisent.
     */
    Optional<Inventaire> verrouiller(CentreId centre, UUID id);

    /**
     * Enregistre l'état de l'inventaire (statut, clôture) et remplace ses lignes.
     */
    void enregistrer(Inventaire inventaire);

    /**
     * Situation du centre : inventaire en cours, dernière clôture.
     */
    PeriodeDeStock periode(CentreId centre);

    PageDInventaires lister(CentreId centre, int page, int taille);

    /**
     * Verrou exclusif du centre pour la durée de la transaction (une ligne par centre).
     */
    void verrouillerLaPeriode(CentreId centre);

    /**
     * Un inventaire d'une liste (sans ses lignes).
     */
    record Resume(UUID id, String reference, java.time.LocalDate dateInventaire, StatutDInventaire statut, int lignes,
                  int comptees, int ecarts, java.math.BigDecimal valeurEcarts, String creePar,
                  java.time.OffsetDateTime creeLe,
                  String clotureePar, java.time.OffsetDateTime clotureeLe) {
    }

    record PageDInventaires(java.util.List<Resume> elements, long total, int page, int taille) {
    }
}


