# Javier Prado Clinic Backend

Backend de la Clinica Javier Prado construido con Java 21 y Spring Boot. En su estado actual se enfoca en autenticacion con JWT, registro de usuarios, recuperacion de contrasena, consulta y edicion de perfil y health check.

## Estado actual

- Registro de pacientes con rol `PATIENT` por defecto
- Inicio de sesion con JWT
- Recuperacion y reseteo de contrasena por correo
- Consulta de perfil autenticado
- Actualizacion de datos de perfil
- Health check para monitoreo basico
- Integracion parcial con el frontend actual

El modulo de citas todavia no esta implementado en la capa API, pero ya existen entidades y repositorios JPA para `appointments` y `clinics`.

## Stack

- Java 21
- Spring Boot 4.0.6
- Spring Security
- Spring Data JPA
- Spring Validation
- Java Mail
- JWT (`jjwt`)
- PostgreSQL
- Maven
- Docker

## Estructura del proyecto

```text
src/main/java/com/clinica_javierprado/cjp_backend/
  controller/
  domain/
  dto/
  exception/
  repository/
  security/
  service/
src/main/resources/
  application.yml
src/test/java/com/clinica_javierprado/cjp_backend/
```

## Modulos principales

### `controller`

Expone los endpoints REST actualmente disponibles:

- `AuthController`
- `UserController`
- `HealthController`

### `service`

Contiene la logica de negocio principal:

- `AuthService`
- `UserService`
- `EmailService`

### `security`

Configura autenticacion stateless con JWT, filtro de autenticacion y CORS.

### `domain`

Modelos persistidos principales:

- `User`
- `DoctorProfile`
- `PasswordResetToken`
- `Role`

## Endpoints implementados

### Autenticacion

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/forgot-password
POST /api/auth/reset-password
```

### Usuario autenticado

```http
GET /api/users/me
PUT /api/users/profile
```

### Salud del servicio

```http
GET /api/health
```

## Detalle funcional

### Registro

- Endpoint: `POST /api/auth/register`
- Crea usuarios con rol `PATIENT`
- Valida email, DNI y telefono duplicados
- Devuelve un JWT en la respuesta

### Login

- Endpoint: `POST /api/auth/login`
- Autentica con email y contrasena
- Devuelve un JWT

### Recuperacion de contrasena

- `POST /api/auth/forgot-password` genera un token temporal y envia un correo
- `POST /api/auth/reset-password` aplica la nueva contrasena si el token sigue vigente

### Perfil de usuario

- `GET /api/users/me` devuelve el perfil autenticado
- `PUT /api/users/profile` actualiza perfil y admite `multipart/form-data`
- El campo opcional `photo` se acepta por compatibilidad, pero actualmente se ignora.
- El `PUT` espera:

```text
data: EditProfileRequest
photo: MultipartFile opcional, no procesado actualmente
```

No existe un endpoint separado `POST /api/users/profile-photo`.

## Flujo de autenticacion

1. El cliente llama `POST /api/auth/login` o `POST /api/auth/register`.
2. El backend responde con un JWT.
3. El cliente envia `Authorization: Bearer <token>` en endpoints protegidos.
4. `JwtAuthenticationFilter` valida el token antes de resolver la peticion.

## Variables de entorno

Variables usadas realmente por el codigo:

### Base de datos

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_MAX_POOL_SIZE`
- `DB_MIN_IDLE`
- `JPA_DDL_AUTO`
- `JPA_SHOW_SQL`
- `HIBERNATE_FORMAT_SQL`

### JWT

- `JWT_SECRET`
- `JWT_EXPIRATION_MS`

### Correo

- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `MAIL_RESET_SUBJECT`

### CORS

- `CORS_ALLOWED_ORIGINS`
- `CORS_ALLOWED_METHODS`
- `CORS_ALLOWED_HEADERS`
- `CORS_ALLOW_CREDENTIALS`

### Recuperacion de contrasena

- `FRONTEND_BASE_URL`
- `PASSWORD_RESET_PATH`
- `PASSWORD_RESET_TOKEN_EXPIRATION_HOURS`

### Docker Compose

- `BACKEND_PORT`
- `SPRING_PROFILES_ACTIVE`

Referencia: `.env.example`

## Configuracion actual relevante

- `application.yml` exige variables sensibles para base de datos, JWT y mail
- `application-docker.yml` activa la siembra de doctores base para el perfil `docker`
- `spring.jpa.hibernate.ddl-auto` se controla con `JPA_DDL_AUTO` y por defecto esta en `update`
- `spring.jpa.show-sql` se controla con `JPA_SHOW_SQL` y por defecto esta en `false`
- `hibernate.format_sql` se controla con `HIBERNATE_FORMAT_SQL` y por defecto esta en `true`
- El pool JDBC se controla con `DB_MAX_POOL_SIZE` y `DB_MIN_IDLE`, pensado para Supabase
- CORS se controla con `CORS_ALLOWED_ORIGINS`, `CORS_ALLOWED_METHODS`, `CORS_ALLOWED_HEADERS` y `CORS_ALLOW_CREDENTIALS`
- El link de recuperacion de contrasena se construye con `FRONTEND_BASE_URL` y `PASSWORD_RESET_PATH`

Spring importa automaticamente `.env` si existe. `.env.example` queda solo como plantilla.

Para entornos reales conviene definir variables de entorno propias y no depender de los fallbacks definidos en `application.yml`.

La ruta soportada para ejecutar en Docker es `docker compose`, no `docker run` del backend por separado.

## Ejecucion local

### Requisitos

- Java 21
- Maven o Maven Wrapper
- PostgreSQL accesible

### Preparacion

1. Crea `.env` usando `.env.example` como plantilla.
2. Configura una instancia PostgreSQL compatible con `DB_URL`; Supabase es la opcion principal esperada.
3. Ajusta credenciales JWT y correo segun tu entorno.

### Ejecutar con Maven Wrapper

```bash
./mvnw spring-boot:run
```

### Ejecutar con Maven instalado

```bash
mvn spring-boot:run
```

La aplicacion queda disponible por defecto en `http://localhost:8080`.

## Docker

### Construir imagen

```bash
docker build -t cjp-backend .
```

### Ejecutar con Docker Compose

```bash
docker compose up --build
```

Comportamiento actual de Docker Compose con Supabase:

- Levanta `backend` apuntando a la base configurada en `DB_URL`
- Carga las variables desde `.env` mediante `env_file`
- Activa el perfil configurado en `SPRING_PROFILES_ACTIVE`, por defecto `docker`
- Al arrancar, Spring vuelve a sembrar doctores base definidos en `application-docker.yml`
- Si un doctor semilla ya existe por `cmp`, o hay conflicto de `email` o `dni`, el backend lo omite y sigue arrancando
- El servicio PostgreSQL local `db` es opcional y solo se activa con el perfil Docker Compose `local-db`

Para usar PostgreSQL local en vez de Supabase, configura:

```env
DB_URL=jdbc:postgresql://db:5432/cjp_db
DB_USERNAME=admin
DB_PASSWORD=password
```

Y ejecuta:

```bash
docker compose --profile local-db up --build
```

La base local de Docker Compose no monta volumen, asi que se considera efimera. Si recreas el contenedor de PostgreSQL, desaparecen los usuarios registrados en runtime.

Credenciales semilla disponibles en el perfil `docker`:

- `ricardo.salazar@cjp.local` / `doctor123`
- `lucia.torres@cjp.local` / `doctor123`

Si quieres cambiar credenciales o doctores precargados, edita `src/main/resources/application-docker.yml`.

Si ejecutas solo la imagen del backend, debes pasar manualmente las variables `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` apuntando a una base PostgreSQL accesible.

## Integracion con el frontend actual

El frontend en `../JavierPradoClinic-frontend` hoy consume o espera principalmente estos endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me`

Estado de la integracion:

- Login y registro estan conectados
- El dashboard del frontend se apoya en `GET /api/users/me`
- El modulo visual de citas del frontend sigue mayormente mock y no tiene soporte backend aun
- La recuperacion de contrasena existe en backend, pero el frontend actual todavia no expone un flujo completo para usarla

## Limitaciones conocidas

- No hay endpoints implementados para citas, medicos o sedes
- No hay integracion OAuth2 implementada
- La cobertura de tests es minima
- La configuracion sensible debe estar definida en `.env` o variables del entorno antes de arrancar la app

## Archivos utiles

- `src/main/resources/application.yml`
- `src/main/resources/application-docker.yml`
- `.env.example`
- `docker-compose.yml`
- `Dockerfile`
- `cjp_db.sql` contiene SQL legado de MySQL y no se usa en el flujo Docker actual; las entidades JPA son la fuente para crear tablas con Hibernate

## Siguientes pasos recomendados

- Implementar endpoints reales de citas
- Exponer catalogos de medicos, especialidades y sedes
- Agregar pruebas de autenticacion, perfil y errores
- Alinear el flujo de recuperacion de contrasena con el frontend
