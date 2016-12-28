node {

  stage('Checkout') {
    checkout scm
  }

  stage('Test') {
    sh 'lein test'
  }

  stage('Install') {
    sh 'lein install'
  }

  stage('Deploy') {
    echo 'TODO: Setup Deploy'
    // sh 'lein deploy clojars'
  }
}
