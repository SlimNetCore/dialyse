package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;

/**
 * La demande de prise en charge saisie sur la fiche (RG-PEC-007), dans la transaction de la fiche. Enregistrer la fiche ne ramène
 * jamais une PEC validée à « créée » : une PEC existante (même identifiant, à défaut même demande) est conservée avec son statut
 * et son accord, seule une demande modifiée met la demande à jour ; sinon une PEC {@code CREE} est ajoutée. Une demande
 * incomplète (début ou fin absent) est ignorée en silence (reproduit, KB-PAT-35).
 * <p>
 * Un identifiant de PEC déjà pris par une autre PEC n'est plus une erreur 500 qui annule la fiche (décision du 2026-11-03) : la
 * fiche est enregistrée, la PEC n'est pas créée, et le refus est rendu avec le code stable
 * {@value #IDENTIFIANT_DEJA_PRIS} (409 à l'interface).
 */
public class PecDeLaFiche {

    public static final String IDENTIFIANT_DEJA_PRIS = "PEC_IDENTIFIANT_DEJA_UTILISE";

    private final PrisesEnCharge pecs;
    private final Identifiants identifiants;
    private final HorlogeDesCentres horloge;

    public PecDeLaFiche(PrisesEnCharge pecs, Identifiants identifiants, HorlogeDesCentres horloge) {
        this.pecs = pecs;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    /** @return le code du refus de la demande, vide si elle a été prise en compte (ou ignorée) */
    public Optional<String> enregistrer(CentreId centre, PatientId patient, CouvertureDeLaFiche.DemandeDePec demande) {
        if (demande == null || !demande.complete()) return Optional.empty();
        List<PriseEnCharge> existantes = pecs.duPatient(centre, patient);
        PriseEnCharge existante = demande.id() == null ? null
                : existantes.stream().filter(p -> demande.id().equals(p.id())).findFirst().orElse(null);
        if (existante == null) {
            existante = existantes.stream().filter(p -> p.demandeIdentique(demande.debut(), demande.fin(), demande.forfaitId()))
                    .findFirst().orElse(null);
        }
        if (existante == null) {
            if (demande.id() != null && pecs.identifiantPris(demande.id())) return Optional.of(IDENTIFIANT_DEJA_PRIS);
            pecs.enregistrer(PriseEnCharge.demander(demande.id() != null ? demande.id() : identifiants.nouveau(), patient, centre,
                    demande.debut(), demande.fin(), demande.forfaitId(), horloge.maintenant()));
            return Optional.empty();
        }
        if (existante.reviserDemande(demande.debut(), demande.fin(), demande.forfaitId())) pecs.enregistrer(existante);
        return Optional.empty();
    }
}
