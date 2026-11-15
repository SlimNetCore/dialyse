package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import com.hemodialyse.backend.contexts.documents.application.ConnexionDeRendu;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le port transitoire de connexion et la transition des gabarits vers un jeu de données (DOC5a, ADR 0193) : la connexion est de lecture,
 * bornée au travail et refermée ; un gabarit qui reçoit ses lignes n'ouvre aucune connexion, y compris un modèle PERSONNALISÉ déjà stocké
 * qui porte encore l'ancienne requête (la source de données prime : son rendu ne change pas).
 */
class ConnexionDeRenduEtGabaritsSansRequeteTest {

    private static final String GABARIT_AVEC_L_ANCIENNE_REQUETE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" name="stocke" pageWidth="300" pageHeight="100"
                          columnWidth="280" leftMargin="10" rightMargin="10" topMargin="10" bottomMargin="10">
                <queryString><![CDATA[SELECT x FROM table_qui_n_existe_pas]]></queryString>
                <field name="X" class="java.lang.String"/>
                <detail><band height="20">
                    <textField><reportElement x="0" y="0" width="200" height="20"/><textFieldExpression><![CDATA[$F{X}]]></textFieldExpression></textField>
                </band></detail>
            </jasperReport>
            """;

    private static final ConnexionDeRendu AUCUNE_CONNEXION = new ConnexionDeRendu() {
        @Override
        public <T> T avecLaConnexion(Travail<T> travail) {
            throw new AssertionError("un gabarit qui reçoit ses lignes n'ouvre aucune connexion");
        }
    };

    /** Une connexion qui note ses appels ({@code setReadOnly}, {@code close}) ; le pilote H2 ignore {@code setReadOnly}. */
    private static DataSource sourceQuiNote(List<String> appels) {
        Connection connexion = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, methode, args) -> {
                    switch (methode.getName()) {
                        case "setReadOnly" -> appels.add("setReadOnly " + args[0]);
                        case "close" -> appels.add("close");
                        case "isReadOnly" -> {
                            return appels.stream().filter(a -> a.startsWith("setReadOnly")).reduce((a, b) -> b).orElse("setReadOnly false")
                                    .endsWith("true");
                        }
                        default -> {
                        }
                    }
                    return methode.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, methode, args) -> "getConnection".equals(methode.getName()) ? connexion : null);
    }

    @Test
    void la_connexion_est_en_lecture_seule_pendant_le_travail_puis_rendue_dans_son_etat_et_refermee() throws Exception {
        List<String> appels = new ArrayList<>();

        boolean lectureSeule = new ConnexionDeRenduDeLaBase(sourceQuiNote(appels)).avecLaConnexion(Connection::isReadOnly);

        assertThat(lectureSeule).isTrue();
        assertThat(appels).containsExactly("setReadOnly true", "setReadOnly false", "close");
    }

    @Test
    void la_connexion_est_refermee_meme_si_le_travail_echoue() {
        List<String> appels = new ArrayList<>();

        assertThatThrownBy(() -> new ConnexionDeRenduDeLaBase(sourceQuiNote(appels)).avecLaConnexion(connexion -> {
            throw new IllegalStateException("échec du travail");
        })).hasMessage("échec du travail");

        assertThat(appels).endsWith("close");
    }

    @Test
    void un_modele_stocke_qui_porte_encore_l_ancienne_requete_est_rendu_par_les_lignes_fournies_sans_ouvrir_de_connexion() throws Exception {
        var jasper = new JasperReportService(AUCUNE_CONNEXION);
        JasperReport stocke = new CustomTemplateCompiler().compileNoCache(GABARIT_AVEC_L_ANCIENNE_REQUETE);
        Map<String, Object> parametres = new HashMap<>();
        parametres.put(JRParameter.REPORT_DATA_SOURCE, new JRMapCollectionDataSource(List.of(Map.of("X", "ligne un"), Map.of("X", "ligne deux"))));

        JasperPrint print = jasper.fillReport(stocke, parametres);

        assertThat(print.getPages().stream().map(JRPrintPage::getElements).flatMap(List::stream)
                .filter((JRPrintElement e) -> e instanceof JRPrintText).map(e -> ((JRPrintText) e).getFullText()))
                .containsExactly("ligne un", "ligne deux");
    }
}
