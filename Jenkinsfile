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
        sh '''echo "WD="$(pwd)
mkdir ./jenkins_styx
unzip -d ./jenkins_styx/ ./distribution/target/styx-0.9-SNAPSHOT-linux-x86_64.zip
'''
      }
    }
    stage('StartUp') {
      steps {
        sh '''cd /jenkins_styx/styx-0.9-SNAPSHOT
./bin/startup conf/env-development/styx-config.yml & 
sleep 10
make -f ../Makefile load-test OPENSSL_INCLUDE_DIR=/usr/include/openssl
kill $!
'''
      }
    }
  }
}