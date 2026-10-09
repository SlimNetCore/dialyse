package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.port.ComptePayeurRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.ComptesPayeursUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.PayeursPort;
import com.hemodialyse.backend.domain.comptabilite.port.PayeursPort.Payeur;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service de domaine pur — compte client de chaque payeur d'un centre.
 */
public class ComptesPayeursService implements ComptesPayeursUseCase {

    private final PayeursPort payeurs;
    private final ComptePayeurRepositoryPort comptes;
    private final PlanComptableUseCase plan;

    public ComptesPayeursService(PayeursPort payeurs, ComptePayeurRepositoryPort comptes, PlanComptableUseCase plan) {
        this.payeurs = payeurs;
        this.comptes = comptes;
        this.plan = plan;
    }

    @Override
    public PagedResult<PayeurCompte> lister(UUID centerId, String recherche, int page, int size) {
        PagedResult<Payeur> pageDePayeurs = payeurs.lister(centerId, recherche, page, size);
        Map<UUID, String> parPayeur = comptes.findAll(centerId, pageDePayeurs.items().stream().map(Payeur::id).toList());
        List<PayeurCompte> items = pageDePayeurs.items().stream()
                .map(p -> new PayeurCompte(p.id(), p.code(), p.nom(), parPayeur.get(p.id()))).toList();
        return PagedResult.of(items, pageDePayeurs.total(), pageDePayeurs.page(), pageDePayeurs.size());
    }

    @Override
    public PayeurCompte definir(UUID centerId, UUID payeurId, String compte) {
        Payeur payeur = payeurs.trouver(centerId, payeurId)
                .orElseThrow(() -> new BusinessException("PAYEUR_INTROUVABLE", "Payeur introuvable dans ce centre"));
        String numero = compte == null || compte.isBlank() ? null : CompteComptable.normaliser(compte);
        if (numero == null) {
            comptes.delete(centerId, payeurId);
        } else {
            plan.exigerActifs(centerId, List.of(numero));
            comptes.save(centerId, payeurId, numero);
        }
        return new PayeurCompte(payeurId, payeur.code(), payeur.nom(), numero);
    }
}
