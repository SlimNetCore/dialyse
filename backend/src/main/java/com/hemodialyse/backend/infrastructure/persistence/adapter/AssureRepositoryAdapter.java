package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AssureJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AssureJpaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AssureRepositoryAdapter implements AssureRepositoryPort {
    private final AssureJpaRepository jpa;

    public AssureRepositoryAdapter(AssureJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "patient.assure.byNumero", key = "#assure.numeroAssurance"),
            @CacheEvict(cacheNames = "patient.assure.searchByCenter", allEntries = true)
    })
    public Assure save(Assure assure) {
        AssureJpaEntity e = new AssureJpaEntity();
        e.setNumeroAssurance(assure.getNumeroAssurance());
        e.setCenterId(assure.getCenterId());
        e.setNom(assure.getNom());
        e.setPrenom(assure.getPrenom());
        e.setSexe(assure.getSexe());
        e.setDateNaissance(assure.getDateNaissance());
        e.setTelPersonnel(assure.getTelPersonnel());
        e.setTelMobile(assure.getTelMobile());
        e.setTelBureau(assure.getTelBureau());
        e.setAdresse(assure.getAdresse());
        e.setGroupeSanguin(assure.getGroupeSanguin());
        e.setCreatedAt(assure.getCreatedAt());
        return toDomain(jpa.save(e));
    }

    @Override
    @Cacheable(cacheNames = "patient.assure.byNumero", key = "#numeroAssurance")
    public Optional<Assure> findByNumeroAssurance(String numeroAssurance) {
        return jpa.findById(numeroAssurance).map(this::toDomain);
    }

    @Override
    @Cacheable(cacheNames = "patient.assure.searchByCenter", key = "#centerId.value().toString() + ':' + (#query == null ? '' : #query.toLowerCase())")
    public List<Assure> searchByCenter(CenterId centerId, String query) {
        return jpa.searchByCenter(centerId.value(), query).stream().map(this::toDomain).toList();
    }

    private Assure toDomain(AssureJpaEntity e) {
        Assure a = new Assure();
        a.setNumeroAssurance(e.getNumeroAssurance());
        a.setCenterId(e.getCenterId());
        a.setNom(e.getNom());
        a.setPrenom(e.getPrenom());
        a.setSexe(e.getSexe());
        a.setDateNaissance(e.getDateNaissance());
        a.setTelPersonnel(e.getTelPersonnel());
        a.setTelMobile(e.getTelMobile());
        a.setTelBureau(e.getTelBureau());
        a.setAdresse(e.getAdresse());
        a.setGroupeSanguin(e.getGroupeSanguin());
        a.setCreatedAt(e.getCreatedAt());
        return a;
    }
}


