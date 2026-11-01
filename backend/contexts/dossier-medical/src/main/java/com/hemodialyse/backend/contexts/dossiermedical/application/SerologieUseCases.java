package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeSerologie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port d'entrée (use cases) du agrégat {@code Serologie}.
 * Cas d'usage : {@code EnregistrerSerologie}, {@code ConsulterSerologies}.
 * L'événement {@code SerologiePositiveDetectee} est publié par la couche application
 * après enregistrement d'un résultat positif.
 */
public interface SerologieUseCases {

    SerologieId enregistrer(EnregistrerSerologieCommande commande, Instant maintenant);

    Serologie consulter(SerologieId serologieId, CentreId centreId);

    PagedResult<Serologie> listerParPatient(CentreId centreId, UUID patientId, int page, int size);

    record EnregistrerSerologieCommande(UUID patientId, CentreId centreId, String prescripteurId,
                                        LocalDate datePrelevement, TypeSerologie typeSerologie,
                                        ResultatSerologie resultat, String valeur, String unite,
                                        Instant dateResultat, String laboratoire, String commentaire) {
    }
}
