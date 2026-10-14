package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.EquipementExiste;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.util.HashSet;
import java.util.Set;

/** Double in-memory du port {@link EquipementExiste} (même contrat que le repository JPA : {@code EquipementExisteContrat}). */
public class EquipementsEnMemoire implements EquipementExiste {

    private final Set<EquipementId> connus = new HashSet<>();

    public EquipementsEnMemoire avec(EquipementId... equipements) {
        connus.addAll(Set.of(equipements));
        return this;
    }

    public void supprimer(EquipementId equipement) {
        connus.remove(equipement);
    }

    @Override
    public boolean existe(EquipementId equipement) {
        return connus.contains(equipement);
    }
}
