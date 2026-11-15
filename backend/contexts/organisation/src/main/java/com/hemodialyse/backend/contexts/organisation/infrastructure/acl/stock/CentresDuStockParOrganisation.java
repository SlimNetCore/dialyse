package com.hemodialyse.backend.contexts.organisation.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CentresFournis;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL du Stock vers le langage publié d'Organisation (organisation.v1, O4 lot B, ADR 0108) : traducteur mince, aucun SQL. Le Stock ne peut pas
 * dépendre d'Organisation (cycle Maven) ; Organisation, qui dépend du Stock, héberge ce traducteur et fournit au Stock son port {@link CentresFournis} (ADR 0191). Remplace
 * les lecteurs {@code CentresActifsLegacy}, {@code HorlogeDesCentresLegacy} et la liste des centres d'{@code OperationsDeStockJdbc}.
 * <p>
 * KB-ORG-09 corrigé (ADR 0115) : le Stock utilise la même définition de « centre actif » que les autres consommateurs ({@code actifs()}).
 */
public class CentresDuStockParOrganisation implements CentresFournis {

    private final AnnuaireDesCentres annuaire;

    public CentresDuStockParOrganisation(AnnuaireDesCentres annuaire) {
        this.annuaire = annuaire;
    }

    @Override
    public List<UUID> centresActifs() {
        return annuaire.actifs();
    }

    @Override
    public List<UUID> tousLesCentres() {
        return annuaire.tous();
    }

    @Override
    public Optional<ZoneId> fuseauHoraireDe(UUID centre) {
        return annuaire.fuseauHoraireDe(centre);
    }
}
