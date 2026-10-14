package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.EquipementExiste;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodesEquipementDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JPA du repository des équipements et des codes du centre. L'équipement et ses nouvelles lignes d'historique
 * s'écrivent dans la transaction du cas d'usage ; les écritures sont vidées tout de suite pour que la base refuse
 * (valeur trop longue, code déjà pris) pendant le cas d'usage, avant la publication des événements.
 */
public class EquipementRepositoryJpa implements EquipementRepository, CodesEquipementDuCentre, EquipementExiste {

    private final EquipementSpringData equipements;
    private final EquipementStatutHistoriqueSpringData historique;

    public EquipementRepositoryJpa(EquipementSpringData equipements, EquipementStatutHistoriqueSpringData historique) {
        this.equipements = equipements;
        this.historique = historique;
    }

    @Override
    public void enregistrer(Equipement equipement) {
        EquipementJpa entite = equipements.findById(equipement.id().valeur())
                .orElseGet(() -> new EquipementJpa(equipement.id().valeur()));
        EquipementMapper.recopier(equipement, entite);
        equipements.saveAndFlush(entite);
        for (ChangementDeStatut changement : equipement.tirerChangementsDeStatut()) {
            historique.save(EquipementMapper.versLigneDHistorique(UUID.randomUUID(), equipement, changement));
        }
        historique.flush();
    }

    @Override
    public Optional<Equipement> trouverParId(EquipementId id) {
        return equipements.findById(id.valeur()).map(EquipementMapper::versDomaine);
    }

    /** Un équipement supprimé logiquement existe encore (KB-GMAO-14, KB-GMAO-34). */
    @Override
    public boolean existe(EquipementId id) {
        return equipements.existsById(id.valeur());
    }

    @Override
    public List<ChangementDeStatut> historiqueDe(EquipementId id) {
        return historique.duPlusAncienAuPlusRecent(id.valeur()).stream().map(EquipementMapper::versChangement).toList();
    }

    @Override
    public boolean estUtilise(CentreId centre, CodeEquipement code) {
        return equipements.compterCodeNonSupprime(centre.valeur(), code.valeur()) > 0;
    }
}
