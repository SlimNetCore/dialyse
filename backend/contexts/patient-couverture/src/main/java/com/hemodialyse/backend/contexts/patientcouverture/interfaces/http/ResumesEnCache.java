package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLEffectif;
import com.hemodialyse.backend.contexts.patientcouverture.application.ResumeDesPatients;
import org.springframework.cache.annotation.Cacheable;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Cache des synthèses mensuelles, par centre et par mois (caches {@code patient.list.summary} et
 * {@code patient.list.summary.details}, vidés par {@code POST} et {@code PUT} de la fiche). Le mois est résolu AVANT l'appel (le
 * mois courant est celui du jour du centre) : la clé ne dépend plus du mois de la JVM. La garde de centre a déjà été exercée
 * par la résolution du mois ; une valeur en cache n'est servie qu'à un appelant passé par cette garde.
 */
public class ResumesEnCache {

    private final ConsulterLEffectif effectif;

    public ResumesEnCache(ConsulterLEffectif effectif) {
        this.effectif = effectif;
    }

    @Cacheable(cacheNames = "patient.list.summary", key = "#centre.toString() + ':' + #mois.toString()")
    public ResumeDesPatients resume(UUID centre, YearMonth mois) {
        return effectif.resume(centre, mois);
    }

    @Cacheable(cacheNames = "patient.list.summary.details", key = "#centre.toString() + ':' + #mois.toString()")
    public ResumeDesPatients.Detail detail(UUID centre, YearMonth mois) {
        return effectif.detail(centre, mois);
    }
}
