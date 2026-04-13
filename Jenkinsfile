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
              devImage = docker.build(devRegistry, "-f ./docker/dev.dockerfile .")
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
                  docker.withRegistry(registryUri, registryCredential) {
                    devImage.push("1.0.0-${env.GIT_HASH}")
                  }
                }
              }
            }

            stage('Docker Swarm deployment') {
              steps {
                script {
                  sh "ssh azureuser@docker-swarm 'docker service update iudx-v2-controlplane_controlplane-iudx-v2 --image ghcr.io/datakaveri/controlplane-dev:1.0.0-${env.GIT_HASH}'"
                  sh 'sleep 15'
                  sh '''#!/bin/bash 
                  response_code=$(curl -s -o /dev/null -w \'%{http_code}\\n\' --connect-timeout 5 --retry 5 --retry-connrefused -XGET https://v2.dev.controlplane.iudx.io/apis)

                  if [[ "$response_code" -ne "200" ]]
                  then
                    echo "Health check failed"
                    exit 1
                  else
                    echo "Health check complete; Server is up."
                    exit 0
                  fi
                  '''
                }
              }
              post{
                failure{
                  error "Failed to deploy image in Docker Swarm"
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
        if (env.BRANCH_NAME == 'dev')
        emailext recipientProviders: [buildUser(), developers()],
        to: '$AAA_RECIPIENTS, $DEFAULT_RECIPIENTS',
        subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!',
        body: '''$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS:
Check console output at $BUILD_URL to view the results.'''
      }
    }
  }
}
