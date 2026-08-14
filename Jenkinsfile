// ============================================================
// Jenkinsfile — Hemodialyse CI/CD Pipeline
// Pipeline déclaratif — Jenkins 2.x LTS
// Prérequis plugins: Pipeline, Git, Docker Pipeline, JUnit,
//   HTML Publisher, Slack Notification (optionnel)
// ============================================================

pipeline {

    agent any

    // --------------------------------------------------------
    // Paramètres configurables
    // --------------------------------------------------------
    parameters {
        booleanParam(name: 'SKIP_TESTS',       defaultValue: false, description: 'Passer les tests (builds rapides)')
        booleanParam(name: 'PUSH_DOCKER',      defaultValue: false, description: 'Pousser les images Docker')
        booleanParam(name: 'DEPLOY_PROD',      defaultValue: false, description: 'Déployer en production (branche main uniquement)')
        string(name:  'DOCKER_REGISTRY',       defaultValue: 'ghcr.io/hemodialyse', description: 'Registry Docker')
        string(name:  'IMAGE_TAG',             defaultValue: "${env.BUILD_NUMBER}", description: 'Tag image Docker')
    }

    // --------------------------------------------------------
    // Variables d'environnement
    // --------------------------------------------------------
    environment {
        JAVA_HOME   = tool 'JDK-21'
        NODE_HOME   = tool 'NodeJS-22'
        MAVEN_OPTS  = '-Xmx1g -Dmaven.artifact.threads=4'
        MAVEN_CMD   = './mvnw --batch-mode --quiet'
        PATH        = "${env.JAVA_HOME}/bin:${env.NODE_HOME}/bin:${env.PATH}"
    }

    // --------------------------------------------------------
    // Options globales
    // --------------------------------------------------------
    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timestamps()
    }

    // --------------------------------------------------------
    // Stages
    // --------------------------------------------------------
    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.GIT_BRANCH_SAFE  = env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9_.-]', '-')
                    echo "Branch: ${env.BRANCH_NAME} | Commit: ${env.GIT_COMMIT_SHORT}"
                }
            }
        }

        // ---- BUILD parallèle Backend + Frontend ----
        stage('Build') {
            parallel {

                stage('Build Backend') {
                    steps {
                        dir('backend') {
                            sh "${MAVEN_CMD} clean compile"
                        }
                    }
                }

                stage('Build Frontend') {
                    steps {
                        dir('frontend') {
                            sh 'npm ci --prefer-offline --no-audit'
                            sh 'npm run build -- --configuration production'
                        }
                    }
                }
            }
        }

        // ---- TEST parallèle Backend + Frontend ----
        stage('Test') {
            when { expression { !params.SKIP_TESTS } }
            parallel {

                stage('Test Backend') {
                    steps {
                        dir('backend') {
                            sh "${MAVEN_CMD} verify"
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'backend/target/surefire-reports/TEST-*.xml'
                            publishHTML(target: [
                                allowMissing: true,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'backend/target/site/jacoco',
                                reportFiles: 'index.html',
                                reportName: 'JaCoCo Coverage Report'
                            ])
                        }
                    }
                }

                stage('Test Frontend') {
                    steps {
                        dir('frontend') {
                            sh 'npm test -- --watch=false --reporter=verbose'
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'frontend/test-results/*.xml'
                        }
                    }
                }
            }
        }

        // ---- QUALITY GATE SonarQube (optionnel) ----
        stage('Quality Gate (SonarQube)') {
            when {
                allOf {
                    not { expression { params.SKIP_TESTS } }
                    expression { env.SONAR_HOST_URL != null && env.SONAR_HOST_URL != '' }
                }
            }
            steps {
                dir('backend') {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        sh """
                            ${MAVEN_CMD} sonar:sonar \
                              -Dsonar.host.url=${env.SONAR_HOST_URL} \
                              -Dsonar.token=${SONAR_TOKEN} \
                              -Dsonar.qualitygate.wait=true
                        """
                    }
                }
            }
        }

        // ---- BUILD IMAGES DOCKER ----
        stage('Build Docker Images') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    expression { params.PUSH_DOCKER }
                }
            }
            parallel {

                stage('Docker Backend') {
                    steps {
                        dir('backend') {
                            sh """
                                docker build \
                                  -t ${params.DOCKER_REGISTRY}/backend:${params.IMAGE_TAG} \
                                  -t ${params.DOCKER_REGISTRY}/backend:${env.GIT_BRANCH_SAFE} \
                                  -t ${params.DOCKER_REGISTRY}/backend:latest \
                                  .
                            """
                        }
                    }
                }

                stage('Docker Frontend') {
                    steps {
                        dir('frontend') {
                            sh """
                                docker build \
                                  -t ${params.DOCKER_REGISTRY}/frontend:${params.IMAGE_TAG} \
                                  -t ${params.DOCKER_REGISTRY}/frontend:${env.GIT_BRANCH_SAFE} \
                                  -t ${params.DOCKER_REGISTRY}/frontend:latest \
                                  .
                            """
                        }
                    }
                }
            }
        }

        // ---- PUSH DOCKER REGISTRY ----
        stage('Push to Registry') {
            when { expression { params.PUSH_DOCKER } }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-registry-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh "echo ${DOCKER_PASS} | docker login ${params.DOCKER_REGISTRY} -u ${DOCKER_USER} --password-stdin"
                    sh "docker push ${params.DOCKER_REGISTRY}/backend:${params.IMAGE_TAG}"
                    sh "docker push ${params.DOCKER_REGISTRY}/backend:${env.GIT_BRANCH_SAFE}"
                    sh "docker push ${params.DOCKER_REGISTRY}/frontend:${params.IMAGE_TAG}"
                    sh "docker push ${params.DOCKER_REGISTRY}/frontend:${env.GIT_BRANCH_SAFE}"
                    sh "docker logout ${params.DOCKER_REGISTRY}"
                }
            }
        }

        // ---- DEPLOY PRODUCTION (manuel) ----
        stage('Deploy Production') {
            when {
                allOf {
                    branch 'main'
                    expression { params.DEPLOY_PROD }
                }
            }
            steps {
                input(message: "Déployer en PRODUCTION (${params.IMAGE_TAG}) ?", ok: "Déployer")
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'deploy-ssh-key',
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no ${SSH_USER}@${env.DEPLOY_HOST} \\
                          "cd ${env.DEPLOY_PATH} && \\
                           docker-compose --profile full pull && \\
                           docker-compose --profile full up -d --remove-orphans && \\
                           docker system prune -f"
                    """
                }
            }
        }

    } // end stages

    // --------------------------------------------------------
    // Post actions
    // --------------------------------------------------------
    post {
        always {
            archiveArtifacts artifacts: 'frontend/dist/**', allowEmptyArchive: true
            cleanWs()
        }
        success {
            echo "✅ Build ${env.BUILD_NUMBER} terminé avec succès — ${env.GIT_COMMIT_SHORT}"
            // Décommenter pour les notifications Slack :
            // slackSend channel: '#cicd', color: 'good',
            //   message: "✅ Hemodialyse build #${env.BUILD_NUMBER} réussi (${env.BRANCH_NAME})"
        }
        failure {
            echo "❌ Build ${env.BUILD_NUMBER} échoué — ${env.GIT_COMMIT_SHORT}"
            // Décommenter pour les notifications Slack :
            // slackSend channel: '#cicd', color: 'danger',
            //   message: "❌ Hemodialyse build #${env.BUILD_NUMBER} ÉCHOUÉ (${env.BRANCH_NAME}) — ${env.BUILD_URL}"
        }
        unstable {
            echo "⚠️ Build ${env.BUILD_NUMBER} instable (tests en échec)"
        }
    }

}

