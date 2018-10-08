pipeline {
  agent {
    docker {
      image 'edge-jenkins:latest'
    }

  }
  stages {
    stage('Build') {
      steps {
        sh 'mvn install -Prelease,linux -Dmaven.test.skip=true'
      }
    }
    stage('Deploy') {
      steps {
        sh '''unzip ./distribution/target/styx-0.9-SNAPSHOT-linux-x86_64.zip
'''
      }
    }
    stage('StartUp') {
      steps {
        sh '''cd styx-0.9-SNAPSHOT
./bin/startup conf/env-development/styx-config.yml & 
sleep 10
cd ..
#make -f ../Makefile load-test OPENSSL_INCLUDE_DIR=/usr/include
make load-test OPENSSL_INCLUDE_DIR=/usr/include
kill $!
'''
      }
    }
  }
}