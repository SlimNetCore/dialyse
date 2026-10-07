package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeEquipement;
import com.hemodialyse.backend.domain.planning.port.GenerateurImpactPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Alerte l'administration et le secrétariat quand un générateur de dialyse en service devient indisponible (maintenance,
 * attente de pièce, panne, réforme), avec les patients dont c'est la place habituelle : leurs séances sont à déplacer
 * (optimisation « Maintenance des générateurs »). Une alerte qui échoue ne fait jamais échouer le changement de statut.
 */
@Service
public class GenerateurIndisponibleService {

    private static final Logger log = LoggerFactory.getLogger(GenerateurIndisponibleService.class);

    private final GenerateurImpactPort impact;
    private final NotificationService notifications;

    public GenerateurIndisponibleService(GenerateurImpactPort impact, NotificationService notifications) {
        this.impact = impact;
        this.notifications = notifications;
    }

    /**
     * @param equipement équipement dans son nouveau statut
     * @param precedent  statut avant le changement ({@code null} à la création : rien à signaler)
     */
    public void signaler(Equipement equipement, StatutEquipement precedent) {
        if (equipement.getType() != TypeEquipement.GENERATEUR_DIALYSE
                || precedent != StatutEquipement.EN_SERVICE
                || equipement.getStatut() == StatutEquipement.EN_SERVICE) {
            return;
        }
        try {
            List<String> patients = impact.patientsPlacesSur(equipement.getCentreId(), equipement.getId(),
                    LocalDate.now(ZoneOffset.UTC));
            notifications.notifyGenerateurIndisponible(equipement.getCentreId(), equipement.getCode(),
                    equipement.getStatut().name(), patients);
        } catch (RuntimeException e) {
            log.warn("[GMAO] Alerte d'indisponibilité du générateur {} impossible", equipement.getCode(), e);
        }
    }
}
