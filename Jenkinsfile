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
        sh '''whoami || true
echo $PATH || true
ls -laF /usr/bin || true
cd styx-0.9-SNAPSHOT
./bin/startup conf/env-development/styx-config.yml & echo TEST
kill $!
'''
      }
    }
  }
}