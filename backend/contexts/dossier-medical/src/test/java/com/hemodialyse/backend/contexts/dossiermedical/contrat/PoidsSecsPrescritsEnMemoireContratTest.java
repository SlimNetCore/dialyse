package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.PoidsSecsPrescritsPublies;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Double en mémoire de {@link PoidsSecsPrescritsPublies} : même contrat que l'adapter JDBC (LEC-PLN-GMAO c, ADR 0180). */
class PoidsSecsPrescritsEnMemoireContratTest extends PoidsSecsPrescritsContrat {

    private record Posee(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe) {
    }

    private final List<Posee> posees = new ArrayList<>();

    /** Ordre des identifiants du SGBD : octets non signés. */
    private static int enOctetsNonSignes(UUID a, UUID b) {
        int c = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return c != 0 ? c : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    @Override
    protected PoidsSecsPrescritsPublies port() {
        return (centre, patient) -> posees.stream()
                .filter(p -> p.centre().equals(centre) && p.patient().equals(patient) && p.poidsSec() != null)
                .sorted(Comparator.comparing(Posee::date).reversed()
                        .thenComparing(Comparator.comparing(Posee::majLe, Comparator.nullsFirst(Comparator.<Instant>naturalOrder())).reversed())
                        .thenComparing((x, y) -> enOctetsNonSignes(y.id(), x.id())))
                .map(p -> new PoidsSecsPrescritsPublies.PoidsSecPrescrit(p.date(), p.poidsSec())).toList();
    }

    @Override
    protected void prescription(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe) {
        posees.add(new Posee(id, centre, patient, date, poidsSec, majLe));
    }
}
