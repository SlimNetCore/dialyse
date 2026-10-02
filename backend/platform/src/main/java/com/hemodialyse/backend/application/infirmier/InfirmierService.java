package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.AffectationInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort.CompteRef;
import com.hemodialyse.backend.domain.infirmier.port.InfirmierRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Référentiel des infirmiers d'un centre. Le matricule est unique par centre ; un infirmier désactivé n'apparaît plus
 * dans le planning de présence mais reste dans le référentiel. Désactiver ou réactiver une fiche reliée à un compte
 * utilisateur désactive ou réactive aussi ce compte.
 */
@Service
public class InfirmierService {

    private final InfirmierRepositoryPort infirmiers;
    private final AffectationInfirmierRepositoryPort affectations;
    private final ComptesInfirmierPort comptes;

    public InfirmierService(InfirmierRepositoryPort infirmiers, AffectationInfirmierRepositoryPort affectations,
                            ComptesInfirmierPort comptes) {
        this.infirmiers = infirmiers;
        this.affectations = affectations;
        this.comptes = comptes;
    }

    public PagedResult<InfirmierDetail> lister(UUID centerId, int page, int size) {
        PagedResult<Infirmier> paged = infirmiers.findPaged(centerId, page, size);
        Map<UUID, List<AffectationInfirmier>> parInfirmier = affectations
                .findByInfirmierIds(centerId, paged.items().stream().map(Infirmier::id).toList()).stream()
                .collect(Collectors.groupingBy(AffectationInfirmier::infirmierId));
        Map<UUID, CompteRef> parCompte = comptes
                .trouverParIds(paged.items().stream().map(Infirmier::userId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(CompteRef::id, Function.identity()));
        return PagedResult.of(paged.items().stream()
                        .map(i -> new InfirmierDetail(i, parInfirmier.getOrDefault(i.id(), List.of()),
                                i.userId() == null ? null : parCompte.get(i.userId()))).toList(),
                paged.total(), paged.page(), paged.size());
    }

    public InfirmierDetail creer(UUID centerId, String matricule, String nom, String prenom, String telephone,
                                 QualificationInfirmier qualification, boolean habiliteIsolement) {
        Infirmier infirmier = Infirmier.creer(centerId, matricule, nom, prenom, telephone, qualification, habiliteIsolement);
        verifierMatriculeLibre(centerId, infirmier.matricule(), null);
        return new InfirmierDetail(infirmiers.save(infirmier), List.of());
    }

    public InfirmierDetail modifier(UUID centerId, UUID id, String matricule, String nom, String prenom,
                                    String telephone, QualificationInfirmier qualification, boolean habiliteIsolement) {
        Infirmier modifie = charger(centerId, id)
                .modifier(matricule, nom, prenom, telephone, qualification, habiliteIsolement);
        verifierMatriculeLibre(centerId, modifie.matricule(), id);
        return detail(centerId, infirmiers.save(modifie));
    }

    public InfirmierDetail desactiver(UUID centerId, UUID id) {
        return changerEtat(centerId, id, false);
    }

    public InfirmierDetail reactiver(UUID centerId, UUID id) {
        return changerEtat(centerId, id, true);
    }

    /**
     * Fiche avec son roulement et son compte, après une modification extérieure (liaison d'un compte par exemple).
     */
    InfirmierDetail detail(UUID centerId, Infirmier infirmier) {
        CompteRef compte = infirmier.userId() == null ? null
                : comptes.trouverParIds(List.of(infirmier.userId())).stream().findFirst().orElse(null);
        return new InfirmierDetail(infirmier, affectations.findByInfirmierIds(centerId, List.of(infirmier.id())), compte);
    }

    private InfirmierDetail changerEtat(UUID centerId, UUID id, boolean actif) {
        Infirmier infirmier = charger(centerId, id);
        Infirmier maj = infirmiers.save(actif ? infirmier.reactiver() : infirmier.desactiver());
        if (maj.userId() != null) comptes.definirActif(maj.userId(), actif);
        return detail(centerId, maj);
    }

    private Infirmier charger(UUID centerId, UUID id) {
        return infirmiers.findById(centerId, id)
                .orElseThrow(() -> new BusinessException("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
    }

    private void verifierMatriculeLibre(UUID centerId, String matricule, UUID idCourant) {
        infirmiers.findByMatricule(centerId, matricule).filter(autre -> !autre.id().equals(idCourant)).ifPresent(autre -> {
            throw new BusinessException("INFIRMIER_MATRICULE_EXISTANT", "Ce matricule existe déjà dans le centre");
        });
    }

    /**
     * Infirmier avec son roulement et son compte utilisateur éventuel.
     */
    public record InfirmierDetail(Infirmier infirmier, List<AffectationInfirmier> affectations, CompteRef compte) {
        public InfirmierDetail(Infirmier infirmier, List<AffectationInfirmier> affectations) {
            this(infirmier, affectations, null);
        }
    }
}
