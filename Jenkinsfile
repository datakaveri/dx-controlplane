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

    stage('Building images') {
      steps{
        script {
          echo 'Pulled - ' + env.GIT_BRANCH
          devImage = docker.build(devRegistry, "-f ./docker/dev.dockerfile .")
        }
      }
    }

    stage('Push Images') {
      steps {
        script {
          docker.withRegistry(registryUri, registryCredential) {
            devImage.push("v2.2.RC1-${env.GIT_HASH}")
          }
        }
      }
    }

  }

  post{
    failure{
      script{
        emailext recipientProviders: [buildUser(), developers()],
        to: '$AAA_RECIPIENTS, $DEFAULT_RECIPIENTS',
        subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!',
        body: '''$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS:
Check console output at $BUILD_URL to view the results.'''
      }
    }
  }
}
