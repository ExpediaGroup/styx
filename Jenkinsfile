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
while ! nc -z localhost 8080; do 
sleep 1 
done
make load-test OPENSSL_INCLUDE_DIR=/usr/include
#kill $!

'''
      }
    }
  }
}