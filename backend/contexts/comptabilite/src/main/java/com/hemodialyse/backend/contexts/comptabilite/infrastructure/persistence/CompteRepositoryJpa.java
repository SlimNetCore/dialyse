package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.CompteVue;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.LecturePlan;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JPA du plan comptable enregistré ({@code comptes_comptables}) : écriture du port {@link CompteRepository} et page de
 * lecture {@link LecturePlan}. Chaque requête est bornée au centre.
 */
public class CompteRepositoryJpa implements CompteRepository, LecturePlan {

    private final CompteComptableSpringData donnees;

    public CompteRepositoryJpa(CompteComptableSpringData donnees) {
        this.donnees = donnees;
    }

    /**
     * Une ligne dont le numéro n'a pas le format fait échouer la lecture (400), comme au legacy (KB-CPT-06).
     */
    private static CompteVue vue(CompteComptableJpa ligne) {
        return CompteVue.depuis(versLeDomaine(ligne));
    }

    private static CompteComptable versLeDomaine(CompteComptableJpa ligne) {
        return CompteComptable.reconstitue(new CentreId(ligne.getCenterId()), NumeroDeCompte.de(ligne.getNumero()),
                ligne.getLibelle(), ligne.isActif());
    }

    @Override
    public long nombre(CentreId centre) {
        return donnees.countByCenterId(centre.valeur());
    }

    @Override
    public Optional<CompteComptable> trouver(CentreId centre, NumeroDeCompte numero) {
        return donnees.findByCenterIdAndNumero(centre.valeur(), numero.valeur()).map(CompteRepositoryJpa::versLeDomaine);
    }

    @Override
    public void enregistrer(CompteComptable compte) {
        CompteComptableJpa ligne = donnees.findByCenterIdAndNumero(compte.centre().valeur(), compte.numero().valeur())
                .orElseGet(() -> new CompteComptableJpa(UUID.randomUUID(), compte.centre().valeur(), compte.numero().valeur()));
        ligne.setLibelle(compte.libelle());
        ligne.setActif(compte.actif());
        donnees.saveAndFlush(ligne);
    }

    @Override
    public void supprimer(CentreId centre, NumeroDeCompte numero) {
        donnees.deleteByCenterIdAndNumero(centre.valeur(), numero.valeur());
    }

    @Override
    public PageDe<CompteVue> page(CentreId centre, String recherche, boolean actifsSeulement, int page, int taille) {
        PageRequest demande = PageRequest.of(page, taille);
        Page<CompteComptableJpa> resultat = recherche == null || recherche.isBlank()
                ? donnees.tous(centre.valeur(), actifsSeulement, demande)
                : donnees.rechercher(centre.valeur(), actifsSeulement, "%" + recherche.trim().toLowerCase(Locale.ROOT) + "%",
                demande);
        return new PageDe<>(resultat.getContent().stream().map(CompteRepositoryJpa::vue).toList(), resultat.getTotalElements(),
                page, taille);
    }
}
