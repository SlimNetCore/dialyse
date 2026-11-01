package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.specification.AllergieDoublonSpecification;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implémentation des use cases {@link AllergieUseCases}.
 * Orchestre l'agrégat {@link Allergie} et le port de sortie {@link AllergieRepository} ;
 * génère les identifiants et reçoit l'horloge de l'appelant.
 */
public class AllergieService implements AllergieUseCases {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final AllergieRepository allergies;
    private final Supplier<UUID> idGenerator;

    public AllergieService(AllergieRepository allergies, Supplier<UUID> idGenerator) {
        this.allergies = allergies;
        this.idGenerator = idGenerator;
    }

    @Override
    public AllergieId enregistrer(EnregistrerAllergieCommande commande, OffsetDateTime maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");

        AllergieId id = new AllergieId(idGenerator.get());

        var existants = allergies.findByPatientId(commande.patientId(), commande.centreId());
        if (!AllergieDoublonSpecification.estSatisfaitePar(existants, commande.allergene(), null)) {
            throw new IllegalStateException(
                    "Une allergie active à cet allergène existe déjà pour ce patient");
        }

        Allergie allergie = Allergie.declarer(id, commande.patientId(), commande.centreId(),
                commande.allergene(), commande.type(), commande.reaction(),
                commande.dateDebut(), commande.dateFin(), commande.severite(), commande.note(), maintenant);
        allergies.save(allergie);
        return id;
    }

    @Override
    public Allergie consulter(AllergieId allergieId, CentreId centreId) {
        return allergies.trouver(allergieId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Allergie introuvable"));
    }

    @Override
    public PagedResult<Allergie> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return allergies.findPagedByPatientId(patientId, centreId, Math.max(page, 0), safeSize);
    }

    @Override
    public Allergie resoudre(AllergieId allergieId, CentreId centreId, LocalDate dateResolution,
                             OffsetDateTime maintenant) {
        Allergie allergie = consulter(allergieId, centreId);
        allergie.resoudre(dateResolution, maintenant);
        allergies.save(allergie);
        return allergie;
    }

    @Override
    public Allergie modifier(ModifierAllergieCommande commande, OffsetDateTime maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        Allergie allergie = consulter(commande.allergieId(), commande.centreId());
        StatutClinique statut = commande.statutClinique() == null ? null
                : StatutClinique.valueOf(commande.statutClinique().name());
        allergie.modifier(commande.reaction(), commande.dateDebut(), commande.dateFin(), statut,
                commande.severite(), commande.note(), maintenant);
        allergies.save(allergie);
        return allergie;
    }

    @Override
    public void supprimer(AllergieId allergieId, CentreId centreId) {
        allergies.deleteById(allergieId, centreId);
    }
}
