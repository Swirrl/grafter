node {
  stage('Test') {
    sh 'lein test'
  }

  stage('Install') {
    sh 'lein install'
  }

  stage('Deploy') {
    sh 'lein deploy clojars'
  }
}
