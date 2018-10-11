pipeline {
  agent {
    docker {
      image 'edge-jenkins:latest'
      args '-v $HOME/.m2:/root/.m2'
    }

  }
  stages {
    stage('StartUp') {
      steps {
        sh '''make start-with-origins STACK=perf-local PLATFORM=linux & 
while ! nc -z localhost 8080; do 
sleep 5
done'''
      }
    }
    stage('Test') {
      steps {
        sh '''make load-test OPENSSL_INCLUDE_DIR=/usr/include
'''
      }
    }
  }
}