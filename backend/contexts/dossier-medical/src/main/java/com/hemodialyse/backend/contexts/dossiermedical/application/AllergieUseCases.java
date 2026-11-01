package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAllergie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Port d'entrée (use cases) de l'agrégat {@link Allergie}.
 * Cas d'usage de la slice DM5 : {@code AjouterAllergie}, {@code ConsulterAllergie},
 * {@code ListerAllergiesParPatient}, {@code ResoudreAllergie}, {@code ModifierAllergie},
 * {@code SupprimerAllergie}.
 */
public interface AllergieUseCases {

    AllergieId enregistrer(EnregistrerAllergieCommande commande, OffsetDateTime maintenant);

    Allergie consulter(AllergieId allergieId, CentreId centreId);

    PagedResult<Allergie> listerParPatient(CentreId centreId, UUID patientId, int page, int size);

    Allergie resoudre(AllergieId allergieId, CentreId centreId, LocalDate dateResolution,
                      OffsetDateTime maintenant);

    Allergie modifier(ModifierAllergieCommande commande, OffsetDateTime maintenant);

    void supprimer(AllergieId allergieId, CentreId centreId);

    enum StatutCliniqueParam {
        ACTIF, RESOLU
    }

    record EnregistrerAllergieCommande(UUID patientId, CentreId centreId, String allergene,
                                       TypeAllergie type, String reaction, LocalDate dateDebut,
                                       LocalDate dateFin, String severite, String note) {
    }

    record ModifierAllergieCommande(AllergieId allergieId, CentreId centreId, String reaction,
                                    LocalDate dateDebut, LocalDate dateFin,
                                    StatutCliniqueParam statutClinique, String severite, String note) {
    }
}
