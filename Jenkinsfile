pipeline {
  agent {
    docker {
      image 'maven:3.5.3'
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

/usr/bin/make load-test
#kill $!
ps ax | grep -i \'StyxServer\' | grep -v grep
./bin/shutdown'''
      }
    }
  }
}