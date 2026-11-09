pipeline {
    agent any

    environment {
        // Force JDK 21 (Maven was picking up Java 27 otherwise)
        JAVA_HOME = '/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home'
        PATH = "${JAVA_HOME}/bin:/opt/homebrew/bin:${env.PATH}"
        // Talk to your Colima Docker
        DOCKER_HOST = "unix://${env.HOME}/.colima/default/docker.sock"
        IMAGE_NAME = 'claimprocessingengine-local'
        IMAGE_TAG  = "local-${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo 'Checkout OK'
            }
        }

        stage('Check Tools') {
            steps {
                sh '''
                    echo "== java ==";  java -version
                    echo "== maven =="; mvn -version
                    echo "== docker =="; docker version --format '{{.Server.Version}}'
                '''
            }
        }

        stage('Build (Maven)') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Verify Jar') {
            steps {
                sh 'ls -l target/claims-1.0.0.jar'
            }
        }

        stage('Docker Build (local only)') {
            steps {
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
            }
        }

        stage('Verify Image') {
            steps {
                sh "docker images ${IMAGE_NAME}"
            }
        }
    }

    post {
        success { echo 'Local build successful — no push, no deploy.' }
        failure { echo 'Local build failed.' }
    }
}