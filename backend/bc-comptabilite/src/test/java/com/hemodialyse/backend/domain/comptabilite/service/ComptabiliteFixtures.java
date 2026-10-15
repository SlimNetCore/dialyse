package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.port.ComptePayeurRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.ModelePieceRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.JournalRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort;
import com.hemodialyse.backend.domain.comptabilite.port.PeriodeComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
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

        /**
         * Le mapping est tenu par le contexte comptabilite (C2) : les tests le posent directement.
         */
        void save(MappingComptable mapping) {
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
     * Journaux d'un centre tels que le contexte comptabilite les publie : les journaux par défaut tant que le centre n'a rien
     * enregistré ({@link #definir} les enregistre d'abord, comme à la première modification).
     */
    static final class Journaux implements JournalRepositoryPort {
        final Map<UUID, List<Journal>> parCentre = new HashMap<>();

        @Override
        public List<Journal> findByCenter(UUID centerId) {
            List<Journal> enregistres = parCentre.getOrDefault(centerId, List.of());
            return enregistres.isEmpty() ? Journal.parDefaut()
                    : enregistres.stream().sorted(Comparator.comparing(j -> j.code().valeur())).toList();
        }

        void definir(UUID centerId, Journal journal) {
            List<Journal> liste = new ArrayList<>(parCentre.computeIfAbsent(centerId, c -> new ArrayList<>(Journal.parDefaut())));
            liste.removeIf(j -> j.code().equals(journal.code()));
            liste.add(journal);
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

    /**
     * Plan comptable tel que le contexte comptabilite le publie : le plan SCF de départ (complété des comptes du mapping)
     * tant que le centre n'a rien enregistré ; {@link #ajouter} et {@link #desactiver} l'enregistrent d'abord.
     */
    static final class Plan implements PlanComptableUseCase {
        private static final List<String> SCF = List.of("322", "401", "408", "411100", "411500", "421", "431", "4456",
                "44571", "512", "530", "602", "613", "615", "626", "631", "635", "657", "706", "757");

        private final Mappings mappings;
        private final Map<UUID, Map<String, Boolean>> enregistres = new HashMap<>();

        Plan(Mappings mappings) {
            this.mappings = mappings;
        }

        private Map<String, Boolean> du(UUID centerId) {
            return enregistres.computeIfAbsent(centerId, this::depart);
        }

        void ajouter(UUID centerId, String numero) {
            du(centerId).put(numero, true);
        }

        void desactiver(UUID centerId, String numero) {
            du(centerId).put(numero, false);
        }

        @Override
        public void exigerActifs(UUID centerId, java.util.Collection<String> numeros) {
            Map<String, Boolean> plan = enregistres.containsKey(centerId) ? du(centerId) : depart(centerId);
            for (String numero : numeros) {
                if (numero == null || numero.isBlank()) continue;
                if (!plan.getOrDefault(numero.trim(), false)) {
                    throw new BusinessException("COMPTE_INCONNU", "Le compte " + numero.trim() + " est inconnu");
                }
            }
        }

        private Map<String, Boolean> depart(UUID centerId) {
            Map<String, Boolean> depart = new HashMap<>();
            SCF.forEach(n -> depart.put(n, true));
            mappings.findByCenterId(centerId).comptes().forEach(n -> depart.putIfAbsent(n, true));
            return depart;
        }
    }

    /** Compte client propre à chaque payeur, tel que le contexte comptabilite le publie. */
    static final class ComptesPayeurs implements ComptePayeurRepositoryPort {
        final Map<String, String> comptes = new HashMap<>();

        private static String cle(UUID centerId, UUID payeurId) {
            return centerId + "|" + payeurId;
        }

        @Override
        public Optional<String> find(UUID centerId, UUID payeurId) {
            return Optional.ofNullable(comptes.get(cle(centerId, payeurId)));
        }

        void definir(UUID centerId, UUID payeurId, String compte) {
            comptes.put(cle(centerId, payeurId), compte);
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
        final ComptesPayeurs comptesPayeurs = new ComptesPayeurs();
        final Modeles modeles = new Modeles();
        final Plan plan = new Plan(mappings);
        final ComptabiliteService comptabilite = new ComptabiliteService(ecritures, mappings, periodes, null, comptesPayeurs);
        final PiecesComptablesService pieces = new PiecesComptablesService(modeles, ecritures, periodes, journaux, plan);
    }
}
