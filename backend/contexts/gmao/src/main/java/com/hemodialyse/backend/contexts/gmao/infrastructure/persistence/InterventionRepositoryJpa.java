package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Tache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter JPA du repository des interventions. L'intervention, ses lignes de coût et ses rectifications s'écrivent dans la
 * transaction du cas d'usage ; les écritures sont vidées tout de suite pour que la base refuse (valeur trop longue,
 * précision dépassée, KB-GMAO-45) pendant le cas d'usage, avant la publication des événements. Les lignes sont immuables : on
 * insère les nouvelles et on supprime celles qui ont disparu ; les rectifications ne font que s'ajouter ; les tâches ne se retirent pas, leur avancement
 * se met à jour.
 */
public class InterventionRepositoryJpa implements InterventionRepository {

    private final InterventionSpringData interventions;
    private final LigneDeCoutSpringData lignes;
    private final RectificationSpringData rectifications;
    private final TacheSpringData taches;

    public InterventionRepositoryJpa(InterventionSpringData interventions, LigneDeCoutSpringData lignes,
                                     RectificationSpringData rectifications, TacheSpringData taches) {
        this.interventions = interventions;
        this.lignes = lignes;
        this.rectifications = rectifications;
        this.taches = taches;
    }

    @Override
    public void enregistrer(Intervention intervention) {
        UUID id = intervention.id().valeur();
        InterventionJpa entite = interventions.findById(id).orElseGet(() -> new InterventionJpa(id));
        InterventionMapper.recopier(intervention, entite);
        interventions.saveAndFlush(entite);

        List<LigneDeCoutJpa> presentes = lignes.findByInterventionId(id);
        Set<UUID> voulues = intervention.lignes().stream().map(l -> l.id().valeur()).collect(Collectors.toSet());
        Set<UUID> connues = presentes.stream().map(l -> l.id).collect(Collectors.toSet());
        lignes.deleteAll(presentes.stream().filter(l -> !voulues.contains(l.id)).toList());
        intervention.lignes().stream().filter(l -> !connues.contains(l.id().valeur())).forEach(l -> {
            LigneDeCoutJpa nouvelle = new LigneDeCoutJpa(l.id().valeur());
            InterventionMapper.recopier(l, id, nouvelle);
            lignes.save(nouvelle);
        });
        lignes.flush();

        Set<UUID> rectificationsConnues = rectifications.findByInterventionIdOrderByLeAsc(id).stream()
                .map(r -> r.id).collect(Collectors.toSet());
        intervention.rectifications().stream().filter(r -> !rectificationsConnues.contains(r.id())).forEach(r -> {
            RectificationJpa nouvelle = new RectificationJpa(r.id());
            InterventionMapper.recopier(r, id, nouvelle);
            rectifications.save(nouvelle);
        });
        rectifications.flush();

        Map<UUID, TacheJpa> tachesConnues = new HashMap<>();
        taches.findByInterventionIdOrderByRangAsc(id).forEach(t -> tachesConnues.put(t.id, t));
        int rang = 0;
        for (Tache t : intervention.taches()) {
            TacheJpa tache = tachesConnues.getOrDefault(t.id().valeur(), new TacheJpa(t.id().valeur()));
            InterventionMapper.recopier(t, rang++, id, intervention.centreId().valeur(), tache);
            taches.save(tache);
        }
        taches.flush();
    }

    @Override
    public Optional<Intervention> trouverParId(InterventionId id) {
        return interventions.findById(id.valeur()).map(this::charger);
    }

    @Override
    public Optional<Intervention> chargerPourModification(InterventionId id) {
        return interventions.verrouiller(id.valeur()).map(this::charger);
    }

    Intervention charger(InterventionJpa entite) {
        return InterventionMapper.versDomaine(entite, lignes.findByInterventionId(entite.id),
                rectifications.findByInterventionIdOrderByLeAsc(entite.id),
                taches.findByInterventionIdOrderByRangAsc(entite.id));
    }
}
