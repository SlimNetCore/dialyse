package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port d'entr\u00e9e (use cases) du agr\u00e9gat {@code DemandeExamen}.
 * Cas d'usage : {@code DemanderExamen}, {@code RecevoirResultat}, {@code ConsulterExamens},
 * {@code ValiderExamen}, {@code AnnulerExamen}.
 */
public interface DemandeExamenUseCases {

    DemandeExamenId demander(DemanderExamenCommande commande, Instant maintenant);

    void recevoirResultat(RecevoirResultatCommande commande);

    void valider(ValiderExamenCommande commande);

    void annuler(AnnulerExamenCommande commande);

    DemandeExamen consulter(DemandeExamenId demandeId, CentreId centreId);

    PagedResult<DemandeExamen> listerParPatient(CentreId centreId, UUID patientId, int page, int size);

    record DemanderExamenCommande(UUID patientId, CentreId centreId, String prescripteurId,
                                  LocalDate dateDemande, CategorieExamen categorie, boolean urgent,
                                  String motif, List<LigneDemandeExamen> lignes) {
    }

    record RecevoirResultatCommande(DemandeExamenId demandeId, CentreId centreId, UUID eventId,
                                    Instant maintenant) {
    }

    record ValiderExamenCommande(DemandeExamenId demandeId, CentreId centreId, String conclusion,
                                 Instant maintenant) {
    }

    record AnnulerExamenCommande(DemandeExamenId demandeId, CentreId centreId, Instant maintenant) {
    }
}
