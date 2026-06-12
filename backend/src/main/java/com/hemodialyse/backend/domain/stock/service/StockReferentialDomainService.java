package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Emplacement;
import com.hemodialyse.backend.domain.stock.model.Fournisseur;
import com.hemodialyse.backend.domain.stock.port.EmplacementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.FournisseurRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockReferentialUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class StockReferentialDomainService implements StockReferentialUseCase {

    private final FournisseurRepositoryPort fournisseurRepo;
    private final EmplacementRepositoryPort emplacementRepo;

    public StockReferentialDomainService(FournisseurRepositoryPort fournisseurRepo,
                                         EmplacementRepositoryPort emplacementRepo) {
        this.fournisseurRepo = fournisseurRepo;
        this.emplacementRepo = emplacementRepo;
    }

    @Override
    public Fournisseur createFournisseur(CenterId centerId, String code, String raisonSociale,
                                         String contact, String telephone, String email) {
        if (raisonSociale == null || raisonSociale.isBlank()) {
            throw new IllegalArgumentException("La raison sociale est obligatoire");
        }
        return fournisseurRepo.save(Fournisseur.create(centerId.value(), code, raisonSociale, contact, telephone, email));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Fournisseur> listFournisseurs(CenterId centerId) {
        return fournisseurRepo.findAllActive(centerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Fournisseur> searchFournisseurs(CenterId centerId, String query) {
        if (query == null || query.isBlank()) {
            return fournisseurRepo.findAllActive(centerId);
        }
        return fournisseurRepo.search(centerId, query.trim());
    }

    @Override
    public Emplacement createEmplacement(CenterId centerId, String code, String libelle) {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libelle de l'emplacement est obligatoire");
        }
        return emplacementRepo.save(Emplacement.create(centerId.value(), code, libelle));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Emplacement> listEmplacements(CenterId centerId) {
        return emplacementRepo.findAllActive(centerId);
    }
}

