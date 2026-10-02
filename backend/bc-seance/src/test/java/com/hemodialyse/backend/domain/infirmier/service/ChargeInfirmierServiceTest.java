package com.hemodialyse.backend.domain.infirmier.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ChargeMensuelle;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.MATIN;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.SALLE;
import static com.hemodialyse.backend.domain.infirmier.service.PresenceTestData.SALLE_B;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChargeInfirmierServiceTest {

    /**
     * Octobre 2026 compte quatre lundis (5, 12, 19, 26) et quatre mercredis (7, 14, 21, 28).
     */
    private static final YearMonth OCTOBRE = YearMonth.of(2026, 10);

    private static ChargeInfirmier de(ChargeMensuelle charge, InfirmierRef i) {
        return charge.infirmiers().stream().filter(c -> c.infirmierId().equals(i.id())).findFirst().orElseThrow();
    }

    @Test
    void rotation_sessions_are_counted_on_open_days() {
        PresenceTestData t = new PresenceTestData();
        InfirmierRef a = t.infirmier("Amrani", false);
        t.affecter(a, SALLE, MATIN, JourSemaine.LUNDI, JourSemaine.MERCREDI);

        ChargeInfirmier c = de(ChargeInfirmierService.chargeMensuelle(t.build(), OCTOBRE), a);

        assertEquals(8, c.seances());
        assertEquals(0, c.remplacements());
        assertEquals(8, c.total());
    }

    @Test
    void closed_days_and_absences_reduce_the_sessions() {
        PresenceTestData t = new PresenceTestData().fermeture(LocalDate.of(2026, 10, 12));
        InfirmierRef a = t.infirmier("Amrani", false);
        t.affecter(a, SALLE, MATIN, JourSemaine.LUNDI)
                .absence(a, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));

        ChargeInfirmier c = de(ChargeInfirmierService.chargeMensuelle(t.build(), OCTOBRE), a);

        assertEquals(2, c.seances(), "lundis 5 et 26 seulement (12 férié, 19 en congé)");
        assertEquals(7, c.joursAbsence());
    }

    @Test
    void replacements_of_the_month_are_added_and_the_average_is_computed() {
        PresenceTestData t = new PresenceTestData();
        InfirmierRef a = t.infirmier("Amrani", false);
        InfirmierRef b = t.infirmier("Benali", false);
        t.affecter(a, SALLE, MATIN, JourSemaine.LUNDI);
        t.remplacer(LocalDate.of(2026, 10, 6), SALLE_B, MATIN, b);
        t.remplacer(LocalDate.of(2026, 11, 3), SALLE_B, MATIN, b);   // autre mois : ignoré

        ChargeMensuelle charge = ChargeInfirmierService.chargeMensuelle(t.build(), OCTOBRE);

        assertEquals(1, de(charge, b).remplacements());
        assertEquals(1, de(charge, b).total());
        assertEquals(4, de(charge, a).total());
        assertEquals(2.5, charge.moyenne());
        assertEquals("Amrani", charge.infirmiers().get(0).nom());
    }
}
