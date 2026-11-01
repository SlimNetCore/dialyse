package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConceptCode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAntecedent;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Port d'entrée (use cases) de l'agrégat {@link Antecedent}.
 * Cas d'usage de la slice DM5 : {@code AjouterAntecedent}, {@code ConsulterAntecedent},
 * {@code ListerAntecedentsParPatient}, {@code ResoudreAntecedent}, {@code ModifierAntecedent},
 * {@code SupprimerAntecedent}.
 */
public interface AntecedentUseCases {

    AntecedentId enregistrer(EnregistrerAntecedentCommande commande, OffsetDateTime maintenant);

    Antecedent consulter(AntecedentId antecedentId, CentreId centreId);

    PagedResult<Antecedent> listerParPatient(CentreId centreId, UUID patientId, int page, int size);

    Antecedent resoudre(AntecedentId antecedentId, CentreId centreId, LocalDate dateResolution,
                        OffsetDateTime maintenant);

    Antecedent modifier(ModifierAntecedentCommande commande, OffsetDateTime maintenant);

    void supprimer(AntecedentId antecedentId, CentreId centreId);

    enum StatutCliniqueParam {
        ACTIF, RESOLU
    }

    record EnregistrerAntecedentCommande(UUID patientId, CentreId centreId, TypeAntecedent type,
                                         ConceptCode diagnostic, String libelleLibre, LocalDate dateDebut,
                                         LocalDate dateFin, String severite, String note) {
    }

    record ModifierAntecedentCommande(AntecedentId antecedentId, CentreId centreId, ConceptCode diagnostic,
                                      String libelleLibre, LocalDate dateDebut, LocalDate dateFin,
                                      StatutCliniqueParam statutClinique, String severite, String note) {
    }
}
