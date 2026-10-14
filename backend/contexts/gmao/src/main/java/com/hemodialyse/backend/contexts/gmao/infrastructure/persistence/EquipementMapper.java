package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.PrixAcquisition;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;

import java.util.UUID;

/** Mapping explicite agrégat / vue <-> modèle de persistance. */
final class EquipementMapper {

    private EquipementMapper() {
    }

    static Equipement versDomaine(EquipementJpa e) {
        return Equipement.reconstituer(new EquipementId(e.getId()), new CentreId(e.getCentreId()),
                new CodeEquipement(e.getCode()), e.getDesignation(), TypeEquipement.valueOf(e.getType()), e.getFabricant(),
                e.getModele(), e.getNumeroSerie(), e.getDateInstallation(), StatutEquipement.valueOf(e.getStatut()),
                e.getLocalisation(), e.getObservations(), SalleId.ouAucune(e.getSalleId()),
                PrixAcquisition.ouAucun(e.getPrixAcquisition()), e.getDateCreation(), e.getDateModification(),
                new UtilisateurId(e.getCreePar()), utilisateur(e.getModifiePar()));
    }

    /** Recopie l'état de l'agrégat dans le modèle de persistance (neuf ou géré). */
    static void recopier(Equipement equipement, EquipementJpa e) {
        e.setCode(equipement.code().valeur());
        e.setDesignation(equipement.designation());
        e.setType(equipement.type().name());
        e.setFabricant(equipement.fabricant());
        e.setModele(equipement.modele());
        e.setNumeroSerie(equipement.numeroSerie());
        e.setDateInstallation(equipement.dateInstallation());
        e.setCentreId(equipement.centreId().valeur());
        e.setStatut(equipement.statut().name());
        e.setLocalisation(equipement.localisation());
        e.setObservations(equipement.observations());
        e.setDateCreation(equipement.creeLe());
        e.setDateModification(equipement.modifieLe());
        e.setCreePar(equipement.creePar().valeur());
        e.setModifiePar(equipement.modifiePar() == null ? null : equipement.modifiePar().valeur());
        e.setSalleId(equipement.salleId() == null ? null : equipement.salleId().valeur());
        e.setPrixAcquisition(equipement.prixAcquisition() == null ? null : equipement.prixAcquisition().valeur());
        // KB-GMAO-34 reproduit : la suppression logique n'est pas portée par l'agrégat ; réécrire l'équipement l'efface.
        e.setDeletedAt(null);
    }

    static EquipementVue versVue(EquipementJpa e) {
        return new EquipementVue(e.getId(), e.getCentreId(), e.getCode(), e.getDesignation(),
                TypeEquipement.valueOf(e.getType()), e.getFabricant(), e.getModele(), e.getNumeroSerie(),
                e.getDateInstallation(), StatutEquipement.valueOf(e.getStatut()), e.getLocalisation(), e.getObservations(),
                e.getDateCreation(), e.getDateModification(), e.getCreePar(), e.getModifiePar(), e.getSalleId(),
                e.getPrixAcquisition());
    }

    static EquipementStatutHistoriqueJpa versLigneDHistorique(UUID id, Equipement equipement, ChangementDeStatut changement) {
        return new EquipementStatutHistoriqueJpa(id, equipement.id().valeur(), equipement.centreId().valeur(),
                changement.precedent() == null ? null : changement.precedent().name(), changement.nouveau().name(),
                changement.motif(), changement.le(), changement.par() == null ? null : changement.par().valeur());
    }

    static ChangementDeStatut versChangement(EquipementStatutHistoriqueJpa h) {
        return new ChangementDeStatut(h.getStatutPrecedent() == null ? null : StatutEquipement.valueOf(h.getStatutPrecedent()),
                StatutEquipement.valueOf(h.getStatutNouveau()), h.getMotif(), utilisateur(h.getChangedBy()), h.getChangedAt());
    }

    private static UtilisateurId utilisateur(UUID id) {
        return id == null ? null : new UtilisateurId(id);
    }
}
