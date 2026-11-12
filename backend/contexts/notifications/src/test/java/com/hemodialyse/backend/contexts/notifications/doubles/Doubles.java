package com.hemodialyse.backend.contexts.notifications.doubles;

import com.hemodialyse.backend.contexts.notifications.application.AccesRefuse;
import com.hemodialyse.backend.contexts.notifications.application.Horloge;
import com.hemodialyse.backend.contexts.notifications.application.Identifiants;
import com.hemodialyse.backend.contexts.notifications.application.Session;
import com.hemodialyse.backend.contexts.notifications.application.SessionCourante;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Doubles simples des ports du contexte. */
public final class Doubles {

    private Doubles() {
    }

    /** Session du centre donné ; un centre demandé différent est refusé, comme la garde de centre de la session. */
    public static final class SessionFixe implements SessionCourante {
        private final Session session;

        public SessionFixe(CentreId centre, String utilisateur, Set<String> roles) {
            this.session = new Session(centre, utilisateur, roles);
        }

        @Override
        public Session ouvrir(UUID centreDemande) {
            if (centreDemande != null && !centreDemande.equals(session.centre().valeur()))
                throw new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session");
            return session;
        }
    }

    public static final class HorlogeFixe implements Horloge {
        private final Instant instant;

        public HorlogeFixe(Instant instant) {
            this.instant = instant;
        }

        @Override
        public Instant maintenant() {
            return instant;
        }
    }

    public static final class IdentifiantsSequentiels implements Identifiants {
        private long compteur;

        @Override
        public UUID suivant() {
            compteur++;
            return new UUID(0L, compteur);
        }
    }

    public static final class DiffusionEnregistree implements DiffusionEnTempsReel {
        public record Diffusion(UUID centre, String type, Map<String, String> contenu, UUID identifiant, Instant emisLe) {
        }

        public final List<Diffusion> diffusions = new ArrayList<>();

        @Override
        public void diffuser(UUID centre, String type, Map<String, String> contenu, UUID identifiant, Instant emisLe) {
            diffusions.add(new Diffusion(centre, type, contenu, identifiant, emisLe));
        }
    }
}
