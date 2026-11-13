package com.hemodialyse.backend.contexts.documents.doubles;

import com.hemodialyse.backend.contexts.documents.application.GabaritPersonnaliseDuCentre;
import com.hemodialyse.backend.contexts.documents.application.GabaritsLivres;
import com.hemodialyse.backend.contexts.documents.application.ModelesDuCentre;
import com.hemodialyse.backend.contexts.documents.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersions;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersionsRepository;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCentre;
import com.hemodialyse.backend.contexts.documents.domain.VersionDeGabarit;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.VerificationDeGabarit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Doubles en mémoire des ports de la personnalisation des gabarits (DOC3). */
public final class DoublesDesGabarits {

    private DoublesDesGabarits() {
    }

    /** L'historique en mémoire : mêmes règles de stockage que la table (les versions d'un modèle d'un centre, avec leur contenu). */
    public static final class HistoriquesEnMemoire implements HistoriqueDesVersionsRepository, GabaritPersonnaliseDuCentre {
        private record Cle(UUID centre, UUID modele) {
        }

        private final Map<Cle, List<VersionDeGabarit>> versions = new HashMap<>();
        private final Map<UUID, String> contenus = new HashMap<>();
        public int enregistrements;

        @Override
        public HistoriqueDesVersions historique(UUID centre, UUID modele) {
            return HistoriqueDesVersions.de(centre, modele, versions.getOrDefault(new Cle(centre, modele), List.of()));
        }

        @Override
        public void enregistrer(HistoriqueDesVersions historique) {
            enregistrements++;
            versions.put(new Cle(historique.centre(), historique.modele()), new ArrayList<>(historique.versions()));
            historique.depotEnAttente().ifPresent(d -> contenus.put(d.version().id(), d.contenu()));
        }

        @Override
        public Optional<String> contenuDe(UUID centre, UUID modele, int numero) {
            return versions.getOrDefault(new Cle(centre, modele), List.of()).stream().filter(v -> v.numero() == numero)
                    .findFirst().map(v -> contenus.get(v.id()));
        }

        @Override
        public Optional<String> contenuEnVigueur(UUID centre, UUID modele) {
            return actif(centre, modele).map(Gabarit::contenu);
        }

        @Override
        public Optional<Gabarit> actif(UUID centre, UUID modele) {
            return versions.getOrDefault(new Cle(centre, modele), List.of()).stream().filter(VersionDeGabarit::active)
                    .reduce((a, b) -> a.numero() > b.numero() ? a : b).map(v -> new Gabarit(v.numero(), contenus.get(v.id())));
        }

        @Override
        public void retirerTout(UUID modele) {
            versions.keySet().removeIf(c -> c.modele().equals(modele));
        }
    }

    public static final class ModelesDuCentreEnMemoire implements ModelesDuCentre {
        private final List<ModeleDuCentre> modeles = new ArrayList<>();

        public ModeleDuCentre ajouter(ModeleDuCentre modele) {
            modeles.add(modele);
            return modele;
        }

        @Override
        public Optional<ModeleDuCentre> duCentre(UUID centre, UUID modele) {
            return modeles.stream().filter(m -> m.id().equals(modele) && m.centre().equals(centre)).findFirst();
        }
    }

    public static final class GabaritsLivresEnMemoire implements GabaritsLivres {
        private final Map<String, String> sources = new HashMap<>();

        public void livrer(String chemin, String contenu) {
            sources.put(chemin, contenu);
        }

        @Override
        public Optional<String> source(String cheminDuGabarit) {
            return Optional.ofNullable(sources.get(cheminDuGabarit));
        }
    }

    public static final class UniteImmediate implements UniteDeTravail {
        public int executions;

        @Override
        public <T> T executer(Supplier<T> action) {
            executions++;
            return action.get();
        }
    }

    /** Vérification de compilation : accepte tout, ou échoue avec l'erreur donnée. */
    public static final class VerificationFixe implements VerificationDeGabarit {
        public Exception echec;
        public final List<String> verifies = new ArrayList<>();

        @Override
        public void verifierQuIlSeCompile(String xml) throws Exception {
            verifies.add(xml);
            if (echec != null) throw echec;
        }
    }
}
