package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.FactureARegler;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglements;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FactureReglementRecordedEvent;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage « enregistrer un règlement » (F12, RG-REG-001) : dans une transaction, retrouve la facture du centre (verrouillée),
 * enregistre le règlement et publie {@link FactureReglementRecordedEvent} ; répond la situation de la facture après règlement.
 * <p>
 * Avec une clé d'idempotence (KB-FAC-39 corrigé, facultative), un renvoi pour la même facture et le même centre ne crée ni
 * règlement ni événement : il répond la situation courante de la facture. Sans clé, un renvoi crée un second règlement
 * (reproduit). L'auteur est l'utilisateur de la session (KB-FAC corrigé, comme F3), {@code system} s'il est inconnu.
 */
public final class EnregistrerUnReglement {

    private final Reglements reglements;
    private final SituationsDesFactures situations;
    private final PublicationDesReglements publication;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;
    private final Supplier<UUID> identifiants;

    public EnregistrerUnReglement(Reglements reglements, SituationsDesFactures situations, PublicationDesReglements publication,
                                  UniteDeTravail uniteDeTravail, Clock horloge, Supplier<UUID> identifiants) {
        this.reglements = reglements;
        this.situations = situations;
        this.publication = publication;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    /**
     * @param utilisateur l'utilisateur de la session ; {@code null} si la session ne le donne pas
     * @throws IllegalArgumentException                                                                    facture introuvable pour le centre, facture à total négatif, montant nul
     * @throws com.hemodialyse.backend.contexts.facturation.domain.reglement.RemboursementSuperieurAuTotalRegle remboursement supérieur au total déjà réglé
     */
    public LigneDeSituation enregistrer(CentreId centre, UUID factureId, DemandeDeReglement demande, UUID utilisateur) {
        return uniteDeTravail.executer(() -> {
            FactureARegler facture = reglements.factureARegler(centre, factureId)
                    .orElseThrow(() -> new IllegalArgumentException("Facture introuvable pour le centre courant"));
            String cle = demande.cleDIdempotence() == null || demande.cleDIdempotence().isBlank() ? null
                    : demande.cleDIdempotence().trim();
            if (cle == null || reglements.parCleDIdempotence(centre, factureId, cle).isEmpty()) {
                LocalDate date = demande.date() != null ? demande.date() : LocalDate.now(horloge);
                String auteur = utilisateur != null ? utilisateur.toString() : null;
                Reglement reglement = Reglement.enregistrer(identifiants.get(), centre, facture, demande.montant(), date, auteur,
                        demande.mode(), demande.reference(), cle);
                reglements.enregistrer(reglement);
                publication.reglementEnregistre(new FactureReglementRecordedEvent(reglement.id(), factureId, centre.valeur(),
                        demande.montant(), date, reglement.auteur(),
                        reglement.mode() == null ? null : reglement.mode().name()));
            }
            return situations.deLaFacture(centre, factureId)
                    .orElseThrow(() -> new IllegalArgumentException("Facture introuvable pour le centre courant"));
        });
    }
}
