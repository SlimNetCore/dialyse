package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.IndisponibiliteDeGenerateur;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Alerte l'administration et le secrétariat quand un générateur en service devient indisponible (événement {@code gmao.v1}
 * {@code GenerateurDevenuIndisponible}), en citant les patients dont c'est la place habituelle. <b>Aucun déplacement
 * automatique</b> (KB-PLN-63). Idempotente : la clé est le générateur et le jour du centre, l'événement étant livré au moins une
 * fois. Une alerte qui échoue lève l'exception : l'événement est rejoué par la messagerie (le changement de statut, lui, est déjà
 * validé côté GMAO).
 */
public class AlerterUnGenerateurIndisponible {

    private final UniteDeTravail uniteDeTravail;
    private final PlacesDuCentreRepository places;
    private final NomsDesPatients noms;
    private final NotificationsDuPlacement notifications;
    private final HorlogeDesCentres horloge;

    public AlerterUnGenerateurIndisponible(UniteDeTravail uniteDeTravail, PlacesDuCentreRepository places, NomsDesPatients noms,
                                           NotificationsDuPlacement notifications, HorlogeDesCentres horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.places = places;
        this.noms = noms;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    /** @return vrai si l'alerte du jour a été émise par cet appel */
    public boolean executer(CentreId centre, GenerateurId generateur, String code, String statutPrecedent, String statut) {
        if (!IndisponibiliteDeGenerateur.aSignaler(statutPrecedent, statut)) return false;
        return uniteDeTravail.executer(() -> {
            LocalDate jour = horloge.aujourdhui(centre);
            PlacesDuCentre lues = places.charger(centre, new RessourcesDuCentre(null, null, null, null, null), jour);
            List<PatientId> concernes = lues.patientsSur(generateur);
            Map<PatientId, String> nomsParPatient = noms.de(centre, concernes);
            List<String> tries = concernes.stream().map(nomsParPatient::get).filter(n -> n != null)
                    .sorted(Comparator.naturalOrder()).toList();
            return notifications.generateurIndisponible(centre, generateur, code, statut, tries.size(),
                    IndisponibiliteDeGenerateur.citer(tries), jour);
        });
    }
}
