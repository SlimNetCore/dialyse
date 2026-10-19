package com.hemodialyse.backend.contexts.comptabilite.application.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.LigneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.NumeroDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.PageEcritures;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.StatutEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExporterUnJournalTest {
    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private static EcritureComptable ecriture(StatutEcriture statut) {
        return EcritureComptable.reconstituer(UUID.randomUUID(), CENTRE, CodeJournal.VE, DATE, DATE,
                NumeroDePiece.de("VE-2026-000001"), "Test", List.of(
                        ligne("411", "10.00", "0.00"), ligne("706", "0.00", "10.00")), statut, null, null,
                statut == StatutEcriture.EXPORTEE ? Instant.parse("2026-03-10T12:00:00Z") : null, null);
    }

    private static LigneEcriture ligne(String compte, String debit, String credit) {
        return new LigneEcriture(UUID.randomUUID(), compte, compte, new BigDecimal(debit), new BigDecimal(credit), null,
                List.of());
    }

    @Test
    void exporte_seulement_les_nouvelles_ecritures_et_date_les_validees() {
        Instant instant = Instant.parse("2026-03-11T12:00:00Z");
        DepotMemoire depot = new DepotMemoire(List.of(
                ecriture(StatutEcriture.VALIDEE), ecriture(StatutEcriture.BROUILLON),
                ecriture(StatutEcriture.EXPORTEE)));
        List<EcritureComptable> exporteesDansLeFichier = new ArrayList<>();
        ExporterUnJournal useCase = new ExporterUnJournal(depot, ecritures -> {
            exporteesDansLeFichier.addAll(ecritures);
            return new byte[]{1, 2, 3};
        }, new UniteDeTravail() {
            @Override
            public <T> T executer(Supplier<T> action) {
                return action.get();
            }
        },
                Clock.fixed(instant, ZoneOffset.UTC));

        byte[] fichier = useCase.executer(CENTRE, DATE, DATE, CodeJournal.VE);

        assertEquals(3, fichier.length);
        assertEquals(2, exporteesDansLeFichier.size());
        assertEquals(StatutEcriture.EXPORTEE, depot.ecritures.get(0).statut());
        assertEquals(instant, depot.ecritures.get(0).exporteeLe());
        assertEquals(StatutEcriture.BROUILLON, depot.ecritures.get(1).statut());
        assertEquals(StatutEcriture.EXPORTEE, depot.ecritures.get(2).statut());
    }

    private record DepotMemoire(List<EcritureComptable> ecritures) implements EcritureComptableRepository {
            private DepotMemoire(List<EcritureComptable> ecritures) {
                this.ecritures = new ArrayList<>(ecritures);
            }

            @Override
            public Optional<EcritureComptable> parId(CentreId centre, UUID id) {
                return ecritures.stream().filter(e -> e.centre().equals(centre) && e.id().equals(id)).findFirst();
            }

            @Override
            public Optional<EcritureComptable> parSource(CentreId centre, UUID source) {
                return Optional.empty();
            }

            @Override
            public PageEcritures rechercher(CentreId centre, LocalDate du, LocalDate au, CodeJournal journal,
                                            StatutEcriture statut, int page, int taille) {
                return new PageEcritures(List.of(), 0, page, taille);
            }

            @Override
            public void enregistrer(EcritureComptable ecriture) {
                int index = IntStream.range(0, this.ecritures.size())
                        .filter(i -> this.ecritures.get(i).id().equals(ecriture.id())).findFirst().orElse(-1);
                if (index >= 0) this.ecritures.set(index, ecriture);
            }

            @Override
            public List<EcritureComptable> duJournal(CentreId centre, CodeJournal journal, LocalDate du, LocalDate au) {
                return ecritures.stream().filter(e -> e.centre().equals(centre) && e.journal().equals(journal)
                        && !e.dateEcriture().isBefore(du) && !e.dateEcriture().isAfter(au)).toList();
            }
        }
}


