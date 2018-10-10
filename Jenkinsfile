pipeline {
  agent {
    docker {
      image 'edge-jenkins:latest'
    }

  }
  stages {
    stage('StartUp') {
      steps {
        sh '''make start-with-origins STACK=perf-local & 
sleep 180 make load-test OPENSSL_INCLUDE_DIR=/usr/include
kill $!
'''
      }
    }
  }
}