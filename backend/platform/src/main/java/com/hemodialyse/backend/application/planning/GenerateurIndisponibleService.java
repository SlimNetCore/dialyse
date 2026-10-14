package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.planning.port.GenerateurImpactPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Alerte l'administration et le secrétariat quand un générateur de dialyse en service devient indisponible (maintenance,
 * attente de pièce, panne, réforme), avec les patients dont c'est la place habituelle : leurs séances sont à déplacer
 * (optimisation « Maintenance des générateurs »). Une alerte qui échoue ne fait jamais échouer le changement de statut.
 */
@Service
public class GenerateurIndisponibleService {

    private static final Logger log = LoggerFactory.getLogger(GenerateurIndisponibleService.class);
    private static final String GENERATEUR_DIALYSE = "GENERATEUR_DIALYSE";
    private static final String EN_SERVICE = "EN_SERVICE";

    private final GenerateurImpactPort impact;
    private final NotificationService notifications;

    public GenerateurIndisponibleService(GenerateurImpactPort impact, NotificationService notifications) {
        this.impact = impact;
        this.notifications = notifications;
    }

    /**
     * @param type       type de l'équipement (code) ; seul {@code GENERATEUR_DIALYSE} est signalé
     * @param statut     statut après le changement (code)
     * @param precedent  statut avant le changement ({@code null} à la création : rien à signaler)
     */
    public void signaler(String type, UUID centreId, UUID equipementId, String code, String statut, String precedent) {
        if (!GENERATEUR_DIALYSE.equals(type) || !EN_SERVICE.equals(precedent) || EN_SERVICE.equals(statut)) {
            return;
        }
        try {
            List<String> patients = impact.patientsPlacesSur(centreId, equipementId, LocalDate.now(ZoneOffset.UTC));
            notifications.notifyGenerateurIndisponible(centreId, code, statut, patients);
        } catch (RuntimeException e) {
            log.warn("[GMAO] Alerte d'indisponibilité du générateur {} impossible", code, e);
        }
    }
}
