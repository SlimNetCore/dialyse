package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenRepository;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.ResultatExamenRecu;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Impl\u00e9mentation des use cases {@code DemandeExamenUseCases}.
 * Orchestre l'agr\u00e9gat et le port de sortie ; g\u00e9n\u00e8re identifiants et instants.
 */
public class DemandeExamenService implements DemandeExamenUseCases {

    private final DemandeExamenRepository demandes;
    private final java.util.function.Supplier<UUID> idGenerator;

    public DemandeExamenService(DemandeExamenRepository demandes,
                                java.util.function.Supplier<UUID> idGenerator) {
        this.demandes = demandes;
        this.idGenerator = idGenerator;
    }

    @Override
    public DemandeExamenId demander(DemanderExamenCommande commande, Instant maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        DemandeExamenId id = new DemandeExamenId(idGenerator.get());
        OffsetDateTime maintenantOd = maintenant.atOffset(ZoneOffset.UTC);
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        DemandeExamen demande = DemandeExamen.creer(id, commande.patientId(), commande.centreId(),
                commande.prescripteurId(), commande.dateDemande(), aujourdhui,
                commande.categorie(), commande.urgent(), commande.motif(), commande.lignes(), maintenantOd);
        demandes.save(demande);
        return id;
    }

    @Override
    public void recevoirResultat(RecevoirResultatCommande commande) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (commande.maintenant() == null) throw new IllegalArgumentException("maintenant requis");
        DemandeExamen demande = demandes.trouver(commande.demandeId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("DemandeExamen introuvable"));
        demande.marquerResultatDisponible(commande.maintenant());
        demandes.save(demande);
        // L'\u00e9v\u00e9ment ResultatExamenRecu est construit ici (couche application) et peut \u00eatre publi\u00e9
        // via l'outbox / l'\u00e9diteur d'\u00e9v\u00e9nements Spring Modulith.
        ResultatExamenRecu evenement = new ResultatExamenRecu(
                commande.eventId() != null ? commande.eventId() : UUID.randomUUID(),
                demande.id().value(), demande.patientId(), demande.centreId().valeur(),
                demande.categorie().name(), commande.maintenant());
    }

    @Override
    public void valider(ValiderExamenCommande commande) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        DemandeExamen demande = demandes.trouver(commande.demandeId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("DemandeExamen introuvable"));
        if (commande.maintenant() == null) throw new IllegalArgumentException("maintenant requis");
        demande.valider(commande.conclusion(), commande.maintenant().atOffset(ZoneOffset.UTC));
        demandes.save(demande);
    }

    @Override
    public void annuler(AnnulerExamenCommande commande) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        DemandeExamen demande = demandes.trouver(commande.demandeId(), commande.centreId())
                .orElseThrow(() -> new IllegalArgumentException("DemandeExamen introuvable"));
        if (commande.maintenant() == null) throw new IllegalArgumentException("maintenant requis");
        demande.annuler(commande.maintenant().atOffset(ZoneOffset.UTC));
        demandes.save(demande);
    }

    @Override
    public DemandeExamen consulter(DemandeExamenId demandeId, CentreId centreId) {
        return demandes.trouver(demandeId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("DemandeExamen introuvable"));
    }

    @Override
    public PagedResult<DemandeExamen> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        return demandes.listerParPatient(centreId, patientId, page, size);
    }
}
