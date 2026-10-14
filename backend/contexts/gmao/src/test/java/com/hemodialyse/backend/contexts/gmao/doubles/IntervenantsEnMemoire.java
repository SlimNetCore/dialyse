package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.IntervenantsDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.ConditionsDeValorisation;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.LectureIntervenants;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.PageDIntervenants;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.TarifIntervenant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Double in-memory des ports de persistance et de lecture des intervenants. Fidèle au contrat partagé
 * ({@code IntervenantContrat}) : les mêmes tests s'exécutent contre l'adapter JPA. Il reproduit les contraintes de la base
 * observées au legacy (KB-GMAO-26/27) : tarif arrondi à deux décimales (demi supérieur), précision 10, longueurs refusées.
 */
public class IntervenantsEnMemoire implements IntervenantRepository, LectureIntervenants, IntervenantsDuCentre {

    private static final BigDecimal TARIF_MAX = new BigDecimal("99999999.99");

    private final Map<IntervenantId, Intervenant> intervenants = new LinkedHashMap<>();

    @Override
    public void enregistrer(Intervenant intervenant) {
        verifierLesContraintesDeLaBase(intervenant);
        intervenants.put(intervenant.id(), copie(intervenant));
    }

    @Override
    public Optional<Intervenant> trouverParId(IntervenantId id) {
        return Optional.ofNullable(intervenants.get(id)).map(IntervenantsEnMemoire::copie);
    }

    @Override
    public Optional<ConditionsDeValorisation> conditionsDe(IntervenantId id, CentreId centre) {
        return Optional.ofNullable(intervenants.get(id)).filter(i -> i.centreId().equals(centre))
                .map(i -> new ConditionsDeValorisation(i.nature(), i.tarif() == null ? null : i.tarif().valeur()));
    }

    @Override
    public Optional<IntervenantVue> parId(IntervenantId id) {
        return Optional.ofNullable(intervenants.get(id)).map(IntervenantVue::depuis);
    }

    @Override
    public PageDIntervenants duCentre(CentreId centre, int page, int size) {
        List<IntervenantVue> tous = intervenants.values().stream()
                .filter(i -> i.centreId().equals(centre))
                .sorted(Comparator.comparing(Intervenant::nom))
                .map(IntervenantVue::depuis)
                .toList();
        long debut = (long) page * size;
        List<IntervenantVue> items = debut >= tous.size() ? List.of()
                : tous.subList((int) debut, (int) Math.min(tous.size(), debut + size));
        return new PageDIntervenants(items, tous.size(), page, size);
    }

    private static void verifierLesContraintesDeLaBase(Intervenant i) {
        if (i.nom().length() > 255 || (i.telephone() != null && i.telephone().length() > 50)
                || (i.email() != null && i.email().length() > 255)) {
            throw new IllegalStateException("Valeur trop longue pour la colonne");
        }
        if (i.tarif() != null && i.tarif().valeur().setScale(2, RoundingMode.HALF_UP).compareTo(TARIF_MAX) > 0) {
            throw new IllegalStateException("Précision du tarif dépassée");
        }
    }

    /** Ce que la base restitue : tarif ramené à deux décimales (demi supérieur). */
    private static Intervenant copie(Intervenant i) {
        TarifIntervenant tarif = i.tarif() == null ? null : new TarifIntervenant(i.tarif().valeur().setScale(2, RoundingMode.HALF_UP));
        return Intervenant.reconstituer(i.id(), i.centreId(), i.nom(), i.nature(), i.telephone(), i.email(), tarif,
                i.estActif());
    }
}
