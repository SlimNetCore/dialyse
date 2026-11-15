package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer.LigneImprimable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les lignes de l'état imprimé d'une ordonnance (DOC5d, ADR 0196) : ce que l'ancienne requête du gabarit joignait en SQL sur quatre tables
 * ({@code ordonnances} et {@code lignes_ordonnance} du Dossier médical, {@code patients} de Patient et couverture, {@code centers} de
 * l'Organisation) est composé ici par ports. Comme la jointure interne d'avant, rien n'est imprimé (liste vide) si l'ordonnance est
 * inconnue ou d'un autre centre, si son patient n'est pas un patient du centre, si le centre est inconnu ou si l'ordonnance n'a aucune
 * ligne. Aucun contrôle de statut ni de profil ici : la garde est celle de la règle d'impression publiée à Documents.
 */
public final class ComposerLesLignesDeLOrdonnance {

    private final OrdonnancesAImprimer ordonnances;
    private final IdentitesDesPatients identites;
    private final NomsDesCentres centres;

    public ComposerLesLignesDeLOrdonnance(OrdonnancesAImprimer ordonnances, IdentitesDesPatients identites, NomsDesCentres centres) {
        this.ordonnances = ordonnances;
        this.identites = identites;
        this.centres = centres;
    }

    /** @param ordonnance l'identifiant demandé ; {@code null} (jamais fourni) : rien à imprimer */
    public List<LigneDeLOrdonnanceImprimee> executer(UUID centre, UUID ordonnance) {
        if (centre == null || ordonnance == null) return List.of();
        CentreId centreId = new CentreId(centre);
        Optional<OrdonnancesAImprimer.OrdonnanceImprimable> trouvee = ordonnances.trouver(centreId, ordonnance);
        if (trouvee.isEmpty() || trouvee.get().lignes().isEmpty()) return List.of();
        OrdonnancesAImprimer.OrdonnanceImprimable o = trouvee.get();
        Optional<IdentitesDesPatients.Identite> identite = identites.duPatient(centreId, new PatientId(o.patient()));
        Optional<String> nomDuCentre = centres.duCentre(centreId);
        if (identite.isEmpty() || nomDuCentre.isEmpty()) return List.of();
        IdentitesDesPatients.Identite patient = identite.get();
        return o.lignes().stream().map(l -> new LigneDeLOrdonnanceImprimee(patient.nom(), patient.prenom(), patient.dateNaissance(),
                patient.numeroAssurance(), nomDuCentre.get(), o.numero(), o.datePrescription(), o.medecin(), medicament(l), l.posologie(),
                l.voie(), l.dureeJours(), l.quantite(), l.instructions())).toList();
    }

    /** Le libellé du code affiché, à défaut le nom saisi (même règle que l'ancien {@code COALESCE}). */
    static String medicament(LigneImprimable ligne) {
        return ligne.libelleDuCode() != null ? ligne.libelleDuCode() : ligne.libelle();
    }
}
