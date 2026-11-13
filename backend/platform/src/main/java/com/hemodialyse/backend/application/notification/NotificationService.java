package com.hemodialyse.backend.application.notification;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TempsReelDeLaDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Publishes real-time events to WebSocket topics per center. Les alertes qui demandent une action
 * ({@link #DURABLES}) sont aussi enregistrées dans le journal du centre : une personne absente ou déconnectée au moment
 * de l'événement (panne d'un équipement, réforme proposée) les retrouve à sa connexion.
 * <p>
 * Chaque méthode {@code notifyXxx} ne fait que composer le contenu et le rôle ciblé ; la demande
 * ({@link DemandeDeNotification}, langage publié notifications.v1) est construite en un seul endroit ({@link #demande}).
 */
@Service
public class NotificationService implements DiffusionEnTempsReel {

    /**
     * Types d'alerte conservés dans le journal ; les autres évènements (création, mise à jour) ne valent que sur le
     * moment.
     */
    static final Set<String> DURABLES = Set.of(
            "ABSENCES_A_QUALIFIER", "REFORME_PROPOSEE", "LOTS_PROCHES_PEREMPTION");

    /**
     * Types de saisie de l'infirmier notifiés au médecin du centre (clé de traduction {@code NOTIFICATION.SAISIE.<type>}).
     */
    public static final Set<String> SAISIES_INFIRMIER = Set.of(
            "SEANCE_CREEE", "SEANCE_VALIDEE", "PARAMEDICAL", "CONSOMMABLE_AJOUT", "CONSOMMABLE_MODIF",
            "CONSOMMABLE_RETRAIT", "ANEMIE", "ABSENCE");

    private static final String CLE_DES_ROLES = "targetRoles";
    private static final String CLE_DU_DESTINATAIRE = "targetUserId";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messaging;
    private final TempsReelDeLaDirection directionRealtime;
    private final NotifierEnDeuxTemps notifierEnDeuxTemps;

    /**
     * {@code notifierEnDeuxTemps} (notifications.v1, ADR 0143) ne dépend pas du transport temps réel que ce service fournit : pas de cycle.
     */
    public NotificationService(SimpMessagingTemplate messaging, TempsReelDeLaDirection directionRealtime,
                               NotifierEnDeuxTemps notifierEnDeuxTemps) {
        this.messaging = messaging;
        this.directionRealtime = directionRealtime;
        this.notifierEnDeuxTemps = notifierEnDeuxTemps;
    }

    // Patient et couverture (fiche, PEC, attestations) et Dossier médical (prescription, observance) : catalogue chez les émetteurs (PL13a, ADR 0153)

    // Séance (créée, validée, volets, consommables, supprimée, déverrouillée, saisies de l'infirmier, séances à régulariser) : catalogue
    // dans contexts/seance (PL13b, ADR 0154). Reste ici la seule saisie ABSENCE, jusqu'à PL13d.

    // Planification (présence, libération des places, optimisation, replanification, séances déplacées, générateur indisponible, isolement,
    // roulement, remplacement, absences d'infirmier) : catalogue dans contexts/planification (PL13c, ADR 0155).

    /**
     * La saisie {@code ABSENCE} d'une absence de patient (seule saisie encore émise par la plateforme) est signalée au <b>médecin</b> du
     * centre dans son centre de notifications.
     *
     * @param type    l'une de {@link #SAISIES_INFIRMIER}
     * @param patient identité du patient concerné (peut être vide pour une saisie sans patient)
     * @param auteur  nom d'utilisateur de l'auteur de la saisie
     * @param date    date de la séance ou de l'absence concernée (ISO), facultative
     */
    public void notifySaisieInfirmier(UUID centerId, String type, UUID patientId, String patientNom,
                                      String patientPrenom, String auteur, String date) {
        if (!SAISIES_INFIRMIER.contains(type)) {
            throw new IllegalArgumentException("Type de saisie inconnu : " + type);
        }
        send(centerId, "SAISIE_INFIRMIER", contenu("saisie", type, "patientId", patientId == null ? "" : patientId.toString(),
                "patientNom", ouVide(patientNom), "patientPrenom", ouVide(patientPrenom), "auteur", ouVide(auteur),
                "date", ouVide(date), CLE_DES_ROLES, "MEDECIN"));
    }

    // ---------------------------------------------------------------- GMAO, absences de patients, stock

    /**
     * Une intervention a constaté l'état « à réformer » d'un équipement (P2, KB-GMAO-57) : les détenteurs de l'habilitation
     * {@code GMAO_REFORME} du centre sont prévenus qu'une réforme est à examiner. Jamais de réforme automatique (RG-GMA-063).
     * L'identifiant de l'alerte se déduit de l'équipement et de l'instant de l'événement : un événement rejoué (livraison au
     * moins une fois) ne crée pas de doublon. Contrairement aux autres alertes, si l'enregistrement durable échoue l'exception
     * remonte à l'appelant (l'écouteur d'événement) sans rien envoyer en temps réel : la publication reste incomplète et
     * l'événement est rejoué.
     */
    public void notifyReformeProposee(UUID centerId, UUID equipementId, String code, Instant survenuLe) {
        var payload = contenu("equipementId", equipementId.toString(), "code", code, "statut", "A_REFORMER",
                "message", "Équipement " + code + " : état constaté « à réformer » à la clôture d'une intervention. "
                        + "Aucune réforme n'est décidée automatiquement : consultez l'aide à la décision de maintenance.",
                CLE_DES_ROLES, "GMAO_REFORME");
        UUID id = UUID.nameUUIDFromBytes(("REFORME_PROPOSEE|" + equipementId + "|" + survenuLe).getBytes(StandardCharsets.UTF_8));
        send(centerId, "REFORME_PROPOSEE", payload, id);
    }

    /** Des absences de patients attendent leur qualification (motif) ; certaines dépassent le délai. */
    public void notifyAbsencesAQualifier(UUID centerId, long nbAQualifier, long nbEnRetard) {
        notifyAbsencesAQualifier(centerId, nbAQualifier, nbEnRetard, null);
    }

    /**
     * Variante à identifiant déterministe (un par centre et par jour du centre, calculé par l'appelant) : déjà enregistrée, l'alerte
     * n'est ni renvoyée ni dupliquée, même si deux instances du serveur la déclenchent ensemble.
     */
    public void notifyAbsencesAQualifier(UUID centerId, long nbAQualifier, long nbEnRetard, UUID idImpose) {
        send(centerId, "ABSENCES_A_QUALIFIER", contenu("nbAQualifier", String.valueOf(nbAQualifier),
                "nbEnRetard", String.valueOf(nbEnRetard), CLE_DES_ROLES, "ADMIN,SECRETAIRE,INFIRMIER,MEDECIN"), idImpose);
    }

    /**
     * Des lots en stock périment dans les 30 jours ou sont périmés (contrôle quotidien du Stock, RG-STK-061) : le pharmacien et
     * l'administrateur du centre en sont prévenus par une alerte à identifiant déterministe (une par centre et par jour du centre).
     */
    public void notifyLotsProchesPeremption(UUID centerId, long nbLots, long nbPerimes, LocalDate plusProche, UUID idImpose) {
        send(centerId, "LOTS_PROCHES_PEREMPTION", contenu("nbLots", String.valueOf(nbLots), "nbPerimes", String.valueOf(nbPerimes),
                "plusProche", plusProche == null ? "" : plusProche.toString(), CLE_DES_ROLES, "ADMIN,PHARMACIEN"), idImpose);
    }

    // ---------------------------------------------------------------- composition du contenu

    /** Paires clé/valeur (dans l'ordre) vers une table de hachage : la forme historique du contenu, donc du JSON émis. */
    private static HashMap<String, String> contenu(String... paires) {
        var contenu = new HashMap<String, String>();
        for (int i = 0; i < paires.length; i += 2) {
            contenu.put(paires[i], paires[i + 1]);
        }
        return contenu;
    }

    private static String ouVide(String valeur) {
        return valeur == null ? "" : valeur;
    }

    // ---------------------------------------------------------------- émission

    private void send(UUID centerId, String eventType, Map<String, String> payload) {
        send(centerId, eventType, payload, null, false);
    }

    private void send(UUID centerId, String eventType, Map<String, String> payload, UUID idImpose) {
        send(centerId, eventType, payload, idImpose, false);
    }

    /**
     * {@code idImpose} : identifiant déterministe d'une alerte durable ; déjà enregistrée, elle n'est ni renvoyée ni dupliquée.
     * {@code apresLeCommit} : le temps réel part après le commit de la transaction en cours (jamais d'alerte fantôme si elle est
     * annulée), même sans identifiant imposé.
     */
    private void send(UUID centerId, String eventType, Map<String, String> payload, UUID idImpose, boolean apresLeCommit) {
        emettre(demande(centerId, eventType, payload, idImpose), apresLeCommit);
    }

    /**
     * Seul point de construction de la demande publiée (notifications.v1). Le contenu reste celui d'avant (rôles et compte visé inclus,
     * lus par le front) ; l'audience en est déduite, elle n'est pas une seconde source.
     */
    private static DemandeDeNotification demande(UUID centerId, String eventType, Map<String, String> payload, UUID idImpose) {
        String roles = payload.getOrDefault(CLE_DES_ROLES, "");
        List<String> listeDesRoles = roles.isBlank() ? List.of() : Arrays.stream(roles.split(",")).map(String::trim).toList();
        return new DemandeDeNotification(centerId, eventType, payload,
                new Audience(listeDesRoles, payload.get(CLE_DU_DESTINATAIRE)), idImpose, DURABLES.contains(eventType));
    }

    private void emettre(DemandeDeNotification demande, boolean apresLeCommit) {
        UUID idImpose = demande.identifiantDeterministe();
        // premier temps, dans la transaction de l'émetteur : identifiant déjà journalisé = rien à faire ; journal en panne =
        // l'erreur remonte si l'identifiant est imposé (rien n'est envoyé, l'événement rejoué recommencera), sinon elle est rendue
        Optional<NotificationAEnvoyer> journalisee = notifierEnDeuxTemps.journaliser(demande);
        if (journalisee.isEmpty()) {
            return;
        }
        NotificationAEnvoyer aEnvoyer = journalisee.get();
        if (aEnvoyer.panneDuJournal() != null) {
            // le temps réel reste prioritaire : une base indisponible n'empêche pas d'alerter les connectés
            log.warn("[NOTIFICATION] Alerte {} non enregistrée pour le centre {}", demande.type(), demande.centre(),
                    aEnvoyer.panneDuJournal());
        }
        Runnable tempsReel = () -> diffuser(aEnvoyer.centre(), aEnvoyer.type(), aEnvoyer.contenu(), aEnvoyer.identifiant(),
                aEnvoyer.emisLe());
        // Alerte à identifiant imposé (P3 à P5) émise dans une transaction : le temps réel part APRÈS le commit, pour qu'une
        // transaction annulée (409, 422, échec du commit) ne diffuse jamais d'alerte fantôme (revue P5, constat 4).
        if ((idImpose != null || apresLeCommit) && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tempsReel.run();
                }
            });
        } else {
            tempsReel.run();
        }
    }

    /**
     * Transport temps réel (port {@link DiffusionEnTempsReel} du contexte Notifications, NOT1, ADR 0141) : le message STOMP du centre, puis
     * l'avertissement de la direction de la société. Les destinations et la forme du message sont celles d'avant le contexte.
     */
    @Override
    public void diffuser(UUID centerId, String eventType, Map<String, String> payload, UUID id, Instant maintenant) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("centerId", centerId.toString());
        // Alerte personnelle : le canal temps réel du centre ne porte que la cible ; le contenu (infirmier, date, salle, créneau)
        // se lit au journal, filtré par compte (revue P8, M2)
        event.put("payload", payload.containsKey(CLE_DU_DESTINATAIRE)
                ? Map.of(CLE_DES_ROLES, payload.getOrDefault(CLE_DES_ROLES, ""), CLE_DU_DESTINATAIRE, payload.get(CLE_DU_DESTINATAIRE))
                : payload);
        event.put("timestamp", maintenant.toString());
        if (id != null) event.put("id", id.toString());
        String destination = "/topic/center/" + centerId + "/events";
        messaging.convertAndSend(destination, (Object) event);
        // La direction de la société voit aussi ce changement, sans attendre le prochain balayage.
        directionRealtime.unCentreAChange(centerId);
    }
}
