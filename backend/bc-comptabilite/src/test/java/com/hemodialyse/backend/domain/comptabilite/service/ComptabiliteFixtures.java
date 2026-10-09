package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.JournalRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort;
import com.hemodialyse.backend.domain.comptabilite.port.ParametrageFiscalPort;
import com.hemodialyse.backend.domain.comptabilite.port.PeriodeComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ports en mémoire pour les tests du domaine comptable (aucune base).
 */
final class ComptabiliteFixtures {

    private ComptabiliteFixtures() {
    }

    static final class Ecritures implements EcritureComptableRepositoryPort {
        final Map<UUID, EcritureComptable> parId = new LinkedHashMap<>();

        @Override
        public void save(EcritureComptable ecriture) {
            parId.put(ecriture.getId(), ecriture);
        }

        @Override
        public void saveAll(List<EcritureComptable> ecritures) {
            ecritures.forEach(this::save);
        }

        @Override
        public Optional<EcritureComptable> findById(UUID id, UUID centerId) {
            return Optional.ofNullable(parId.get(id)).filter(e -> e.getCenterId().equals(centerId));
        }

        @Override
        public Optional<EcritureComptable> findBySourceId(UUID sourceId, UUID centerId) {
            return parId.values().stream()
                    .filter(e -> e.getCenterId().equals(centerId) && sourceId.equals(e.getSourceId())).findFirst();
        }

        @Override
        public boolean existsByJournal(UUID centerId, JournalCode journalCode) {
            return parId.values().stream()
                    .anyMatch(e -> e.getCenterId().equals(centerId) && e.getJournalCode().equals(journalCode));
        }

        @Override
        public PagedResult<EcritureComptable> findByCenterAndPeriod(UUID centerId, LocalDate from, LocalDate to,
                                                                    JournalCode journalCode, StatutEcriture statut,
                                                                    int page, int size) {
            return PagedResult.of(du(centerId), parId.size(), page, size);
        }

        @Override
        public List<EcritureComptable> findForExport(UUID centerId, LocalDate from, LocalDate to, JournalCode journalCode) {
            return du(centerId);
        }

        @Override
        public String nextNumeroPiece(UUID centerId, JournalCode journalCode, int year) {
            long deja = parId.values().stream()
                    .filter(e -> e.getCenterId().equals(centerId) && e.getJournalCode().equals(journalCode)).count();
            return journalCode.valeur() + "-" + year + "-" + String.format("%06d", deja + 1);
        }

        List<EcritureComptable> du(UUID centerId) {
            return parId.values().stream().filter(e -> e.getCenterId().equals(centerId)).toList();
        }
    }

    static final class Mappings implements MappingComptablePort {
        final Map<UUID, MappingComptable> parCentre = new HashMap<>();

        @Override
        public MappingComptable findByCenterId(UUID centerId) {
            return parCentre.getOrDefault(centerId, MappingComptable.defaultFor(centerId));
        }

        @Override
        public void save(MappingComptable mapping) {
            parCentre.put(mapping.centerId(), mapping);
        }
    }

    static final class Periodes implements PeriodeComptableRepositoryPort {
        final Set<YearMonth> cloturees = new HashSet<>();

        @Override
        public boolean isClotured(UUID centerId, YearMonth periode) {
            return cloturees.contains(periode);
        }

        @Override
        public void cloturer(UUID centerId, YearMonth periode, String userId) {
            cloturees.add(periode);
        }
    }

    /**
     * Aucune règle de TVA : les prestations sont exonérées.
     */
    static final class Fiscal implements ParametrageFiscalPort {
        @Override
        public Optional<RegleTVA> findActiveAt(UUID centerId, String typePrestation, LocalDate date) {
            return Optional.empty();
        }

        @Override
        public List<RegleTVA> findAll(UUID centerId) {
            return List.of();
        }

        @Override
        public void save(UUID centerId, RegleTVA regle) {
            throw new UnsupportedOperationException();
        }
    }

    static final class Journaux implements JournalRepositoryPort {
        final Map<UUID, List<Journal>> parCentre = new HashMap<>();

        @Override
        public List<Journal> findByCenter(UUID centerId) {
            return parCentre.getOrDefault(centerId, List.of()).stream()
                    .sorted(Comparator.comparing(j -> j.code().valeur())).toList();
        }

        @Override
        public void save(UUID centerId, Journal journal) {
            List<Journal> liste = new ArrayList<>(parCentre.getOrDefault(centerId, List.of()));
            liste.removeIf(j -> j.code().equals(journal.code()));
            liste.add(journal);
            parCentre.put(centerId, liste);
        }

        @Override
        public void delete(UUID centerId, JournalCode code) {
            List<Journal> liste = new ArrayList<>(parCentre.getOrDefault(centerId, List.of()));
            liste.removeIf(j -> j.code().equals(code));
            parCentre.put(centerId, liste);
        }
    }

    static final class Stock implements OperationsStockPort {
        final List<Reception> receptions = new ArrayList<>();
        final List<SortiesDuJour> sorties = new ArrayList<>();
        final List<Inventaire> inventaires = new ArrayList<>();

        @Override
        public List<UUID> centres() {
            return List.of();
        }

        @Override
        public List<Reception> receptions(UUID centerId, LocalDate du, LocalDate au) {
            return receptions.stream().filter(r -> !r.date().isBefore(du) && !r.date().isAfter(au)).toList();
        }

        @Override
        public List<SortiesDuJour> sorties(UUID centerId, LocalDate du, LocalDate au) {
            return sorties.stream().filter(s -> !s.jour().isBefore(du) && !s.jour().isAfter(au)).toList();
        }

        @Override
        public List<Inventaire> inventaires(UUID centerId, LocalDate du, LocalDate au) {
            return inventaires.stream().filter(i -> !i.date().isBefore(du) && !i.date().isAfter(au)).toList();
        }
    }
}
