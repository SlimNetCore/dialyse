package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Planning de présence des infirmiers d'une semaine : modèle de document
 * {@value ModeleDocumentCatalog#PLANNING_PRESENCE_INFIRMIERS}, imprimé via {@link ModeleDocumentPrinter}. Le modèle
 * lit le roulement, les absences et les remplacements du centre pour les sept dates passées en paramètres.
 */
@Service
public class PresenceInfirmierReportService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private final ModeleDocumentPrinter printer;

    public PresenceInfirmierReportService(ModeleDocumentPrinter printer) {
        this.printer = printer;
    }

    static Map<String, Object> params(LocalDate debut) {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        for (int i = 0; i < 7; i++) {
            p.put("DATE_" + i, Date.valueOf(debut.plusDays(i)));
        }
        p.put("SEMAINE_LABEL", "Semaine du " + JOUR.format(debut) + " au " + JOUR.format(debut.plusDays(6)));
        return p;
    }

    /**
     * @param date un jour de la semaine voulue (semaine du dimanche au samedi)
     */
    public ModeleDocumentPrinter.Document imprimer(UUID centreId, LocalDate date) {
        return printer.print(centreId, ModeleDocumentCatalog.PLANNING_PRESENCE_INFIRMIERS,
                params(PlanningSemaineService.debutSemaine(date)));
    }
}
