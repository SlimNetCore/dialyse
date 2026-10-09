package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.port.ComptePayeurRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.CompteRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.ModelePieceRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PayeursPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
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
        public boolean existsByCompte(UUID centerId, String compte) {
            return du(centerId).stream().flatMap(e -> e.getLignes().stream())
                    .anyMatch(l -> l.getCompteSCF().equals(compte));
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

    static final class Comptes implements CompteRepositoryPort {
        final Map<UUID, Map<String, CompteComptable>> parCentre = new HashMap<>();

        private Map<String, CompteComptable> du(UUID centerId) {
            return parCentre.computeIfAbsent(centerId, c -> new java.util.TreeMap<>());
        }

        @Override
        public long count(UUID centerId) {
            return du(centerId).size();
        }

        @Override
        public PagedResult<CompteComptable> findPaged(UUID centerId, String recherche, boolean actifsSeulement, int page,
                                                      int size) {
            String filtre = recherche == null ? "" : recherche.trim().toLowerCase();
            List<CompteComptable> tous = du(centerId).values().stream()
                    .filter(c -> !actifsSeulement || c.actif())
                    .filter(c -> filtre.isEmpty() || c.numero().contains(filtre) || c.libelle().toLowerCase().contains(filtre))
                    .toList();
            int debut = Math.min(page * size, tous.size());
            return PagedResult.of(tous.subList(debut, Math.min(debut + size, tous.size())), tous.size(), page, size);
        }

        @Override
        public Optional<CompteComptable> find(UUID centerId, String numero) {
            return Optional.ofNullable(du(centerId).get(numero));
        }

        @Override
        public void save(UUID centerId, CompteComptable compte) {
            du(centerId).put(compte.numero(), compte);
        }

        @Override
        public void delete(UUID centerId, String numero) {
            du(centerId).remove(numero);
        }
    }

    static final class ComptesPayeurs implements ComptePayeurRepositoryPort {
        final Map<String, String> comptes = new HashMap<>();

        private static String cle(UUID centerId, UUID payeurId) {
            return centerId + "|" + payeurId;
        }

        @Override
        public Optional<String> find(UUID centerId, UUID payeurId) {
            return Optional.ofNullable(comptes.get(cle(centerId, payeurId)));
        }

        @Override
        public Map<UUID, String> findAll(UUID centerId, java.util.Collection<UUID> payeurIds) {
            Map<UUID, String> trouves = new HashMap<>();
            payeurIds.forEach(id -> find(centerId, id).ifPresent(c -> trouves.put(id, c)));
            return trouves;
        }

        @Override
        public void save(UUID centerId, UUID payeurId, String compte) {
            comptes.put(cle(centerId, payeurId), compte);
        }

        @Override
        public void delete(UUID centerId, UUID payeurId) {
            comptes.remove(cle(centerId, payeurId));
        }

        @Override
        public boolean existsByCompte(UUID centerId, String compte) {
            return comptes.entrySet().stream()
                    .anyMatch(e -> e.getKey().startsWith(centerId + "|") && e.getValue().equals(compte));
        }
    }

    static final class Payeurs implements PayeursPort {
        final Map<UUID, List<Payeur>> parCentre = new HashMap<>();

        void ajouter(UUID centerId, Payeur payeur) {
            parCentre.computeIfAbsent(centerId, c -> new ArrayList<>()).add(payeur);
        }

        @Override
        public PagedResult<Payeur> lister(UUID centerId, String recherche, int page, int size) {
            List<Payeur> tous = parCentre.getOrDefault(centerId, List.of());
            int debut = Math.min(page * size, tous.size());
            return PagedResult.of(tous.subList(debut, Math.min(debut + size, tous.size())), tous.size(), page, size);
        }

        @Override
        public Optional<Payeur> trouver(UUID centerId, UUID payeurId) {
            return parCentre.getOrDefault(centerId, List.of()).stream().filter(p -> p.id().equals(payeurId)).findFirst();
        }
    }

    static final class Modeles implements ModelePieceRepositoryPort {
        final Map<UUID, ModelePiece> parId = new LinkedHashMap<>();

        private java.util.stream.Stream<ModelePiece> du(UUID centerId) {
            return parId.values().stream().filter(m -> m.centerId().equals(centerId));
        }

        @Override
        public PagedResult<ModelePiece> findPaged(UUID centerId, boolean actifsSeulement, int page, int size) {
            List<ModelePiece> tous = du(centerId).filter(m -> !actifsSeulement || m.actif())
                    .sorted(Comparator.comparing(ModelePiece::code)).toList();
            int debut = Math.min(page * size, tous.size());
            return PagedResult.of(tous.subList(debut, Math.min(debut + size, tous.size())), tous.size(), page, size);
        }

        @Override
        public Optional<ModelePiece> findById(UUID centerId, UUID id) {
            return du(centerId).filter(m -> m.id().equals(id)).findFirst();
        }

        @Override
        public Optional<ModelePiece> findByCode(UUID centerId, String code) {
            return du(centerId).filter(m -> m.code().equals(code)).findFirst();
        }

        @Override
        public void save(ModelePiece modele) {
            parId.put(modele.id(), modele);
        }

        @Override
        public void delete(UUID centerId, UUID id) {
            findById(centerId, id).ifPresent(m -> parId.remove(id));
        }

        @Override
        public boolean existsByCompte(UUID centerId, String compte) {
            return du(centerId).flatMap(m -> m.lignes().stream()).anyMatch(l -> l.compte().equals(compte));
        }

        @Override
        public boolean existsByJournal(UUID centerId, JournalCode journal) {
            return du(centerId).anyMatch(m -> m.journal().equals(journal));
        }
    }

    /**
     * Tous les ports en mémoire et les services du domaine câblés dessus, comme le fait la couche applicative.
     */
    static final class Monde {
        final Ecritures ecritures = new Ecritures();
        final Mappings mappings = new Mappings();
        final Periodes periodes = new Periodes();
        final Journaux journaux = new Journaux();
        final Comptes comptes = new Comptes();
        final ComptesPayeurs comptesPayeurs = new ComptesPayeurs();
        final Payeurs payeurs = new Payeurs();
        final Modeles modeles = new Modeles();
        final PlanComptableService plan = new PlanComptableService(comptes, mappings, comptesPayeurs, modeles, ecritures);
        final JournauxService journauxService = new JournauxService(journaux, mappings, ecritures, modeles);
        final ComptabiliteService comptabilite = new ComptabiliteService(ecritures, mappings, new Fiscal(), periodes,
                null, journaux, comptesPayeurs, plan);
        final ComptesPayeursService comptesPayeursService = new ComptesPayeursService(payeurs, comptesPayeurs, plan);
        final PiecesComptablesService pieces = new PiecesComptablesService(modeles, ecritures, periodes, journaux, plan);
    }
}
