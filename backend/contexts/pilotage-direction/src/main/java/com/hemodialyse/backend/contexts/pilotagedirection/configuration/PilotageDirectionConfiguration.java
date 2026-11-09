package com.hemodialyse.backend.contexts.pilotagedirection.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.TraitementDeLAnemiePublie;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConstituerLeRapportDeSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaRepartition;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaVueDEnsemble;
import com.hemodialyse.backend.contexts.pilotagedirection.application.EffectifsDesPatients;
import com.hemodialyse.backend.contexts.pilotagedirection.application.FacturationDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HorlogeDuPilotage;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SeancesRealiseesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SocietesSuivies;
import com.hemodialyse.backend.contexts.pilotagedirection.application.TraitementsDeLAnemieDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.dossiermedical.AnemieParLeDossierMedical;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.facturation.FacturationParLaFacturation;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.seance.SeancesRealiseesParLaSeance;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.PatientsDesCentresJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.SocietesEtCentresJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.RapportDeSynthesePublie;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.TableauDeBordPublie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/**
 * Composition du lot A (PD1 + PD4, ADR 0102) : le tableau de bord et le rapport de synthèse de la direction, publiés. Mêmes tables
 * ({@code societes}, {@code centers}, {@code patients}) et mêmes URL (servies par la plateforme) que le legacy ; aucun flag, aucune
 * façade. L'horloge est le fuseau de la JVM (KB-DIR-03, statu quo). Les indicateurs sont FOURNIS par la plateforme
 * ({@link IndicateursDeLaDirectionFournis}).
 */
@Configuration(proxyBeanMethods = false)
public class PilotageDirectionConfiguration {

    @Bean
    HorlogeDuPilotage horlogeDuPilotage() {
        // BackendApplication fixe le fuseau par défaut de la JVM à UTC : Clock.systemUTC() est donc identique au LocalDate.now() du legacy
        // (KB-DIR-03, statu quo), et satisfait la règle « aucune horloge ni fuseau du système » de FrontieresDesContextesTest.
        return new HorlogeDuPilotage(Clock.systemUTC());
    }

    @Bean
    SocietesSuivies societesSuivies(JdbcTemplate jdbc) {
        return new SocietesEtCentresJdbc(jdbc);
    }

    @Bean
    EffectifsDesPatients effectifsDesPatients(JdbcTemplate jdbc) {
        return new PatientsDesCentresJdbc(jdbc);
    }

    @Bean
    SeancesRealiseesDesCentres seancesRealiseesDesCentres(SeancesRealiseesDesCentresPubliees seance) {
        return new SeancesRealiseesParLaSeance(seance);
    }

    @Bean
    FacturationDesCentres facturationDesCentres(SyntheseFinanciereDesCentres facturation) {
        return new FacturationParLaFacturation(facturation);
    }

    @Bean
    TraitementsDeLAnemieDesCentres traitementsDeLAnemieDesCentres(TraitementDeLAnemiePublie dossierMedical) {
        return new AnemieParLeDossierMedical(dossierMedical);
    }

    @Bean
    TableauDeBordDeLaDirection tableauDeBordDeLaDirection(SocietesSuivies societes, EffectifsDesPatients effectifs,
                                                          SeancesRealiseesDesCentres seances, FacturationDesCentres facturation,
                                                          TraitementsDeLAnemieDesCentres anemie, HorlogeDuPilotage horloge) {
        return new TableauDeBordPublie(new ConsulterLaSociete(societes),
                new ConsulterLaVueDEnsemble(societes, effectifs, seances, facturation, horloge),
                new ConsulterLaRepartition(societes, effectifs, seances, facturation, anemie, horloge));
    }

    @Bean
    RapportDeSyntheseDeLaDirection rapportDeSyntheseDeLaDirection(TableauDeBordDeLaDirection tableau,
                                                                  IndicateursDeLaDirectionFournis indicateurs,
                                                                  HorlogeDuPilotage horloge) {
        return new RapportDeSynthesePublie(new ConstituerLeRapportDeSynthese(tableau, indicateurs, horloge));
    }
}
