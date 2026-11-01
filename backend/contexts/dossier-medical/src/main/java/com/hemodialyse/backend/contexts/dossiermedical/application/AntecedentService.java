package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConceptCode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.specification.AntecedentDupliqueSpecification;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implémentation des use cases {@link AntecedentUseCases}.
 * Orchestre l'agrégat {@link Antecedent} et le port de sortie {@link AntecedentRepository} ;
 * génère les identifiants et reçoit l'horloge de l'appelant.
 */
public class AntecedentService implements AntecedentUseCases {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final AntecedentRepository antecedents;
    private final Supplier<UUID> idGenerator;

    public AntecedentService(AntecedentRepository antecedents, Supplier<UUID> idGenerator) {
        this.antecedents = antecedents;
        this.idGenerator = idGenerator;
    }

    @Override
    public AntecedentId enregistrer(EnregistrerAntecedentCommande commande, OffsetDateTime maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");

        AntecedentId id = new AntecedentId(idGenerator.get());

        var existants = antecedents.findByPatientId(commande.patientId(), commande.centreId());
        if (!AntecedentDupliqueSpecification.estSatisfaitePar(existants, commande.diagnostic(), null)) {
            throw new IllegalStateException(
                    "Un antécédent actif avec ce diagnostic existe déjà pour ce patient");
        }

        Antecedent antecedent = Antecedent.creer(id, commande.patientId(), commande.centreId(),
                commande.type(), commande.diagnostic(), commande.libelleLibre(),
                commande.dateDebut(), commande.dateFin(), commande.severite(), commande.note(), maintenant);
        antecedents.save(antecedent);
        return id;
    }

    @Override
    public Antecedent consulter(AntecedentId antecedentId, CentreId centreId) {
        return antecedents.trouver(antecedentId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Antécédent introuvable"));
    }

    @Override
    public PagedResult<Antecedent> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return antecedents.findPagedByPatientId(patientId, centreId, Math.max(page, 0), safeSize);
    }

    @Override
    public Antecedent resoudre(AntecedentId antecedentId, CentreId centreId, LocalDate dateResolution,
                               OffsetDateTime maintenant) {
        Antecedent antecedent = consulter(antecedentId, centreId);
        antecedent.resoudre(dateResolution, maintenant);
        antecedents.save(antecedent);
        return antecedent;
    }

    @Override
    public Antecedent modifier(ModifierAntecedentCommande commande, OffsetDateTime maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        Antecedent antecedent = consulter(commande.antecedentId(), commande.centreId());
        var existants = antecedents.findByPatientId(antecedent.getPatientId(), antecedent.getCentreId());
        if (!AntecedentDupliqueSpecification.estSatisfaitePar(existants, commande.diagnostic(),
                commande.antecedentId().value())) {
            throw new IllegalStateException(
                    "Un antécédent actif avec ce diagnostic existe déjà pour ce patient");
        }
        StatutClinique statut = commande.statutClinique() == null ? null
                : StatutClinique.valueOf(commande.statutClinique().name());
        antecedent.modifier(commande.diagnostic(), commande.libelleLibre(), commande.dateDebut(),
                commande.dateFin(), statut, commande.severite(), commande.note(), maintenant);
        antecedents.save(antecedent);
        return antecedent;
    }

    @Override
    public void supprimer(AntecedentId antecedentId, CentreId centreId) {
        antecedents.deleteById(antecedentId, centreId);
    }
}
