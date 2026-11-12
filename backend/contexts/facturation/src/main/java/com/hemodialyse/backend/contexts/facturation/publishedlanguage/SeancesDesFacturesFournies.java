package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié de la Facturation, côté FOURNI (ADR 0097) : combien de séances sont « facturées », selon les DEUX définitions conservées
 * (KB-SEA-75, KB-FAC-54), fournies par la plateforme à partir de {@code seance.v1}. Lecture seule.
 */
public interface SeancesDesFacturesFournies {

    /** Séances du centre rattachées à chaque facture demandée, quels que soient leur statut et leur date ; une facture sans séance est absente. */
    Map<UUID, Long> nombreParFacture(UUID centre, Collection<UUID> factures);

    /** Comme {@link #nombreParFacture} mais seules les séances au statut {@code FACTUREE} (casse exacte) comptent (synthèse imprimée, PL8, KB-PL8-5). */
    Map<UUID, Long> nombreParFactureAuStatutFacturee(UUID centre, Collection<UUID> factures);

    /** Séances du centre au statut {@code FACTUREE} (casse exacte) datées de la période (bornes incluses), avec ou sans facture. */
    long nombreDeSeancesFactureesEntre(UUID centre, LocalDate debut, LocalDate fin);
}
