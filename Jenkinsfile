pipeline {
  environment {
    devRegistry = 'ghcr.io/datakaveri/controlplane-dev'
    registryUri = 'https://ghcr.io'
    registryCredential = 'datakaveri-ghcr'
    GIT_HASH = GIT_COMMIT.take(7)
  }

  agent { 
    node {
      label 'slave1' 
    }
  }

  stages {

    stage('Conditional Execution') {
      when {
        allOf {
          anyOf {
            changeset "docker/**"
            changeset "docs/**"
            changeset "pom.xml"
            changeset "src/main/**"
            changeset "example-config/config.json"
            triggeredBy cause: 'UserIdCause'
          }
          expression {
            return env.BRANCH_NAME == 'dev' || env.BRANCH_NAME.startsWith('PR-');
          }
        }
      }

      stages {

        stage('Trivy Code Scan (Dependencies)') {
          steps {
            script {
              sh '''
                trivy fs --scanners vuln,secret,misconfig --output trivy-fs-report.txt .
              '''
            }
          }
        }

        stage('Building images') {
          steps{
            script {

              echo 'Pulled - ' + env.GIT_BRANCH

              DX_COMMON_COMMIT = sh(
                script: "git ls-remote https://github.com/datakaveri/dx-common.git refs/heads/dev | cut -f1",
                returnStdout: true
              ).trim()

              echo "DX_COMMON_COMMIT=${DX_COMMON_COMMIT}"

              devImage = docker.build(
                devRegistry,
                "--build-arg CACHE_BUST=${DX_COMMON_COMMIT} -f ./docker/dev.dockerfile ."
              )
            }
          }
        }

        stage('Trivy Scan and Report') {
          steps {
            script {
              try {
                sh """
                trivy image \\
                  --exit-code 1 \\
                  --severity HIGH,CRITICAL \\
                  --ignore-unfixed \\
                  ${devImage.imageName()}
                """
                sh "trivy image --output trivy-dev-image-report.txt ${devImage.imageName()}"
              } catch (Exception e) {
                echo "Trivy scan failed due to high or critical vulnerabilities."
                throw e
              }
            }
          }
          post {
            always {
              archiveArtifacts artifacts: 'trivy-*.txt', allowEmptyArchive: true
              publishHTML(target: [
                allowMissing: true,
                keepAll: true,
                reportDir: '.',
                reportFiles: 'trivy-fs-report.txt, trivy-dev-image-report.txt',
                reportName: 'Trivy Reports'
              ])
            }
          }
        }

        stage('Detect config/migration change') {
          when {
            not { changeRequest() }
          }
          steps {
            script {
              def baseCommit = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT
              if (!baseCommit) {
                baseCommit = sh(script: 'git rev-list --max-parents=0 HEAD | tail -1', returnStdout: true).trim()
              }

              def changedFiles = sh(
                script: "git diff --name-only ${baseCommit} HEAD",
                returnStdout: true
              ).trim().split('\n') as List

              env.CONFIG_CHANGED = changedFiles.contains('example-config/config.json') ? 'true' : 'false'
              env.MIGRATION_CHANGED = changedFiles.any { it.startsWith('src/main/resources/db/migration/') } ? 'true' : 'false'

              echo "Diffing against ${baseCommit} (last successful build's commit): config changed=${env.CONFIG_CHANGED}, migration changed=${env.MIGRATION_CHANGED}"
            }
          }
        }

        stage('Continuous Deployment') {
          when {
            expression {
              return env.BRANCH_NAME == 'dev'
            }
          }

          stages {

            stage('Push Images') {
              steps {
                script {
                  def tagSuffix = ''
                  if (env.CONFIG_CHANGED == 'true') {
                    tagSuffix += '-C'
                  }
                  if (env.MIGRATION_CHANGED == 'true') {
                    tagSuffix += '-M'
                  }
                  env.IMAGE_TAG = "1.0.0-${env.GIT_HASH}${tagSuffix}"
                  docker.withRegistry(registryUri, registryCredential) {
                    devImage.push(env.IMAGE_TAG)
                  }
                }
              }
            }

                stage('EKS Helm deployment') {
                  steps {
                    script {
                      sh "ssh ubuntu@dev-eks 'cd v2-deployments/iudx/iudx-installer/K8s-deployment/Charts/controlplane && helm upgrade iudx-control-plane . -n control-plane --atomic --timeout 5m --reuse-values --set image.repository=${devRegistry} --set image.tag=${env.IMAGE_TAG}'"
                    }
                  }
                  post{
                    failure{
                      error "Failed to deploy image to EKS via Helm"
                    }
                  }
                }

          }
        }

      }
    }

  }

  post{
    failure{
      script{
        if (env.BRANCH_NAME == 'dev') {
          emailext recipientProviders: [buildUser(), developers()],
          to: '$AAA_RECIPIENTS, $DEFAULT_RECIPIENTS',
          subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!',
          body: '''$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS:
Check console output at $BUILD_URL to view the results.'''
        }
      }
    }
  }
}
