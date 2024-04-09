def FAILED_STAGE
pipeline {
    agent any
    tools {
        nodejs 'nodejs'
    }
    parameters {
        choice choices: ["Baseline", "APIS", "Full"],
            description: 'Type of scan that is going to perform inside the container',
            name: 'SCAN_TYPE'

        string defaultValue: "http://<Load-Balancer-IP>/Ingress-Route",
            description: 'Target URL to scan',
            name: 'TARGET'

        booleanParam defaultValue: true,
            description: 'Parameter to know if wanna generate report.',
            name: 'GENERATE_REPORT'    
    }
    environment {
        DOCKERHUB_USERNAME = '<username>'
        APP_NAME = '<App-Name>'
        IMAGE_TAG = "${BUILD_NUMBER}"
        IMAGE_NAME = "${DOCKERHUB_USERNAME}/${APP_NAME}"
        REGISTRY_CREDS = 'dockerhub'
    }
    stages {
        stage('Cleanup Workspace') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    cleanWs()
                }
            }
        }
        stage('Checkout git') {
            steps {
                script {
                    FAILED_STAGE = env.STAGE_NAME
                    sh """
                        git config --global http.postBuffer 1048576000
                    """
                }
               
                timeout(time: 10, unit: 'MINUTES') {
                    // Set the timeout duration according to your needs
                   
                    // Body of the timeout step
                    git credentialsId: 'github',
                        url: '<Repo-URL>',
                        branch: 'master'
                }
            }
        }

        stage('SonarQube Analysis'){
            steps{
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    withSonarQubeEnv('sonarqube') {                        
                            sh """
                                /home/sonar/bin/sonar-scanner \
                                -Dsonar.projectKey=demoapp \
                                -Dsonar.sources=. \
                                -Dsonar.host.url=<Host-IP> \
                                -Dsonar.token=<Sonar-Token>
                            """
                    }
                }    
            }
        }
        stage("Quality Gate") {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                }
            timeout(time: 1, unit: 'HOURS') {
                waitForQualityGate abortPipeline: false
            }
            }
        }


        stage('Docker Build') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    docker_image = docker.build(IMAGE_NAME)
                }
            }
        }

        stage('Image Scan') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                }
                sh """
                    mkdir -p /home/trivy/reports
                    #chmod 777 /home/trivy/reports
                    cd /home/trivy/reports
                    touch image-results-${BUILD_NUMBER}.txt
                    trivy image --format table -o /home/trivy/reports/image-results-${BUILD_NUMBER}.txt $IMAGE_NAME:latest
                """
               
            }
        }

        stage('Docker login & pushing image') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    docker.withRegistry('', REGISTRY_CREDS) {
                        docker_image.push(BUILD_NUMBER)
                        docker_image.push('latest')
                    }
                }
            }
        }

        stage('Delete Docker Images') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    sh "docker rmi ${IMAGE_NAME}:${IMAGE_TAG}"
                    sh "docker rmi ${IMAGE_NAME}:latest"
                }
            }
        }

                stage('Checkout Demoapp-Argo-CD') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    git credentialsId: 'github',
                        url: '<Repo-Url>',
                        branch: 'main'
                }
            }
        }

        stage('Updating Kubernetes Deployment file') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    sh """
                    cat Deployment.yml
                    sed -i 's|image: ${IMAGE_NAME}.*|image: ${IMAGE_NAME}:${IMAGE_TAG}|' Deployment.yml
                    cat Deployment.yml
                    """
                }
            }
        }
        stage('Push The Changed Deployment File To Git') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    sh """
                    git config --global user.name "<Username>"
                    git config --global user.email "<Email>"
                    git add Deployment.yml
                    git commit -m "Update the deployment file"
                    """
                    withCredentials([gitUsernamePassword(credentialsId: 'github', gitToolName: 'Default')]) {
                        sh """
                            git push <Repo-URL> main
                            git remote remove origin
                        """  
                    }
                }
            }
        }

        stage('Pipeline Info') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    echo "<--Parameter Initialization-->"
                    echo """
                    The current parameters are:
                        Scan Type: ${params.SCAN_TYPE}
                        Target: ${params.TARGET}
                        Generate report: ${params.GENERATE_REPORT}
                    """
                }
            }
        }

        stage('Setting up OWASP ZAP docker container') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    echo "Pulling up last OWASP ZAP container --> Start"
                    sh 'docker pull owasp/zap2docker-stable'
                    echo "Pulling up last VMS container --> End"
                    echo "Starting container --> Start"
                    sh """
                    docker run -dt --name owasp \
                    owasp/zap2docker-stable \
                    /bin/bash
                    """
                }
            }
        }

        stage('Prepare wrk directory') {
            when {
                environment name: 'GENERATE_REPORT', value: 'true'
            }
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    sh """
                    docker exec owasp \
                    mkdir /zap/wrk
                    """
                }
            }
        }

        stage('Scanning target on owasp container') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    def scan_type = "${params.SCAN_TYPE}"
                    echo "----> scan_type: $scan_type"
                    def target = "${params.TARGET}"
                    if (scan_type == "Baseline") {
                        sh """
                        docker exec owasp \
                        zap-baseline.py \
                        -t $target \
                        -r report.html \
                        -I
                        """
                    } else if (scan_type == "APIS") {
                        sh """
                        docker exec owasp \
                        zap-api-scan.py \
                        -t $target \
                        -r report.html \
                        -I
                        """
                    } else if (scan_type == "Full") {
                        sh """
                        docker exec owasp \
                        zap-full-scan.py \
                        -t $target \
                        -r report.html \
                        -I
                        """
                    } else {
                        echo "Something went wrong..."
                    }
                }
            }
        }

        stage('Copy Report to Workspace') {
            steps {
                script {
                    FAILED_STAGE=env.STAGE_NAME
                    sh '''
                    docker cp owasp:/zap/wrk/report.html ${WORKSPACE}/ZAP-report-${BUILD_NUMBER}.html
                    mv ${WORKSPACE}/ZAP-report-${BUILD_NUMBER}.html /home/DAST
                    '''
                }
            }
        }



    }    
    post {
        failure {
            script {
                echo "Failed stage name: ${FAILED_STAGE}"
                sh """
                    if docker ps -a | grep -q myapp; then
                        docker stop myapp
                        docker rm myapp
                    fi
                    rm -rf /home/trivy/project
                """
                withCredentials([string(credentialsId: 'ntfy', variable: 'SECRET_VARIABLE')]) {
                    sh """
                        curl \
                        -u :${SECRET_VARIABLE} \
                        -H "Title: Failure Details" \
                        -d "Pipeline failed in the stage: ${FAILED_STAGE}\nCheck the jenkins job for details." \
                        http://<Ntfy-URL>/<Topic>
                    """
                }
            }
        }
        success {
            script {
                withCredentials([string(credentialsId: 'ntfy', variable: 'SECRET_VARIABLE')]) {
                    sh """
                        curl \
                        -u :${SECRET_VARIABLE} \
                        -H "Title: Front End Pipeline Status" \
                        -d "The status of your built-${BUILD_NUMBER} is ${currentBuild.result}." \
                        http://<Ntfy-URL>/<Topic> # Here the top should be revised    
                    """
                }            
                sh """
                    git config --global user.name "<Username>"
                    git config --global user.email "<Email>"
                    cd /home/publish-reports
                    git init
                    if git remote | grep origin > /dev/null; then
                        echo "Remote origin already exists."
                    else
                        # Add the remote origin since it does not exist
                        git remote add origin <Repo-URL>
                        echo "Remote origin added."
                    fi
                    git checkout -B reports
                    mv /home/trivy/reports/report-${BUILD_NUMBER}.txt /home/publish-reports/frontend/trivyfilescanning
                    mv /home/DAST/ZAP-report-${BUILD_NUMBER}.html /home/publish-reports/frontend/DAST
                    mv /home/trivy/reports/image-results-${BUILD_NUMBER}.txt /home/publish-reports/frontend/trivyimagescanning
                    git add .
                    git commit -m 'publishing report of built-${BUILD_NUMBER}'
                """
                withCredentials([gitUsernamePassword(credentialsId: 'github', gitToolName: 'Default')]) {
                        sh """
                            cd /home/publish-reports
                            git pull --rebase <Repo-URL> reports
                            git push <Repo-URL> reports
                        """
                }
            }
        }
        always {
            script {
                echo "Removing container"
                sh '''
                docker stop owasp
                docker rm owasp
                if git remote | grep origin > /dev/null; then
                    git remote remove origin
                    echo "Remote origin removed"
                else
                    # Add the remote origin since it does not exist
                    echo "Remote origin does not exists."
                fi
                '''
            }
        }      
    }    
}