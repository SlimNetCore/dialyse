package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.application.supervision.AnalyseRequete;
import com.hemodialyse.backend.application.supervision.Conseil;
import com.hemodialyse.backend.application.supervision.RequeteAnalysee;
import com.hemodialyse.backend.application.supervision.SanteBase;
import com.hemodialyse.backend.application.supervision.StatutStatistiques;

import java.time.Instant;
import java.util.List;

/**
 * Réponses de l'écran « Performance de la base » du propriétaire.
 */
public final class SupervisionBaseResponses {

    private SupervisionBaseResponses() {
    }

    public record Statut(boolean disponible, String raison, Instant reinitialiseLe, double tempsTotalMs) {
        public static Statut from(StatutStatistiques s) {
            return new Statut(s.disponible(), s.raison(), s.reinitialiseLe(), s.tempsTotalMs());
        }
    }

    public record Requete(String id, String requete, long appels, double tempsTotalMs, double tempsMoyenMs,
                          double tempsMaxMs, long lignes, double partTempsTotalPct, String niveau,
                          List<ConseilDto> conseils) {
        public static Requete from(RequeteAnalysee a) {
            var s = a.statistique();
            return new Requete(s.id(), s.requete(), s.appels(), s.tempsTotalMs(), s.tempsMoyenMs(), s.tempsMaxMs(),
                    s.lignes(), a.partTempsTotalPct(), a.niveau().name(),
                    a.conseils().stream().map(ConseilDto::from).toList());
        }
    }

    public record ConseilDto(String code, String niveau, String valeur) {
        static ConseilDto from(Conseil c) {
            return new ConseilDto(c.code(), c.niveau().name(), c.valeur());
        }
    }

    public record Analyse(boolean disponible, String raison, String requete, String planTexte, double coutTotal,
                          int indexUtilises, List<Balayage> balayagesComplets) {
        public static Analyse from(AnalyseRequete a) {
            return new Analyse(a.disponible(), a.raison(), a.requete(), a.planTexte(), a.coutTotal(), a.indexUtilises(),
                    a.balayagesComplets().stream().map(Balayage::from).toList());
        }
    }

    public record Balayage(String table, String filtre, List<String> colonnes, long lignesTable,
                           List<String> indexExistants, String verdict, String indexSuggere) {
        static Balayage from(AnalyseRequete.BalayageComplet b) {
            return new Balayage(b.table(), b.filtre(), b.colonnes(), b.lignesTable(), b.indexExistants(),
                    b.verdict().name(), b.indexSuggere());
        }
    }

    public record Sante(boolean disponible, String raison, long tailleOctets, double cachePct, int connexions,
                        int connexionsMax, Instant statsReset, List<TableSante> tables,
                        List<IndexInutiliseDto> indexInutilises, List<AlerteSante> alertes) {
        public static Sante from(SanteBase s) {
            return new Sante(s.disponible(), s.raison(), s.tailleOctets(), s.cachePct(), s.connexions(),
                    s.connexionsMax(), s.statsReset(),
                    s.tables().stream().map(TableSante::from).toList(),
                    s.indexInutilises().stream()
                            .map(i -> new IndexInutiliseDto(i.table(), i.index(), i.tailleOctets())).toList(),
                    s.alertes().stream()
                            .map(a -> new AlerteSante(a.code(), a.niveau(), a.cible(), a.valeur())).toList());
        }
    }

    public record TableSante(String nom, long tailleOctets, long lignes, double mortesPct, long scansComplets,
                             long scansIndex, Instant dernierVacuum, String partitionnement) {
        static TableSante from(SanteBase.TableDiagnostiquee d) {
            var t = d.table();
            return new TableSante(t.nom(), t.tailleOctets(), t.lignes(), d.mortesPct(), t.scansComplets(),
                    t.scansIndex(), t.dernierAutovacuum(), d.partitionnement().name());
        }
    }

    public record IndexInutiliseDto(String table, String index, long tailleOctets) {
    }

    public record AlerteSante(String code, String niveau, String cible, String valeur) {
    }
}
