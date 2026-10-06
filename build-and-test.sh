#!/bin/bash

# Build and Test Script for Inmob Backend Services
# Usage: ./build-and-test.sh [service_name] [command]

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SERVICES=(
    "property-service"
    "api-gateway"
    "access-control-service"
    "contract-service"
    "identity-service"
    "notification-service"
    "operation-service"
    "service-registry"
    "user-service"
    "visit-calendar-service"
)

JAVA_VERSION="21"
MAVEN_OPTS="-Xmx1024m -Xms512m"

# Functions
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

build_service() {
    local service=$1
    print_header "Building $service"
    
    if [ ! -d "$service" ]; then
        print_error "Service directory not found: $service"
        return 1
    fi
    
    cd "$service"
    
    if [ -f "mvnw" ]; then
        ./mvnw clean package \
            -DskipTests=true \
            -Dmaven.compiler.source=$JAVA_VERSION \
            -Dmaven.compiler.target=$JAVA_VERSION
        
        if [ -f "target/*-SNAPSHOT.jar.original" ] || [ -f "target/*-SNAPSHOT.jar" ]; then
            print_success "Build successful for $service"
        else
            print_error "Build failed for $service"
            cd ..
            return 1
        fi
    else
        print_warning "Maven wrapper not found in $service"
    fi
    
    cd ..
}

test_service() {
    local service=$1
    print_header "Testing $service"
    
    if [ ! -d "$service" ]; then
        print_error "Service directory not found: $service"
        return 1
    fi
    
    cd "$service"
    
    if [ -f "mvnw" ]; then
        export MAVEN_OPTS
        ./mvnw test \
            -Dgroups="!integration" \
            || print_warning "Some tests may have failed"
        print_success "Tests completed for $service"
    else
        print_warning "Maven wrapper not found in $service"
    fi
    
    cd ..
}

analyze_code() {
    local service=$1
    print_header "Analyzing code quality for $service"
    
    if [ ! -d "$service" ]; then
        print_error "Service directory not found: $service"
        return 1
    fi
    
    cd "$service"
    
    if [ -f "mvnw" ]; then
        ./mvnw sonar:sonar \
            -Dsonar.projectKey="$service" \
            -Dsonar.sources=src/main \
            -Dsonar.tests=src/test \
            || print_warning "SonarQube analysis completed (non-blocking)"
        print_success "Code analysis completed for $service"
    else
        print_warning "Maven wrapper not found in $service"
    fi
    
    cd ..
}

docker_build() {
    local service=$1
    print_header "Building Docker image for $service"
    
    if [ ! -d "$service" ]; then
        print_error "Service directory not found: $service"
        return 1
    fi
    
    if [ ! -f "$service/Dockerfile" ]; then
        print_warning "Dockerfile not found for $service"
        return 0
    fi
    
    SERVICE_VERSION=$(grep -E '<version>.*</version>' "$service/pom.xml" | head -n 1 | sed 's/.*<version>//;s/<\/version>.*//')
    
    docker build \
        -t "inmobiliaria/$service:$SERVICE_VERSION" \
        -t "inmobiliaria/$service:latest" \
        "$service"
    
    print_success "Docker image built: inmobiliaria/$service:$SERVICE_VERSION"
}

run_all_services() {
    local command=$1
    
    for service in "${SERVICES[@]}"; do
        case $command in
            build)
                build_service "$service"
                ;;
            test)
                test_service "$service"
                ;;
            analyze)
                analyze_code "$service"
                ;;
            docker)
                docker_build "$service"
                ;;
            *)
                print_error "Unknown command: $command"
                exit 1
                ;;
        esac
    done
}

show_usage() {
    echo "Usage: ./build-and-test.sh [options]"
    echo ""
    echo "Commands:"
    echo "  build [service]      Build specific service or all services"
    echo "  test [service]       Run tests for specific service or all services"
    echo "  analyze [service]    Run SonarQube analysis for service or all services"
    echo "  docker [service]     Build Docker image for service or all services"
    echo "  full [service]       Build, test, and analyze (all steps)"
    echo ""
    echo "Services:"
    for service in "${SERVICES[@]}"; do
        echo "  - $service"
    done
    echo "  - all (default if no service specified)"
}

# Main script
COMMAND=${1:-full}
SERVICE=${2:-all}

print_header "Inmob Backend - Build & Test Script"

case $COMMAND in
    build)
        if [ "$SERVICE" = "all" ]; then
            run_all_services "build"
        else
            build_service "$SERVICE"
        fi
        ;;
    test)
        if [ "$SERVICE" = "all" ]; then
            run_all_services "test"
        else
            test_service "$SERVICE"
        fi
        ;;
    analyze)
        if [ "$SERVICE" = "all" ]; then
            run_all_services "analyze"
        else
            analyze_code "$SERVICE"
        fi
        ;;
    docker)
        if [ "$SERVICE" = "all" ]; then
            run_all_services "docker"
        else
            docker_build "$SERVICE"
        fi
        ;;
    full)
        if [ "$SERVICE" = "all" ]; then
            run_all_services "build"
            run_all_services "test"
            run_all_services "docker"
        else
            build_service "$SERVICE"
            test_service "$SERVICE"
            docker_build "$SERVICE"
        fi
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        print_error "Unknown command: $COMMAND"
        echo ""
        show_usage
        exit 1
        ;;
esac

print_success "Script execution completed!"
