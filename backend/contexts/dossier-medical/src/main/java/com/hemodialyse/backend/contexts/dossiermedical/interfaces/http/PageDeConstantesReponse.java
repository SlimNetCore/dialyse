package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ConstantesDeSeance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeConstantes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Enveloppe JSON du legacy : {@code items}, {@code total}, {@code page}, {@code size}. */
public record PageDeConstantesReponse(List<Point> items, long total, int page, int size) {

    /** Un point de la vue longitudinale : mêmes noms de champs que le legacy ({@code taAvant}, {@code taApres}...). */
    public record Point(UUID seanceId, LocalDate dateSeance, BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String taAvant,
                        String taApres, Integer debitSangMlMin, BigDecimal ultrafiltrationMl, Integer dureeMinutes) {
        static Point de(ConstantesDeSeance c) {
            var m = c.constantes();
            return new Point(c.seance().valeur(), c.dateSeance(), m.poidsAvantKg(), m.poidsApresKg(), m.tensionAvant(),
                    m.tensionApres(), m.debitSangMlMin(), m.ultrafiltrationMl(), m.dureeMinutes());
        }
    }

    static PageDeConstantesReponse de(PageDeConstantes page) {
        return new PageDeConstantesReponse(page.items().stream().map(Point::de).toList(), page.total(), page.page(), page.size());
    }
}
