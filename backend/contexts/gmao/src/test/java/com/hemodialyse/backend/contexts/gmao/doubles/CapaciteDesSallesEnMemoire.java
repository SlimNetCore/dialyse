package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.equipement.CapaciteDesSalles;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Double du port {@link CapaciteDesSalles} : les salles pleines refusent toute affectation d'un autre équipement. */
public class CapaciteDesSallesEnMemoire implements CapaciteDesSalles {

    public static class SalleSaturee extends IllegalStateException {
        public SalleSaturee() {
            super("Salle à sa capacité maximale");
        }
    }

    private final Set<SalleId> pleines = new HashSet<>();
    public final List<String> verifications = new ArrayList<>();

    public CapaciteDesSallesEnMemoire saturee(SalleId salle) {
        pleines.add(salle);
        return this;
    }

    @Override
    public void verifierAffectation(CentreId centre, SalleId salle, EquipementId equipement) {
        verifications.add(salle + "/" + equipement);
        if (salle != null && pleines.contains(salle)) throw new SalleSaturee();
    }
}
