node {
  checkout scm

  stage('Test') {
    sh 'pwd'
    sh 'pwd > pwd-out'
    sh 'lein test'
  }

  stage('Install') {
    sh 'lein install'
  }

  stage('Deploy') {
    sh 'lein deploy clojars'
  }
}
