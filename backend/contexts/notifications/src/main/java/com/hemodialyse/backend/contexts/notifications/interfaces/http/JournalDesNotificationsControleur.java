package com.hemodialyse.backend.contexts.notifications.interfaces.http;

import com.hemodialyse.backend.contexts.notifications.application.ConsulterLeJournal;
import com.hemodialyse.backend.contexts.notifications.application.MarquerLesNotificationsLues;
import com.hemodialyse.backend.contexts.notifications.application.ToutMarquerLu;
import com.hemodialyse.backend.contexts.notifications.domain.PaginationDuJournal;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP du journal des notifications (NOT1, ex {@code NotificationRestController}) : MEMES trois URL, MEMES champs JSON,
 * MEMES codes ; session + garde de centre, aucun rôle particulier. Écarts décidés (ADR 0141) : page, taille, centre ou corps illisibles =
 * 400 et non 500 (KB-NOT1-2) ; seule une notification de l'audience de l'utilisateur peut être marquée lue (KB-NOT1-3).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class JournalDesNotificationsControleur {

    private final ConsulterLeJournal consulter;
    private final MarquerLesNotificationsLues marquer;
    private final ToutMarquerLu toutMarquer;

    public JournalDesNotificationsControleur(ConsulterLeJournal consulter, MarquerLesNotificationsLues marquer, ToutMarquerLu toutMarquer) {
        this.consulter = consulter;
        this.marquer = marquer;
        this.toutMarquer = toutMarquer;
    }

    @GetMapping
    public ResponseEntity<PagedResult<NotificationReponse>> lister(@RequestParam(required = false) UUID centerId,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "" + PaginationDuJournal.TAILLE_PAR_DEFAUT) int size) {
        var consultation = consulter.executer(centerId, page, size);
        List<NotificationReponse> items = consultation.notifications().items().stream()
                .map(n -> NotificationReponse.de(n, consultation.centre().valeur())).toList();
        var resultat = consultation.notifications();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PagedResult.of(items, resultat.total(), resultat.page(), resultat.size()));
    }

    @PostMapping("/lecture")
    public ResponseEntity<Void> marquerLues(@RequestParam(required = false) UUID centerId, @RequestBody LecturesDemande demande) {
        marquer.executer(centerId, demande.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lecture-tout")
    public ResponseEntity<Void> toutMarquerLu(@RequestParam(required = false) UUID centerId) {
        toutMarquer.executer(centerId);
        return ResponseEntity.noContent().build();
    }

    /** Corps de {@code POST /lecture} : les identifiants des notifications lues. */
    public record LecturesDemande(List<UUID> ids) {
    }
}
