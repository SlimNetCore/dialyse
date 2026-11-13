package com.hemodialyse.backend.contexts.documents.doubles;

import com.hemodialyse.backend.contexts.documents.application.AccesRefuse;
import com.hemodialyse.backend.contexts.documents.application.IdentitesImprimables;
import com.hemodialyse.backend.contexts.documents.application.ModeleEnDoublon;
import com.hemodialyse.backend.contexts.documents.application.ModelesDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.DefinitionDeModele;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCatalogue;
import com.hemodialyse.backend.contexts.documents.domain.RechercheDeModeles;
import com.hemodialyse.backend.contexts.documents.application.SessionCourante;
import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.ReglesDImpressionDesTypesSensibles;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doubles en mémoire des ports du contexte Documents. */
public final class Doubles {

    private Doubles() {
    }

    /** Session d'un centre : refuse tout autre centre, accepte l'absence de centre demandé. */
    public static final class SessionFixe implements SessionCourante {
        private final UUID centre;
        public final List<UUID> demandes = new ArrayList<>();

        public SessionFixe(UUID centre) {
            this.centre = centre;
        }

        @Override
        public CentreId exigerLeCentre(UUID centreDemande) {
            demandes.add(centreDemande);
            if (centreDemande != null && !centreDemande.equals(centre)) throw new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session");
            return new CentreId(centre);
        }

        @Override
        public String utilisateur() {
            return "admin-fictif";
        }
    }

    /** Les modèles en mémoire : même sélection que la base (actif le plus récent du centre, type comparé sans casse). */
    public static final class ModelesEnMemoire implements ModelesDeDocument {
        private record Ligne(ModeleDuCatalogue modele, java.time.Instant creeLe) {
            ModeleDeDocument pourLImpression() {
                return new ModeleDeDocument(modele.id(), modele.cheminDuGabarit() == null ? "" : modele.cheminDuGabarit(),
                        modele.format() == null ? "PDF" : modele.format(), modele.type() == null ? "CUSTOM" : modele.type());
            }
        }

        private final List<Ligne> lignes = new ArrayList<>();
        public int recherches;

        public ModeleDeDocument ajouter(UUID centre, ModeleDeDocument modele, boolean actif, java.time.Instant creeLe) {
            lignes.add(new Ligne(new ModeleDuCatalogue(modele.id(), centre, "C-" + modele.id(), "Modele", modele.type(), modele.cheminDuGabarit(),
                    modele.formatDImpression(), null, actif), creeLe));
            return modele;
        }

        /** Un modèle actif créé maintenant (le type est celui du modèle). */
        public ModeleDeDocument actif(UUID centre, String type, ModeleDeDocument modele) {
            return ajouter(centre, modele, true, java.time.Instant.now());
        }

        public List<ModeleDuCatalogue> tous() {
            return lignes.stream().map(Ligne::modele).toList();
        }

        @Override
        public Optional<ModeleDeDocument> actifDuCentre(UUID centre, String typeNormalise) {
            recherches++;
            return lignes.stream().filter(l -> l.modele().centre().equals(centre) && l.modele().actif()
                            && l.pourLImpression().type().equalsIgnoreCase(typeNormalise))
                    .max(java.util.Comparator.comparing(Ligne::creeLe)).map(Ligne::pourLImpression);
        }

        @Override
        public Optional<ModeleDeDocument> duCentre(UUID modele, UUID centre) {
            recherches++;
            return lignes.stream().filter(l -> l.modele().id().equals(modele) && l.modele().centre().equals(centre)).map(Ligne::pourLImpression)
                    .findFirst();
        }

        @Override
        public Optional<String> typeDuModele(UUID modele) {
            return lignes.stream().filter(l -> l.modele().id().equals(modele)).map(l -> l.modele().type()).filter(t -> t != null).findFirst();
        }

        private List<ModeleDuCatalogue> filtrer(UUID centre, String type) {
            return lignes.stream().map(Ligne::modele).filter(m -> m.centre().equals(centre) && (type == null || type.equals(m.type())))
                    .sorted(java.util.Comparator.comparing(ModeleDuCatalogue::libelle)).toList();
        }

        @Override
        public List<ModeleDuCatalogue> lister(UUID centre, String type) {
            return filtrer(centre, type);
        }

        @Override
        public List<ModeleDuCatalogue> rechercher(RechercheDeModeles recherche) {
            List<ModeleDuCatalogue> tous = filtrer(recherche.centre(), recherche.type());
            long debut = recherche.decalage();
            if (debut >= tous.size()) return List.of();
            return tous.subList((int) debut, (int) Math.min(debut + recherche.taille(), tous.size()));
        }

        private ModeleDuCatalogue catalogue(UUID id, DefinitionDeModele d) {
            return new ModeleDuCatalogue(id, d.centre(), d.code(), d.libelle(), d.type(), d.gabarit().valeur(), d.format(), d.description(), d.actif());
        }

        @Override
        public void creer(UUID id, DefinitionDeModele modele) {
            if (lignes.stream().anyMatch(l -> l.modele().id().equals(id)
                    || (l.modele().centre().equals(modele.centre()) && l.modele().code().equals(modele.code())))) {
                throw new ModeleEnDoublon("doublon", null);
            }
            lignes.add(new Ligne(catalogue(id, modele), java.time.Instant.now()));
        }

        @Override
        public void modifier(UUID id, DefinitionDeModele modele) {
            for (int i = 0; i < lignes.size(); i++) {
                Ligne l = lignes.get(i);
                if (l.modele().id().equals(id) && l.modele().centre().equals(modele.centre())) {
                    boolean pris = lignes.stream().anyMatch(a -> !a.modele().id().equals(id) && a.modele().centre().equals(modele.centre())
                            && a.modele().code().equals(modele.code()));
                    if (pris) throw new ModeleEnDoublon("doublon", null);
                    lignes.set(i, new Ligne(catalogue(id, modele), l.creeLe()));
                    return;
                }
            }
        }

        @Override
        public void supprimer(UUID centre, UUID id) {
            lignes.removeIf(l -> l.modele().id().equals(id) && l.modele().centre().equals(centre));
        }
    }

    public static final class IdentitesEnMemoire implements IdentitesImprimables {
        private final Map<UUID, IdentiteImprimable> centres = new HashMap<>();
        private final Map<UUID, IdentiteImprimable.Societe> societes = new HashMap<>();

        public IdentitesEnMemoire centre(UUID id, IdentiteImprimable identite) {
            centres.put(id, identite);
            return this;
        }

        public IdentitesEnMemoire societe(UUID id, IdentiteImprimable.Societe societe) {
            societes.put(id, societe);
            return this;
        }

        @Override
        public Optional<IdentiteImprimable> duCentre(UUID centre) {
            return Optional.ofNullable(centre == null ? null : centres.get(centre));
        }

        @Override
        public Optional<IdentiteImprimable.Societe> deLaSociete(UUID societe) {
            return Optional.ofNullable(societe == null ? null : societes.get(societe));
        }
    }

    /** Moteur espion : mémorise les appels, rend des octets fixes ou échoue. */
    public static final class MoteurEspion implements MoteurDeRendu {
        public UUID modele;
        public UUID centre;
        public String chemin;
        public Map<String, Object> parametres;
        public String format;
        public byte[] octets = {1, 2, 3};
        public Throwable echec;
        public String typeDemande;
        public String formatDemande;
        public Map<String, Object> donneesDemandees;
        public Rendu rendu = new Rendu(new byte[]{9}, "PDF");

        @Override
        public byte[] rendreParModele(UUID modele, UUID centre, String cheminDuGabarit, Map<String, Object> parametres, String format) throws Exception {
            this.modele = modele;
            this.centre = centre;
            this.chemin = cheminDuGabarit;
            this.parametres = parametres;
            this.format = format;
            if (echec instanceof Exception e) throw e;
            if (echec instanceof Error e) throw e;
            return octets;
        }

        @Override
        public Rendu rendreParType(UUID centre, String type, Map<String, Object> donnees, String formatDemande) {
            this.centre = centre;
            this.typeDemande = type;
            this.donneesDemandees = donnees;
            this.formatDemande = formatDemande;
            return rendu;
        }
    }

    /** Règles espion : ne refuse rien sauf demande contraire, rend des données serveur fixes. */
    public static final class ReglesEspion implements ReglesDImpressionDesTypesSensibles {
        public final List<String> appels = new ArrayList<>();
        public RuntimeException refus;
        public Map<String, Object> donnees = Map.of();

        @Override
        public void garderParType(String type, UUID centreDemande) {
            appels.add("type:" + type);
            if (refus != null) throw refus;
        }

        @Override
        public void garderParModele(String typeDuModele, UUID centreDemande) {
            appels.add("modele:" + typeDuModele);
            if (refus != null) throw refus;
        }

        @Override
        public Map<String, Object> donneesDuServeur(String type, UUID centre, Map<String, String> parametres) {
            appels.add("donnees:" + type);
            return donnees;
        }
    }
}
