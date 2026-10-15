package com.hemodialyse.backend.contexts.comptabilite.doubles;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.CompteVue;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.LecturePlan;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Double en mémoire du plan comptable enregistré : même contrat que l'adaptateur JPA (voir les tests de contrat).
 */
public class ComptesEnMemoire implements CompteRepository, LecturePlan {

    private final Map<Cle, CompteComptable> comptes = new HashMap<>();

    @Override
    public long nombre(CentreId centre) {
        return comptes.keySet().stream().filter(c -> c.centre().equals(centre)).count();
    }

    @Override
    public Optional<CompteComptable> trouver(CentreId centre, NumeroDeCompte numero) {
        return Optional.ofNullable(comptes.get(new Cle(centre, numero)));
    }

    @Override
    public void enregistrer(CompteComptable compte) {
        comptes.put(new Cle(compte.centre(), compte.numero()), compte);
    }

    @Override
    public void supprimer(CentreId centre, NumeroDeCompte numero) {
        comptes.remove(new Cle(centre, numero));
    }

    @Override
    public PageDe<CompteVue> page(CentreId centre, String recherche, boolean actifsSeulement, int page, int taille) {
        String motif = recherche == null || recherche.isBlank() ? null : "%" + recherche.trim().toLowerCase(Locale.ROOT) + "%";
        List<CompteVue> tous = comptes.values().stream()
                .filter(c -> c.centre().equals(centre))
                .filter(c -> !actifsSeulement || c.actif())
                .filter(c -> motif == null || Like.correspond(motif, c.numero().valeur().toLowerCase(Locale.ROOT))
                        || Like.correspond(motif, c.libelle().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(c -> c.numero().valeur()))
                .map(CompteVue::depuis).toList();
        return PageDe.decoupee(tous, page, taille);
    }

    private record Cle(CentreId centre, NumeroDeCompte numero) {
    }
}
