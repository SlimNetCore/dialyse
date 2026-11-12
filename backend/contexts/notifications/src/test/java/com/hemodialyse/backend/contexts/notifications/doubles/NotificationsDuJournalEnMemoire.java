package com.hemodialyse.backend.contexts.notifications.doubles;

import com.hemodialyse.backend.contexts.notifications.application.NotificationDuJournal;
import com.hemodialyse.backend.contexts.notifications.application.NotificationsDuJournal;
import com.hemodialyse.backend.contexts.notifications.domain.Notification;
import com.hemodialyse.backend.contexts.notifications.domain.NotificationId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Double en mémoire du journal : passe le même jeu de tests de contrat que l'adapter JDBC. */
public class NotificationsDuJournalEnMemoire implements NotificationsDuJournal {

    private final Map<NotificationId, Notification> notifications = new LinkedHashMap<>();
    private final Set<String> lectures = new java.util.HashSet<>();
    public RuntimeException panne;

    public int nombre() {
        return notifications.size();
    }

    public int lecturesDe(String utilisateur) {
        return (int) lectures.stream().filter(l -> l.endsWith("|" + utilisateur)).count();
    }

    @Override
    public void enregistrer(Notification notification) {
        if (panne != null) throw panne;
        if (notifications.containsKey(notification.id())) throw new IllegalStateException("clé primaire déjà présente");
        notifications.put(notification.id(), notification);
    }

    @Override
    public boolean existe(NotificationId id) {
        return notifications.containsKey(id);
    }

    @Override
    public PagedResult<NotificationDuJournal> lister(CentreId centre, String utilisateur, Set<String> roles, Instant depuis, int page, int taille) {
        List<Notification> visibles = visibles(centre, utilisateur, roles, depuis);
        List<NotificationDuJournal> tranche = visibles.stream().skip((long) page * taille).limit(taille)
                .map(n -> new NotificationDuJournal(n.id(), n.type(), n.contenu(), n.creeLe(), lectures.contains(cle(n.id(), utilisateur))))
                .toList();
        return PagedResult.of(tranche, visibles.size(), page, taille);
    }

    @Override
    public void marquerLues(CentreId centre, String utilisateur, Set<String> roles, Collection<NotificationId> ids, Instant quand) {
        for (NotificationId id : ids) {
            Notification n = notifications.get(id);
            if (n != null && n.centre().equals(centre) && n.estVisiblePar(roles, utilisateur)) lectures.add(cle(id, utilisateur));
        }
    }

    @Override
    public void toutMarquerLu(CentreId centre, String utilisateur, Set<String> roles, Instant depuis, Instant quand) {
        visibles(centre, utilisateur, roles, depuis).forEach(n -> lectures.add(cle(n.id(), utilisateur)));
    }

    @Override
    public int purger(Instant avant) {
        List<NotificationId> anciennes = notifications.values().stream().filter(n -> n.creeLe().isBefore(avant)).map(Notification::id).toList();
        anciennes.forEach(id -> {
            notifications.remove(id);
            lectures.removeIf(l -> l.startsWith(id.valeur() + "|"));
        });
        return anciennes.size();
    }

    private List<Notification> visibles(CentreId centre, String utilisateur, Set<String> roles, Instant depuis) {
        List<Notification> visibles = new ArrayList<>(notifications.values().stream()
                .filter(n -> n.centre().equals(centre) && !n.creeLe().isBefore(depuis) && n.estVisiblePar(roles, utilisateur)).toList());
        visibles.sort(Comparator.comparing(Notification::creeLe).reversed().thenComparing(n -> n.id().valeur()));
        return visibles;
    }

    private static String cle(NotificationId id, String utilisateur) {
        return id.valeur() + "|" + utilisateur;
    }
}
