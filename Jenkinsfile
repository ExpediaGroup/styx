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
sleep 5 # wait for 1/10 of the second before check again
done
cd ..
make load-test OPENSSL_INCLUDE_DIR=/usr/include
kill $!

'''
      }
    }
  }
}