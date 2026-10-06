# Guía de Pruebas Unitarias y CI/CD

Este documento describe cómo usar las pruebas unitarias y el archivo Jenkinsfile para construir e instalar los servicios en un ambiente destino.

## 📋 Contenido

- [Pruebas Unitarias](#pruebas-unitarias)
- [Jenkinsfile](#jenkinsfile)
- [Scripts de Build](#scripts-de-build)
- [Ejecución Local](#ejecución-local)
- [Integración con Jenkins](#integración-con-jenkins)

## 🧪 Pruebas Unitarias

### Estructura de las Pruebas

Se han creado pruebas unitarias para cada servicio usando **JUnit 5** y **Mockito**:

```
property-service/src/test/java/com/inmobiliaria/property_service/
├── service/
│   ├── PropertyServiceTest.java       # Tests para PropertyService
│   └── ImageServiceTest.java          # Tests para ImageService
├── controller/
│   └── PropertyControllerTest.java    # Tests para PropertyController
└── repository/
    └── PropertyRepositoryTest.java    # Tests para PropertyRepository
```

### Tipos de Pruebas Incluidas

1. **Service Tests** (`PropertyServiceTest.java`):
   - Búsqueda de propiedades
   - Creación y actualización
   - Validación de seguridad y permisos

2. **Controller Tests** (`PropertyControllerTest.java`):
   - Endpoints REST
   - Validación de solicitudes
   - Manejo de errores

3. **Repository Tests** (`PropertyRepositoryTest.java`):
   - Operaciones CRUD
   - Queries personalizadas
   - Lógica de eliminación

### Ejecutar Pruebas Localmente

#### Con Maven (Todos los servicios)

```bash
# Ejecutar todas las pruebas
mvn test

# Ejecutar solo un servicio
cd property-service
mvn test

# Ejecutar una clase de test específica
mvn test -Dtest=PropertyServiceTest

# Ejecutar un método específico
mvn test -Dtest=PropertyServiceTest#testFindByIdSuccess
```

#### Con los Scripts Proporcionados

**Windows (PowerShell):**

```powershell
# Ejecutar pruebas para todos los servicios
.\build-and-test.ps1 -Command test

# Ejecutar pruebas para un servicio específico
.\build-and-test.ps1 -Command test -Service property-service

# Build + Test + Docker
.\build-and-test.ps1 -Command full
```

**Linux/Mac (Bash):**

```bash
# Ejecutar pruebas para todos los servicios
./build-and-test.sh test

# Ejecutar pruebas para un servicio específico
./build-and-test.sh test property-service

# Build + Test + Docker
./build-and-test.sh full
```

### Configuración de Pruebas

El archivo `application-test.yml` proporciona configuración para tests:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/inmob_property_test
      auto-index-creation: true
  cloud:
    config:
      enabled: false
    discovery:
      enabled: false

eureka:
  client:
    enabled: false
```

### Requisitos para Ejecutar Tests Localmente

- **Java 21+**
- **Maven 3.6+**
- **MongoDB** (para integration tests)

Opcionalmente, usando Docker:

```bash
# Iniciar MongoDB para tests
docker-compose -f docker-compose.yml --profile dev up -d mongo-dev

# Ejecutar tests
mvn test

# Detener servicios
docker-compose -f docker-compose.yml --profile dev down
```

### Cobertura de Código

Para generar reportes de cobertura con JaCoCo:

```bash
mvn clean test jacoco:report

# El reporte estará en: target/site/jacoco/index.html
```

## 🔄 Jenkinsfile

El archivo `Jenkinsfile` en la raíz del proyecto automatiza el proceso de:

1. **Build** - Compilación con Maven
2. **Tests** - Ejecución de pruebas unitarias
3. **Análisis de Código** - SonarQube
4. **Docker Build** - Construcción de imágenes
5. **Push a Registro** - Envío a Docker Registry
6. **Deploy** - Despliegue a ambientes destino

### Etapas del Pipeline

```
Preparation
    ↓
Build
    ↓
Unit Tests (Opcional)
    ↓
Code Quality (Opcional - SonarQube)
    ↓
Docker Build
    ↓
Push to Registry (Si Deploy = true)
    ↓
Deploy to Environment (Si Deploy = true)
    ↓
Health Check
    ↓
Reports
```

### Parámetros del Jenkinsfile

| Parámetro  | Tipo      | Descripción                                 |
| ---------- | --------- | ------------------------------------------- |
| SERVICE    | Selección | Servicio(s) a construir (all, o específico) |
| DEPLOY_ENV | Selección | Ambiente destino (dev, staging, production) |
| RUN_TESTS  | Boolean   | Ejecutar pruebas unitarias                  |
| RUN_SONAR  | Boolean   | Ejecutar análisis SonarQube                 |
| DEPLOY     | Boolean   | Desplegar a ambiente destino                |

## 📦 Scripts de Build

### build-and-test.sh (Linux/Mac)

```bash
# Sintaxis
./build-and-test.sh [command] [service]

# Comandos disponibles
./build-and-test.sh build property-service     # Build solo
./build-and-test.sh test all                   # Test todos
./build-and-test.sh analyze property-service   # SonarQube
./build-and-test.sh docker property-service    # Docker build
./build-and-test.sh full                       # Build + Test + Docker
```

### build-and-test.ps1 (Windows PowerShell)

```powershell
# Sintaxis
.\build-and-test.ps1 -Command <cmd> -Service <svc> [-SkipTests]

# Ejemplos
.\build-and-test.ps1 -Command build -Service property-service
.\build-and-test.ps1 -Command test -Service all
.\build-and-test.ps1 -Command full -SkipTests
.\build-and-test.ps1 -Command docker
```

## 🚀 Ejecución Local

### Docker Compose para desarrollo

```bash
# Iniciar todos los servicios en desarrollo
docker-compose -f docker-compose.yml --profile dev up -d

# Servicios disponibles:
# - mongo-dev (MongoDB)
# - minio (Object Storage)
# - redis (Caché)
# - property-service (puerto 8081)
# - api-gateway (puerto 8080)
# - service-registry (puerto 8761)

# Ver logs
docker-compose logs -f property-service

# Detener servicios
docker-compose down
```

### Flujo Local Completo

```bash
# 1. Build
./build-and-test.ps1 -Command build -Service property-service

# 2. Tests
./build-and-test.ps1 -Command test -Service property-service

# 3. Docker Build
./build-and-test.ps1 -Command docker -Service property-service

# 4. Iniciar servicios
docker-compose -f docker-compose.yml --profile dev up -d

# 5. Verificar health
curl http://localhost:8081/actuator/health
```

## 🔗 Integración con Jenkins

### Prerrequisitos

1. **Jenkins instalado y configurado**
2. **Plugins requeridos**:
   - Pipeline
   - Docker
   - SonarQube
   - Git

3. **Credenciales configuradas en Jenkins**:
   - `docker-registry-credentials` - Docker Registry
   - `sonarqube-url` - URL de SonarQube
   - `sonarqube-token` - Token de acceso SonarQube
   - `dev-docker-host` - Host Docker para desarrollo
   - `staging-docker-host` - Host Docker para staging
   - `prod-docker-host` - Host Docker para producción

### Crear Pipeline en Jenkins

1. **Nueva tarea > Pipeline**
2. **Pipeline script from SCM**
3. **Seleccionar Git**
4. **URL del repositorio**: `https://github.com/OsquiMenacho28/Group-C_Droca-Inmob_Backend.git`
5. **Branch**: `Sprint_5_DEV`
6. **Script path**: `Jenkinsfile`

### Ejecutar Pipeline

```bash
# Desde Jenkins UI:
# 1. Ir a la tarea del pipeline
# 2. Click en "Build with Parameters"
# 3. Seleccionar:
#    - SERVICE: property-service
#    - DEPLOY_ENV: dev
#    - RUN_TESTS: true
#    - RUN_SONAR: true
#    - DEPLOY: false (para el primer test)
# 4. Click en "Build"
```

### Ejemplo de ejecución completa

```bash
# Parámetros para Deploy a Desarrollo:
SERVICE=property-service
DEPLOY_ENV=dev
RUN_TESTS=true
RUN_SONAR=true
DEPLOY=true
```

## 📊 Reportes

### Tests

- **Ubicación**: `target/surefire-reports/`
- **Formato**: XML y HTML
- **Jenkins**: Archivado automáticamente

### Cobertura

- **Ubicación**: `target/site/jacoco/`
- **Formato**: HTML
- **Comando**: `mvn jacoco:report`

### SonarQube

- **URL**: Configurada en credenciales
- **Proyecto**: Nombre del servicio
- **Reporte**: Dashboard de SonarQube

## 🐛 Troubleshooting

### Error: "MongoDB connection refused"

```bash
# Iniciar MongoDB
docker-compose -f docker-compose.yml --profile dev up -d mongo-dev

# Verificar
docker ps | grep mongo-dev
```

### Error: "Port already in use"

```bash
# Listar puertos en uso
netstat -ano | findstr :8080  # Windows
lsof -i :8080                 # Linux/Mac

# Cambiar puertos en docker-compose.yml
```

### Tests fallan en CI/CD pero pasan localmente

1. Verificar perfiles de Spring activos
2. Revisar variables de entorno
3. Verificar configuración en `application-test.yml`
4. Ejecutar con mismo comando que Jenkins: `mvn clean test`

### Docker build falla

```bash
# Verificar Dockerfile
docker build -t test . --no-cache

# Ver logs detallados
docker build -t test . --progress=plain
```

## 📝 Ejemplos de Uso Común

### Build y Deploy a Desarrollo

```bash
# Opción 1: Script PowerShell
.\build-and-test.ps1 -Command full -Service property-service

# Opción 2: Jenkins
# SERVICE=property-service, DEPLOY_ENV=dev, DEPLOY=true
```

### Solo Tests (CI)

```bash
# PowerShell
.\build-and-test.ps1 -Command test -Service all

# Bash
./build-and-test.sh test all
```

### Build para Producción

```bash
# PowerShell (sin tests para velocidad)
.\build-and-test.ps1 -Command build -Service all -SkipTests

# Jenkins con parámetros
# DEPLOY_ENV=production, RUN_TESTS=true, DEPLOY=true
```

## 🔐 Seguridad

- Las credenciales deben estar configuradas en Jenkins
- No commitear secretos en el repositorio
- Usar credenciales de Jenkins para acceso a servicios
- Ejecutar builds con permisos limitados

## 📞 Soporte

Para problemas o preguntas:

1. Revisar los logs del pipeline
2. Verificar la configuración de Jenkins
3. Validar credenciales
4. Consultar documentación de servicios específicos
