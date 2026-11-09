package com.hemodialyse.backend.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CentresFournis;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL du Stock vers le langage publié d'Organisation (organisation.v1, O4 lot B, ADR 0108) : traducteur mince, aucun SQL. Le Stock ne peut pas
 * dépendre d'Organisation (cycle Maven) ; la plateforme, qui connaît les deux, fournit au Stock son port {@link CentresFournis}. Remplace
 * les lecteurs {@code CentresActifsLegacy}, {@code HorlogeDesCentresLegacy} et la liste des centres d'{@code OperationsDeStockJdbc}.
 * <p>
 * O4-R3 : le Stock garde SA définition de « centre actif » (variante stricte, un état non renseigné est exclu) ; les autres consommateurs
 * utilisent la variante large. L'écart est tracé (KB-ORG-09).
 */
@Component
public class CentresDuStockParOrganisation implements CentresFournis {

    private final AnnuaireDesCentres annuaire;

    public CentresDuStockParOrganisation(AnnuaireDesCentres annuaire) {
        this.annuaire = annuaire;
    }

    @Override
    public List<UUID> centresActifsAuSensStrict() {
        return annuaire.actifsAuSensStrict();
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
