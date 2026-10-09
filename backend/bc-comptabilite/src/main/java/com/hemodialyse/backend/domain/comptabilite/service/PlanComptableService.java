package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.port.ComptePayeurRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.CompteRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.port.ModelePieceRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Service de domaine pur — plan comptable d'un centre.
 * <p>
 * Un centre qui n'a rien paramétré dispose du plan de départ, complété des comptes que son paramétrage utilise déjà ;
 * ce plan est enregistré à sa première modification. Un compte utilisé par le paramétrage, par un payeur ou par un
 * modèle de pièce ne peut être ni désactivé ni supprimé ; un compte qui porte des écritures ne se supprime pas (il se
 * désactive).
 */
public class PlanComptableService implements PlanComptableUseCase {

    /**
     * Assez pour proposer tout le plan d'un centre dans une liste de choix.
     */
    private static final int TAILLE_MAX = 500;

    private final CompteRepositoryPort comptes;
    private final MappingComptablePort mappings;
    private final ComptePayeurRepositoryPort comptesPayeurs;
    private final ModelePieceRepositoryPort modeles;
    private final EcritureComptableRepositoryPort ecritures;

    public PlanComptableService(CompteRepositoryPort comptes, MappingComptablePort mappings,
                                ComptePayeurRepositoryPort comptesPayeurs, ModelePieceRepositoryPort modeles,
                                EcritureComptableRepositoryPort ecritures) {
        this.comptes = comptes;
        this.mappings = mappings;
        this.comptesPayeurs = comptesPayeurs;
        this.modeles = modeles;
        this.ecritures = ecritures;
    }

    @Override
    public PagedResult<CompteComptable> lister(UUID centerId, String recherche, boolean actifsSeulement, int page,
                                               int size) {
        int taille = Math.max(1, Math.min(size, TAILLE_MAX));
        int numero = Math.max(0, page);
        if (comptes.count(centerId) > 0) {
            return comptes.findPaged(centerId, recherche, actifsSeulement, numero, taille);
        }
        // plan de départ : quelques dizaines de lignes, filtrées et découpées ici
        String filtre = recherche == null ? "" : recherche.trim().toLowerCase(Locale.ROOT);
        List<CompteComptable> tous = planDeDepart(centerId).stream()
                .filter(c -> filtre.isEmpty() || c.numero().toLowerCase(Locale.ROOT).contains(filtre)
                        || c.libelle().toLowerCase(Locale.ROOT).contains(filtre))
                .toList();
        int debut = Math.min(numero * taille, tous.size());
        return PagedResult.of(tous.subList(debut, Math.min(debut + taille, tous.size())), tous.size(), numero, taille);
    }

    @Override
    public CompteComptable enregistrer(UUID centerId, CompteComptable compte) {
        materialiser(centerId);
        if (!compte.actif() && estUtilise(centerId, compte.numero())) {
            throw new BusinessException("COMPTE_UTILISE", "Le compte " + compte.numero()
                    + " est utilisé par le paramétrage, un payeur ou un modèle de pièce : remplacez-le avant de le désactiver");
        }
        comptes.save(centerId, compte);
        return compte;
    }

    @Override
    public void supprimer(UUID centerId, String numero) {
        materialiser(centerId);
        String compte = CompteComptable.normaliser(numero);
        if (comptes.find(centerId, compte).isEmpty()) {
            throw new BusinessException("COMPTE_INTROUVABLE", "Compte " + compte + " introuvable");
        }
        if (estUtilise(centerId, compte)) {
            throw new BusinessException("COMPTE_UTILISE", "Le compte " + compte
                    + " est utilisé par le paramétrage, un payeur ou un modèle de pièce : remplacez-le avant de le supprimer");
        }
        if (ecritures.existsByCompte(centerId, compte)) {
            throw new BusinessException("COMPTE_AVEC_ECRITURES",
                    "Le compte " + compte + " porte des écritures : désactivez-le au lieu de le supprimer");
        }
        comptes.delete(centerId, compte);
    }

    @Override
    public void exigerActifs(UUID centerId, Collection<String> numeros) {
        boolean enregistre = comptes.count(centerId) > 0;
        List<CompteComptable> depart = enregistre ? List.of() : planDeDepart(centerId);
        for (String numero : numeros) {
            if (numero == null || numero.isBlank()) continue;
            String compte = numero.trim();
            Optional<CompteComptable> trouve = enregistre ? comptes.find(centerId, compte)
                    : depart.stream().filter(c -> c.numero().equals(compte)).findFirst();
            if (trouve.isEmpty() || !trouve.get().actif()) {
                throw new BusinessException("COMPTE_INCONNU", "Le compte " + compte
                        + " n'existe pas dans le plan comptable du centre ou est désactivé");
            }
        }
    }

    private boolean estUtilise(UUID centerId, String numero) {
        return mappings.findByCenterId(centerId).comptes().contains(numero)
                || comptesPayeurs.existsByCompte(centerId, numero)
                || modeles.existsByCompte(centerId, numero);
    }

    /**
     * Plan d'un centre qui n'a rien enregistré : le plan SCF de départ, plus les comptes de son paramétrage qui n'y
     * figurent pas (un centre a pu les personnaliser avant l'arrivée du plan comptable).
     */
    private List<CompteComptable> planDeDepart(UUID centerId) {
        TreeMap<String, CompteComptable> plan = new TreeMap<>();
        CompteComptable.parDefaut().forEach(c -> plan.put(c.numero(), c));
        for (String numero : mappings.findByCenterId(centerId).comptes()) {
            plan.computeIfAbsent(numero, n -> new CompteComptable(n, "Compte " + n, true));
        }
        List<CompteComptable> tries = new ArrayList<>(plan.values());
        tries.sort(Comparator.comparing(CompteComptable::numero));
        return tries;
    }

    /**
     * Première modification : le plan de départ devient celui, enregistré, du centre.
     */
    private void materialiser(UUID centerId) {
        if (comptes.count(centerId) == 0) {
            planDeDepart(centerId).forEach(c -> comptes.save(centerId, c));
        }
    }
}
