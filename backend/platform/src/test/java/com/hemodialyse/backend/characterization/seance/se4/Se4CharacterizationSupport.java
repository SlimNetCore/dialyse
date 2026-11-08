package com.hemodialyse.backend.characterization.seance.se4;

import com.hemodialyse.backend.characterization.seance.se3.Se3CharacterizationSupport;
import com.hemodialyse.backend.infrastructure.scheduling.SeanceRegularisationScheduler;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice SE4 (dérogation de régularisation : le déverrouillage d'une séance « créée » d'un jour passé,
 * {@code POST /api/v1/seances/{id}/deverrouiller-regularisation}, et le rappel quotidien à l'administrateur des séances oubliées,
 * {@code SeanceRegularisationScheduler} + {@code SeanceRegularisationService}), au-dessus du harnais SE3 (donc SE2 et SE1).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet) pour le déverrouillage, la validation et les lectures ; pour le rappel
 * (aucune route HTTP), le point d'entrée du travail planifié {@code rappelerCentre(centre, jourDeLaDemande)} du vrai bean, appelé avec
 * un jour FIXE nommé {@code <D+n>} (méthode de visibilité paquet, appelée par réflexion : aucun seam, aucun code de production
 * modifié). Observé : diffusions temps réel, journal durable {@code notification_evenement}, état SQL de {@code seances}.
 * <p>
 * Aucun test ne lit l'heure : les séances du rappel sont posées à des dates fixes de janvier 2026 (toujours passées au jour du centre).
 */
public abstract class Se4CharacterizationSupport extends Se3CharacterizationSupport {

    @Autowired
    private SeanceRegularisationScheduler rappelQuotidien;

    @Override
    protected String tranche() {
        return "se4";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", centre);
        }
        super.nettoyerSe1();
    }

    /** Lance le rappel d'un centre comme le fait le travail de 07:00, mais pour un jour de la demande choisi par le scénario. */
    protected void rappeler(UUID centre, LocalDate jourDeLaDemande) throws Exception {
        Method m = SeanceRegularisationScheduler.class.getDeclaredMethod("rappelerCentre", UUID.class, LocalDate.class);
        m.setAccessible(true);
        try {
            m.invoke(rappelQuotidien, centre, jourDeLaDemande);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("le rappel ne doit jamais propager d'exception", e.getCause());
        }
    }

    protected void viderLesRappels() {
        diffusions.clear();
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM notification_evenement WHERE center_id = ?", centre);
        }
    }

    /** Journal durable : type, rôles visés, charge (dates nommées par le scénario). */
    protected List<Map<String, Object>> journalDesNotifications() {
        return lignes("SELECT center_id, type, target_roles, payload FROM notification_evenement WHERE center_id IN ('" + CENTRE_A + "','"
                + CENTRE_B + "') ORDER BY center_id, type, payload");
    }

    /** Un rappel lu de bout en bout : diffusions (canal du centre) et journal durable. */
    protected Map<String, Object> rappelLu() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("diffusions", diffusionsLues());
        m.put("journal", journalDesNotifications());
        return m;
    }

    /** Date fixe du rappel : {@code <D+n>} ; la fenêtre examinée va de {@code n-7} à {@code n-1} (aujourd'hui exclu). */
    protected LocalDate jourDeLaDemande(int n) {
        return fixe(n);
    }

    protected int lignesDeDerogation() {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM derogations_regularisation", Integer.class);
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
