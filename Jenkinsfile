pipeline {
    agent any

    parameters {
        choice(name: 'SERVICE', choices: [
            'all',
            'property-service',
            'api-gateway',
            'access-control-service',
            'contract-service',
            'identity-service',
            'notification-service',
            'operation-service',
            'service-registry',
            'user-service',
            'visit-calendar-service'
        ], description: 'Select the service to build')
        
        choice(name: 'DEPLOY_ENV', choices: [
            'dev',
            'staging',
            'production'
        ], description: 'Select the deployment environment')
        
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Run unit tests')
        booleanParam(name: 'RUN_SONAR', defaultValue: true, description: 'Run SonarQube analysis')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Deploy to target environment')
    }

    options {
        timeout(time: 1, unit: 'HOURS')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        REGISTRY = 'docker.io'
        REGISTRY_CREDENTIALS = 'docker-registry-credentials'
        JAVA_VERSION = '21'
        MAVEN_OPTS = '-Xmx1024m -Xms512m'
        SONAR_HOST_URL = credentials('sonarqube-url')
        SONAR_LOGIN = credentials('sonarqube-token')
    }

    stages {
        stage('Preparation') {
            steps {
                script {
                    echo "=========================================="
                    echo "Building Service: ${params.SERVICE}"
                    echo "Target Environment: ${params.DEPLOY_ENV}"
                    echo "=========================================="
                    
                    // Set service list based on parameter
                    if (params.SERVICE == 'all') {
                        env.SERVICES = '''
                            property-service
                            api-gateway
                            access-control-service
                            contract-service
                            identity-service
                            notification-service
                            operation-service
                            service-registry
                            user-service
                            visit-calendar-service
                        '''
                    } else {
                        env.SERVICES = params.SERVICE
                    }
                }
                cleanWs()
                checkout scm
            }
        }

        stage('Build') {
            steps {
                script {
                    def services = env.SERVICES.split()
                    
                    services.each { service ->
                        echo "Building ${service}..."
                        dir(service) {
                            sh '''
                                echo "Running Maven build for ${SERVICE_NAME}..."
                                ./mvnw clean package \
                                    -DskipTests=true \
                                    -Dmaven.compiler.source=${JAVA_VERSION} \
                                    -Dmaven.compiler.target=${JAVA_VERSION} \
                                    -q
                                
                                if [ -f target/*-SNAPSHOT.jar.original ]; then
                                    echo "✓ Build successful for ${SERVICE_NAME}"
                                else
                                    echo "✗ Build failed for ${SERVICE_NAME}"
                                    exit 1
                                fi
                            '''
                        }
                    }
                }
            }
        }

        stage('Unit Tests') {
            when {
                expression { params.RUN_TESTS == true }
            }
            steps {
                script {
                    def services = env.SERVICES.split()
                    
                    services.each { service ->
                        echo "Running tests for ${service}..."
                        dir(service) {
                            sh '''
                                echo "Running unit tests for ${SERVICE_NAME}..."
                                ./mvnw test \
                                    -Dgroups="!integration" \
                                    -q
                                
                                if [ $? -eq 0 ]; then
                                    echo "✓ Tests passed for ${SERVICE_NAME}"
                                else
                                    echo "✗ Tests failed for ${SERVICE_NAME}"
                                    exit 1
                                fi
                            '''
                        }
                    }
                }
            }
        }

        stage('Code Quality Analysis') {
            when {
                expression { params.RUN_SONAR == true }
            }
            steps {
                script {
                    def services = env.SERVICES.split()
                    
                    services.each { service ->
                        echo "Running SonarQube analysis for ${service}..."
                        dir(service) {
                            sh '''
                                echo "SonarQube analysis for ${SERVICE_NAME}..."
                                ./mvnw sonar:sonar \
                                    -Dsonar.projectKey=${SERVICE_NAME} \
                                    -Dsonar.sources=src/main \
                                    -Dsonar.tests=src/test \
                                    -Dsonar.host.url=${SONAR_HOST_URL} \
                                    -Dsonar.login=${SONAR_LOGIN} \
                                    -q || echo "SonarQube analysis completed (non-blocking)"
                            '''
                        }
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    def services = env.SERVICES.split()
                    
                    services.each { service ->
                        echo "Building Docker image for ${service}..."
                        dir(service) {
                            sh '''
                                if [ -f Dockerfile ]; then
                                    SERVICE_VERSION=$(grep -E '<version>.*</version>' pom.xml | head -n 1 | sed 's/.*<version>//;s/<\\/version>.*//')
                                    echo "Service version: ${SERVICE_VERSION}"
                                    
                                    docker build \
                                        -t ${REGISTRY}/${SERVICE_NAME}:${SERVICE_VERSION} \
                                        -t ${REGISTRY}/${SERVICE_NAME}:latest \
                                        .
                                    
                                    echo "✓ Docker image built: ${REGISTRY}/${SERVICE_NAME}:${SERVICE_VERSION}"
                                else
                                    echo "⚠ No Dockerfile found for ${SERVICE_NAME}"
                                fi
                            '''
                        }
                    }
                }
            }
        }

        stage('Push to Registry') {
            when {
                expression { params.DEPLOY == true }
            }
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: env.REGISTRY_CREDENTIALS, usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                        sh '''
                            docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD
                            
                            def services = env.SERVICES.split()
                            services.each { service ->
                                SERVICE_VERSION=$(grep -E '<version>.*</version>' ${service}/pom.xml | head -n 1 | sed 's/.*<version>//;s/<\\/version>.*//')
                                docker push ${REGISTRY}/${service}:${SERVICE_VERSION}
                                docker push ${REGISTRY}/${service}:latest
                                echo "✓ Pushed ${REGISTRY}/${service}:${SERVICE_VERSION}"
                            }
                            
                            docker logout
                        '''
                    }
                }
            }
        }

        stage('Deploy to Target Environment') {
            when {
                expression { params.DEPLOY == true }
            }
            steps {
                script {
                    echo "Deploying to ${params.DEPLOY_ENV} environment..."
                    
                    // Load environment-specific configuration
                    def envConfig = [:]
                    switch(params.DEPLOY_ENV) {
                        case 'dev':
                            envConfig.DOCKER_HOST = credentials('dev-docker-host')
                            envConfig.COMPOSE_PROJECT = 'inmob-dev'
                            break
                        case 'staging':
                            envConfig.DOCKER_HOST = credentials('staging-docker-host')
                            envConfig.COMPOSE_PROJECT = 'inmob-staging'
                            break
                        case 'production':
                            envConfig.DOCKER_HOST = credentials('prod-docker-host')
                            envConfig.COMPOSE_PROJECT = 'inmob-prod'
                            break
                    }
                    
                    sh '''
                        echo "Deploying services to ${DEPLOY_ENV}..."
                        
                        # Deploy using docker-compose
                        DOCKER_HOST=${DOCKER_HOST} docker-compose \
                            -f docker-compose.${DEPLOY_ENV}.yml \
                            -p ${COMPOSE_PROJECT} \
                            pull
                        
                        DOCKER_HOST=${DOCKER_HOST} docker-compose \
                            -f docker-compose.${DEPLOY_ENV}.yml \
                            -p ${COMPOSE_PROJECT} \
                            up -d
                        
                        echo "✓ Deployment completed for ${DEPLOY_ENV}"
                    '''
                }
            }
        }

        stage('Health Check') {
            when {
                expression { params.DEPLOY == true }
            }
            steps {
                script {
                    sh '''
                        echo "Performing health checks..."
                        sleep 10
                        
                        # Health check for services
                        def services = env.SERVICES.split()
                        services.each { service ->
                            HEALTH_CHECK_URL="http://localhost:8080/${service}/actuator/health"
                            RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" ${HEALTH_CHECK_URL})
                            
                            if [ "${RESPONSE}" = "200" ]; then
                                echo "✓ Health check passed for ${service}"
                            else
                                echo "✗ Health check failed for ${service} (HTTP ${RESPONSE})"
                                exit 1
                            fi
                        }
                    '''
                }
            }
        }

        stage('Generate Reports') {
            steps {
                script {
                    sh '''
                        echo "Generating test and build reports..."
                        
                        def services = env.SERVICES.split()
                        services.each { service ->
                            if [ -f "${service}/target/surefire-reports" ]; then
                                echo "✓ Test reports found for ${service}"
                            fi
                        }
                    '''
                }
            }
        }
    }

    post {
        always {
            // Archive test results
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
            
            // Archive build logs
            archiveArtifacts artifacts: '**/target/*.jar*', 
                            allowEmptyArchive: true
            
            // Cleanup
            cleanWs()
        }
        
        success {
            echo "✓ Pipeline execution successful!"
            
            // Send success notification (configure with your notification provider)
            sh '''
                echo "Build successful for service(s): ${SERVICE}"
                echo "Deployed to: ${DEPLOY_ENV}"
            '''
        }
        
        failure {
            echo "✗ Pipeline execution failed!"
            
            // Send failure notification
            sh '''
                echo "Build failed for service(s): ${SERVICE}"
            '''
        }
    }
}
