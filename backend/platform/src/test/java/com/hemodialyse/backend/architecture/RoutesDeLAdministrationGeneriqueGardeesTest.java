package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF5 (revue complémentaire RF1-RF3d, constat 5) : le 403 prioritaire de {@code DatesIllisiblesDeLaDirectionAdvice} sur
 * {@code /api/v1/admin/referentials/**} repose sur la présence de {@code @PreAuthorize} (méthode ou classe). Cette garde échoue si UNE route
 * de cette administration (ou un motif plus large qui la recouvre, comme {@code /api/v1/admin/**}) n'en porte pas : ajouter une route sans
 * droit ne passe plus inaperçu. Complément : l'advice refuse par défaut (403) une route de cette administration sans garde.
 */
class RoutesDeLAdministrationGeneriqueGardeesTest {

    private static final String PREFIXE = "/api/v1/admin/referentials";
    private static final PathPatternParser ANALYSEUR = new PathPatternParser();

    /** Routes (classe#méthode) recouvrant le préfixe sans {@code @PreAuthorize}, parmi les {@code controleurs} donnés. */
    static List<String> routesSansGarde(List<Class<?>> controleurs) {
        List<String> sansGarde = new ArrayList<>();
        for (Class<?> controleur : controleurs) {
            List<String> basesDeclarees = chemins(AnnotatedElementUtils.findMergedAnnotation(controleur, RequestMapping.class));
            final List<String> base = basesDeclarees.isEmpty() ? List.of("") : basesDeclarees;
            for (Method methode : controleur.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(methode, RequestMapping.class);
                if (mapping == null) continue;
                List<String> proprement = chemins(mapping);
                final List<String> propres = proprement.isEmpty() ? List.of("") : proprement;
                boolean recouvre = base.stream().anyMatch(b -> propres.stream().anyMatch(p -> recouvre(b + p)));
                boolean garde = AnnotatedElementUtils.findMergedAnnotation(methode, PreAuthorize.class) != null
                        || AnnotatedElementUtils.findMergedAnnotation(controleur, PreAuthorize.class) != null;
                if (recouvre && !garde) sansGarde.add(controleur.getName() + "#" + methode.getName());
            }
        }
        return new ArrayList<>(new TreeSet<>(sansGarde));
    }

    private static List<String> chemins(RequestMapping mapping) {
        if (mapping == null) return List.of();
        return java.util.Arrays.asList(mapping.path().length > 0 ? mapping.path() : mapping.value());
    }

    private static boolean recouvre(String motif) {
        if (motif.isEmpty()) return false;
        PathPattern pattern = ANALYSEUR.parse(motif);
        return motif.startsWith(PREFIXE) || pattern.matches(org.springframework.http.server.PathContainer.parsePath(PREFIXE))
                || pattern.matches(org.springframework.http.server.PathContainer.parsePath(PREFIXE + "/exemple"))
                || pattern.matches(org.springframework.http.server.PathContainer.parsePath(PREFIXE + "/exemple/1"));
    }

    private static List<Class<?>> controleursDeProduction() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.stereotype.Controller.class));
        List<Class<?>> classes = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents("com.hemodialyse.backend")) {
            classes.add(Class.forName(((AnnotatedBeanDefinition) definition).getMetadata().getClassName()));
        }
        return classes;
    }

    @Test
    void toute_route_de_l_administration_generique_porte_une_garde_preauthorize() throws ClassNotFoundException {
        List<Class<?>> controleurs = controleursDeProduction();

        assertThat(controleurs).as("la garde scanne bien les contrôleurs (non vide)").hasSizeGreaterThan(10);
        assertThat(routesSansGarde(controleurs))
                .as("route de /api/v1/admin/referentials/** sans @PreAuthorize : le 403 prioritaire de l'advice en dépend (RF5)").isEmpty();
        assertThat(controleurs.stream().map(Class::getSimpleName)).as("l'administration générique est bien scannée")
                .contains("ReferentialAdminRestController");
    }

    // volontairement SANS @RestController : un contrôleur d'essai ne doit jamais être enregistré par le balayage de composants des tests Spring
    @RequestMapping(PREFIXE)
    static class SansGardeDeClasse {
        @org.springframework.web.bind.annotation.GetMapping("/{kind}")
        public void lire() {
        }
    }

    // volontairement SANS @RestController : un contrôleur d'essai ne doit jamais être enregistré par le balayage de composants des tests Spring
    @RequestMapping("/api/v1/admin")
    static class MotifLargeSansGarde {
        @org.springframework.web.bind.annotation.GetMapping("/**")
        public void tout() {
        }
    }

    // volontairement SANS @RestController : un contrôleur d'essai ne doit jamais être enregistré par le balayage de composants des tests Spring
    @RequestMapping(PREFIXE)
    @PreAuthorize("hasRole('ADMIN')")
    static class GardeeParLaClasse {
        @org.springframework.web.bind.annotation.GetMapping("/{kind}")
        public void lire() {
        }
    }

    // volontairement SANS @RestController : un contrôleur d'essai ne doit jamais être enregistré par le balayage de composants des tests Spring
    @RequestMapping("/api/v1/direction")
    static class AutreRouteSansGarde {
        @org.springframework.web.bind.annotation.GetMapping("/overview")
        public void lire() {
        }
    }

    // volontairement SANS @RestController : un contrôleur d'essai ne doit jamais être enregistré par le balayage de composants des tests Spring
    @RequestMapping(PREFIXE)
    static class GardeeParLaMethodeSeulement {
        @PreAuthorize("hasRole('ADMIN')")
        @org.springframework.web.bind.annotation.GetMapping("/{kind}")
        public void lire() {
        }

        @org.springframework.web.bind.annotation.DeleteMapping("/{kind}/{id}")
        public void supprimer() {
        }
    }

    @Test
    void la_garde_detecte_une_route_sans_preauthorize_et_ignore_les_routes_gardees_ou_hors_perimetre() {
        assertThat(routesSansGarde(List.of(SansGardeDeClasse.class))).hasSize(1);
        assertThat(routesSansGarde(List.of(MotifLargeSansGarde.class))).as("un motif large recouvre l'administration").hasSize(1);
        assertThat(routesSansGarde(List.of(GardeeParLaClasse.class))).isEmpty();
        assertThat(routesSansGarde(List.of(AutreRouteSansGarde.class))).isEmpty();
        assertThat(routesSansGarde(List.of(GardeeParLaMethodeSeulement.class))).containsExactly(
                GardeeParLaMethodeSeulement.class.getName() + "#supprimer");
    }
}
