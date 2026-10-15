package com.hemodialyse.backend.contexts.comptabilite.doubles;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Double en mémoire des règles de TVA : même contrat que l'adaptateur JDBC de {@code tva_types} (voir les tests de contrat),
 * dont l'arrondi du taux à deux décimales par la colonne.
 */
public class ReglesTvaEnMemoire implements RegleTvaRepository {

    private final List<RegleTva> regles = new ArrayList<>();

    private static RegleTva arrondie(RegleTva regle) {
        return RegleTva.reconstituee(regle.centre(), regle.typePrestation(), regle.taux().valeur().setScale(2, RoundingMode.HALF_UP),
                regle.exonere(), regle.debutValidite(), regle.finValidite(), regle.texteReference(), regle.active());
    }

    @Override
    public List<RegleTva> duCentre(CentreId centre) {
        return regles.stream().filter(r -> r.centre().equals(centre))
                .sorted(Comparator.comparing(RegleTva::debutValidite).reversed()).toList();
    }

    @Override
    public void ajouter(RegleTva regle) {
        regles.add(arrondie(regle));
    }

    /**
     * Une règle déjà en base, éventuellement désactivée par la Facturation.
     */
    public ReglesTvaEnMemoire enBase(RegleTva regle) {
        regles.add(regle);
        return this;
    }

    public int nombre() {
        return regles.size();
    }
}
