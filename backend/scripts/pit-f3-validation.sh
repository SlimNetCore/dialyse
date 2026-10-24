#!/bin/bash
# Mutation PIT 1.30.0 (+ junit5-plugin 1.2.3) en ligne de commande sur la validation et l'emission des factures (F3 :
# POST /api/v1/facturation/validate ; legacy platform, refonte sur place), testees par les 5 classes de
# caracterisation HTTP (Spring complet, H2) de com.hemodialyse.backend.characterization.facturation.validation.
# Lent (contexte Spring dans chaque JVM de mutation) : a lancer en arriere-plan, journal puis tail.
# Prerequis : tests compiles (./mvnw -o -q -pl platform -am test-compile) et classpath ecrit par
#   ./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=$CPFILE
# Usage (Git Bash, depuis n'importe ou) : CPFILE=/chemin/classpath.txt bash backend/scripts/pit-f3-validation.sh
# Le repertoire courant de PIT est backend/platform (les goldens sont lus en chemin relatif src/test/resources/...).
B=C:/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend
M=C:/Users/TS-CONSULT/.m2/repository
CPFILE=${CPFILE:-/c/Users/TS-CON~1/AppData/Local/Temp/f3-cp.txt}
DEPS=$(cat "$CPFILE" | tr -d '\r\n' | tr '\' '/')
# les jars des modules du projet (.m2) peuvent etre perimes : on prend leurs target/classes
for MOD in shared-kernel bc-article bc-insurance bc-referential bc-patient bc-assure bc-pec bc-seance bc-medical \
           bc-comptabilite bc-migration; do
  DEPS=$(echo "$DEPS" | sed "s|C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/$MOD/0.0.1-SNAPSHOT/$MOD-0.0.1-SNAPSHOT.jar|$B/$MOD/target/classes|")
done
for CTX in gmao comptabilite facturation; do
  DEPS=$(echo "$DEPS" | sed "s|C:/Users/TS-CONSULT/.m2/repository/com/hemodialyse/contexts-$CTX/0.0.1-SNAPSHOT/contexts-$CTX-0.0.1-SNAPSHOT.jar|$B/contexts/$CTX/target/classes|")
done
CP="$B/platform/target/test-classes;$B/platform/target/classes;$DEPS;$M/org/junit/platform/junit-platform-launcher/6.0.3/junit-platform-launcher-6.0.3.jar"
CP=$(echo "$CP" | tr ';' ',')
PITCP="$M/org/pitest/pitest/1.30.0/pitest-1.30.0.jar;$M/org/pitest/pitest-entry/1.30.0/pitest-entry-1.30.0.jar;$M/org/pitest/pitest-command-line/1.30.0/pitest-command-line-1.30.0.jar;$M/org/pitest/pitest-html-report/1.30.0/pitest-html-report-1.30.0.jar;$M/org/pitest/pitest-junit5-plugin/1.2.3/pitest-junit5-plugin-1.2.3.jar;$M/net/sf/jopt-simple/jopt-simple/4.9/jopt-simple-4.9.jar;$M/org/apache/commons/commons-text/1.14.0/commons-text-1.14.0.jar;$M/org/apache/commons/commons-lang3/3.16.0/commons-lang3-3.16.0.jar"
cd /c/Users/TS-CONSULT/WebstormProjects/Hemodialyse/backend/platform
D=com.hemodialyse.backend.domain.facturation
I=com.hemodialyse.backend.infrastructure
java -cp "$PITCP" org.pitest.mutationtest.commandline.MutationCoverageReport \
  --reportDir "$B/platform/target/pit-reports-f3" \
  --targetClasses "$D.service.FacturationDomainService,$D.aggregate.FactureAggregate,$D.entity.LigneFacture,$D.valueobject.FactureNumberTemplate,$D.valueobject.FacturationPeriod,$D.specification.SeanceEligibleForFacturationSpecification,$I.persistence.adapter.FactureJdbcAdapter,$I.persistence.adapter.SeanceFacturationJdbcAdapter,com.hemodialyse.backend.application.facturation.FacturationApplicationService,$I.web.rest.FacturationRestController" \
  --excludedMethods "dashboard,getSettings,updateSettings,loadDashboard,printSynthese,buildPrintResponse,lambda\$dashboard*,lambda\$loadDashboard*,lambda\$printSynthese*" \
  --targetTests "com.hemodialyse.backend.characterization.facturation.validation.*" \
  --sourceDirs "$B/platform/src/main/java" \
  --classPath "$CP" \
  --threads 2 --timeoutConst 120000 --timeoutFactor 4 --outputFormats XML,HTML \
  --mutators DEFAULTS $PIT_EXTRA
