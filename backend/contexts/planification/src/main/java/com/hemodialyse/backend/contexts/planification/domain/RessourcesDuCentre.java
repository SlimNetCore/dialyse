package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Instantané des ressources d'un centre, lu par le cas d'usage (Organisation, GMAO, calendrier) et passé en entrée à
 * {@link PlacesDuCentre} : salles (dont la marque d'isolement), créneaux, générateurs <b>en service</b> de dialyse rattachés à une
 * salle (RG-PLN-011), jours d'ouverture (aucun renseigné = tous) et fermetures datées des 90 prochains jours (RG-PLN-013).
 */
public record RessourcesDuCentre(List<Salle> salles, List<Creneau> creneaux, List<Generateur> generateurs,
                                 Set<JourDeLaSemaine> joursOuverts, List<Fermeture> fermetures) {

    public record Salle(SalleId id, String nom, boolean isolement) {
    }

    /** @param ordre rang d'affichage : du plus tôt au plus tard dans la journée */
    public record Creneau(CreneauId id, String libelle, int ordre) {
    }

    public record Generateur(GenerateurId id, String code, SalleId salle) {
    }

    public record Fermeture(LocalDate date, String motif) {
    }

    public RessourcesDuCentre {
        salles = salles == null ? List.of() : List.copyOf(salles);
        creneaux = creneaux == null ? List.of() : List.copyOf(creneaux);
        generateurs = generateurs == null ? List.of() : List.copyOf(generateurs);
        fermetures = fermetures == null ? List.of() : List.copyOf(fermetures);
        joursOuverts = joursOuverts == null || joursOuverts.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.allOf(JourDeLaSemaine.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(joursOuverts));
    }

    public boolean estIsolement(SalleId salle) {
        return salles.stream().anyMatch(s -> s.id().equals(salle) && s.isolement());
    }

    public boolean contientSalle(SalleId salle) {
        return salles.stream().anyMatch(s -> s.id().equals(salle));
    }

    public boolean contientCreneau(CreneauId creneau) {
        return creneaux.stream().anyMatch(c -> c.id().equals(creneau));
    }

    public boolean aUneSalleDIsolement() {
        return salles.stream().anyMatch(Salle::isolement);
    }
}
