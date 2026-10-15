#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur les ecritures comptables et leur cycle de vie (C3 :
# consultation, validation, export Sage 100, cloture de periode ; legacy bc-comptabilite + platform, refonte sur place),
# testes par les 7 classes de caracterisation HTTP (Spring complet, H2) de
# com.hemodialyse.backend.characterization.comptabilite.ecritures. Lent (contexte Spring dans chaque JVM de mutation) :
# a lancer en arriere-plan, journal puis tail.
# Prerequis : tests compiles (./mvnw -o -q -pl platform -am test-compile) et classpath ecrit par
#   ./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash, depuis n'importe ou) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-c3-ecritures.sh
# Le repertoire courant de PIT est backend/platform (les goldens sont lus en chemin relatif src/test/resources/...).
B=C:/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend
M=C:/Users/TS-CONSULT/.m2/repository
CPFILE=${CPFILE:-/c/Users/TS-CON~1/AppData/Local/Temp/c2-cp.txt}
DEPS=$(cat "$CPFILE" | tr -d '\r\n' | tr '\\' '/')
# les jars des modules du projet (.m2) peuvent etre perimes : on prend leurs target/classes
for MOD in shared-kernel bc-article bc-insurance bc-referential bc-patient bc-assure bc-stock bc-pec bc-seance bc-medical \
           bc-facturation bc-reglement bc-comptabilite bc-migration; do
  DEPS=$(echo "$DEPS" | sed "s|C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/$MOD/0.0.1-SNAPSHOT/$MOD-0.0.1-SNAPSHOT.jar|$B/$MOD/target/classes|")
done
for CTX in gmao comptabilite; do
  DEPS=$(echo "$DEPS" | sed "s|C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/contexts-$CTX/0.0.1-SNAPSHOT/contexts-$CTX-0.0.1-SNAPSHOT.jar|$B/contexts/$CTX/target/classes|")
done
CP="$B/platform/target/test-classes;$B/platform/target/classes;$DEPS;$M/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar"
CP=$(echo "$CP" | tr ';' ',')
PITCP="$M/org/pitest/pitest/1.30.0/pitest-1.30.0.jar;$M/org/pitest/pitest-entry/1.30.0/pitest-entry-1.30.0.jar;$M/org/pitest/pitest-command-line/1.30.0/pitest-command-line-1.30.0.jar;$M/org/pitest/pitest-html-report/1.30.0/pitest-html-report-1.30.0.jar;$M/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar;$M/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.jar;$M/org/apache/commons/commons-text/1.14.0/commons-text-1.14.0.jar;$M/org/apache/commons/commons-lang3/3.16.0/commons-lang3-3.16.0.jar"
cd /c/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend/platform
D=com.hemodialyse.backend.domain.comptabilite
I=com.hemodialyse.backend.infrastructure
java -cp "$PITCP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$B/platform/target/pit-reports-c3" \
  --targetClasses "$D.aggregate.EcritureComptable,$D.entity.LigneEcriture,$D.service.ComptabiliteService,$I.persistence.adapter.EcritureComptableJpaAdapter,$I.persistence.adapter.PeriodeComptableJpaAdapter,$I.export.Sage100ExportAdapter,$I.web.rest.ComptabiliteRestController,com.hemodialyse.backend.application.comptabilite.ComptabiliteApplicationService" \
  --excludedMethods "rebuild,synchroniserStock,lambda\$rebuild*,lambda\$synchroniserStock*,genererEcritureFacturation,genererEcritureReglement,compteClient" \
  --targetTests "com.hemodialyse.backend.characterization.comptabilite.ecritures.*" \
  --sourceDirs "$B/platform/src/main/java,$B/bc-comptabilite/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 120000 --timeoutFactor 4 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
