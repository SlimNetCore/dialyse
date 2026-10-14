package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesComptages;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesCouts;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Doubles in-memory des lectures du pilotage ({@link LectureDesComptages}, {@link LectureDesCouts}) : tout est calculé depuis le
 * parc et les interventions en mémoire, comme l'adapter JPA le calcule depuis la base (même contrat : {@code PilotageContrat}).
 * Les plans, que le double ne tient pas, sont posés par le test.
 */
public class PilotageEnMemoire implements LectureDesComptages, LectureDesCouts, GenerateursDuCentre {

    private static final Instant DEPUIS_TOUJOURS = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant SANS_LIMITE = Instant.parse("9999-01-01T00:00:00Z");

    private final ParcEnMemoire parc;
    private final InterventionsEnMemoire interventions;
    public long plansActifs;
    public long plansEnRetard;

    public PilotageEnMemoire(ParcEnMemoire parc, InterventionsEnMemoire interventions) {
        this.parc = parc;
        this.interventions = interventions;
    }

    private List<Intervention> duCentre(CentreId centre) {
        return interventions.duCentre(centre, null, 0, 100_000).items();
    }

    // ------------------------------------------------------------------ comptages

    @Override
    public long equipements(CentreId centre) {
        return parc.parcDuCentre(centre).size();
    }

    @Override
    public long equipementsAuStatut(CentreId centre, StatutEquipement statut) {
        return parc.parcDuCentre(centre).stream().filter(e -> e.statut() == statut).count();
    }

    @Override
    public long interventions(CentreId centre) {
        return duCentre(centre).size();
    }

    @Override
    public long interventionsAuStatut(CentreId centre, StatutIntervention statut) {
        return duCentre(centre).stream().filter(i -> i.statut() == statut).count();
    }

    @Override
    public long interventionsDeLEquipement(EquipementId equipement) {
        return interventions.deLEquipement(equipement, 0, 100_000).total();
    }

    @Override
    public Map<UUID, Long> generateursInstallesAvant(Collection<UUID> centres, Instant avant) {
        Map<UUID, Long> parCentre = new HashMap<>();
        for (UUID centre : centres) {
            long n = parc.parcDuCentre(new CentreId(centre)).stream()
                    .filter(e -> e.type().name().equals("GENERATEUR_DIALYSE"))
                    .filter(e -> e.statut() != StatutEquipement.REFORME && e.statut() != StatutEquipement.DESACTIF)
                    .filter(e -> e.dateInstallation() != null && e.dateInstallation().isBefore(avant)).count();
            if (n > 0) parCentre.put(centre, n);
        }
        return parCentre;
    }

    @Override
    public List<Generateur> duCentre(UUID centreId) {
        return parc.parcDuCentre(new CentreId(centreId)).stream()
                .filter(e -> e.type().name().equals("GENERATEUR_DIALYSE"))
                .sorted(java.util.Comparator.comparing(EquipementVue::code))
                .map(e -> new Generateur(e.id(), e.code(), e.salleId(),
                        e.statut().name(), centreId))
                .toList();
    }

    @Override
    public long plansActifs(CentreId centre) {
        return plansActifs;
    }

    @Override
    public long plansEnRetard(CentreId centre, Instant maintenant) {
        return plansEnRetard;
    }

    @Override
    public long interventionsARelancer(CentreId centre, Instant maintenant) {
        return duCentre(centre).stream().filter(i -> i.enRetard(maintenant) || i.echeanceDepassee(maintenant)).count();
    }

    // ------------------------------------------------------------------ coûts

    private static Predicate<Intervention> demarreeSur(Instant du, Instant au) {
        return i -> (i.statut() == StatutIntervention.EN_COURS || i.statut() == StatutIntervention.TERMINEE)
                && i.debut() != null && !i.debut().isBefore(du) && i.debut().isBefore(au);
    }

    private static BigDecimal somme(List<Intervention> liste) {
        return liste.stream().flatMap(i -> i.lignes().stream()).map(LigneDeCout::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal deLEquipement(EquipementId equipement, Instant du, Instant au) {
        return somme(interventions.deLEquipement(equipement, 0, 100_000).items().stream().filter(demarreeSur(du, au)).toList());
    }

    @Override
    public BigDecimal cumuleDeLEquipement(EquipementId equipement) {
        return deLEquipement(equipement, DEPUIS_TOUJOURS, SANS_LIMITE);
    }

    @Override
    public BigDecimal duCentre(CentreId centre, Instant du, Instant au) {
        return somme(duCentre(centre).stream().filter(demarreeSur(du, au)).toList());
    }

    @Override
    public Map<EquipementId, BigDecimal> parEquipement(CentreId centre, Instant du, Instant au) {
        Map<EquipementId, BigDecimal> sommes = new HashMap<>();
        duCentre(centre).stream().filter(demarreeSur(du, au)).filter(i -> !i.lignes().isEmpty())
                .forEach(i -> sommes.merge(i.equipementId(), somme(List.of(i)), BigDecimal::add));
        return sommes;
    }

    @Override
    public Map<EquipementId, BigDecimal> cumuleParEquipement(CentreId centre) {
        return parEquipement(centre, DEPUIS_TOUJOURS, SANS_LIMITE);
    }
}
