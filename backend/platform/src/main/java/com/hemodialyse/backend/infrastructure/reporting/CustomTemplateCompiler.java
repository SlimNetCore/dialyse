package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Compile les modèles JRXML personnalisés <b>déjà validés</b> par {@code JrxmlSecurityValidator}.
 * La compilation est coûteuse : le résultat est mis en cache par (centre, modèle, version). Les versions
 * étant immuables, aucune éviction n'est nécessaire lors d'une bascule de version active.
 */
@Component
public class CustomTemplateCompiler {

    public static final String CACHE_NAME = "reporting.customTemplates";

    @Cacheable(cacheNames = CACHE_NAME, key = "#centerId.toString() + ':' + #modeleId.toString() + ':' + #version")
    public JasperReport compile(UUID centerId, UUID modeleId, int version, String xml) throws JRException {
        return compileNoCache(xml);
    }

    /**
     * Compilation sans cache : sert à refuser dès le téléversement un modèle que Jasper ne sait pas compiler.
     */
    public JasperReport compileNoCache(String xml) throws JRException {
        return JasperCompileManager.compileReport(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
