# Authentication Context Implementation Plan

This plan details the implementation of the authentication context for the CJP Backend API, including Registration, Login (JWT), Password Recovery, and Profile Editing.

## User Decisions

- **Database**: Use `mysql-connector-j` instead of `mssql-jdbc`.
- **Profile Photo Upload**: Not implemented; uploaded files are not stored by the backend.
- **Email Service**: Use Resend as the SMTP provider.
- **Configuration**: Use `application.yml` for configuration.

## Proposed Changes

### Configuration and Dependencies

- Modify `pom.xml` to add:
  - `mysql-connector-j` (and remove `mssql-jdbc`)
  - `spring-boot-starter-validation`
  - `spring-boot-starter-mail`
  - `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (for JWT generation and validation)
- Create/update `src/main/resources/application.yml` with database, mail, and JWT configurations relying on environment variables.

### Domain Layer (Entities)

#### [NEW] `AuthenticationContext/domain/Role.java`
- Enum defining `PATIENT`, `DOCTOR`, `ADMINISTRATOR`.

#### [NEW] `AuthenticationContext/domain/User.java`
- Base entity representing a user (maps to `users` table).
- Fields: `id`, `firstName`, `lastName`, `dni` (Integer, unique), `email` (unique), `phoneNumber`, `password` (hashed), `profilePhoto`, `role`.

#### [NEW] `AuthenticationContext/domain/DoctorProfile.java`
- Entity for doctor-specific fields (maps to `doctor_profiles` table).
- One-to-One relationship with `User`.
- Fields: `id`, `medicalSpecialty`, `cmp`, `user`.

#### [NEW] `AuthenticationContext/domain/PasswordResetToken.java`
- Entity mapping to `password_reset_tokens`.
- Fields: `id`, `token`, `expiryDate`, `user`.

---

### Repository Layer

#### [NEW] `AuthenticationContext/repository/UserRepository.java`
- Spring Data JPA repository for `User`. Includes find by email, find by dni, and exists checks.

#### [NEW] `AuthenticationContext/repository/DoctorProfileRepository.java`
- Repository for `DoctorProfile`.

#### [NEW] `AuthenticationContext/repository/PasswordResetTokenRepository.java`
- Repository for `PasswordResetToken`.

---

### Security Layer

#### [NEW] `AuthenticationContext/security/JwtService.java`
- Handles generating JWTs (8-hour expiry), extracting claims (identity, role), and validating tokens.

#### [NEW] `AuthenticationContext/security/JwtAuthenticationFilter.java`
- Intercepts requests to validate the JWT in the `Authorization` header and sets the Spring Security Context.

#### [NEW] `AuthenticationContext/security/SecurityConfig.java`
- Configures `SecurityFilterChain`.
- Secures endpoints (e.g., `/api/auth/**` is public, `/api/users/profile` requires authentication, doctor-specific endpoints require `DOCTOR` role).
- Configures `BCryptPasswordEncoder`.

#### [NEW] `AuthenticationContext/security/UserDetailsServiceImpl.java`
- Implements `UserDetailsService` to load user data for Spring Security.

---

### Service Layer

#### [NEW] `AuthenticationContext/service/AuthService.java`
- **Registration**: Validates input (uniqueness of DNI/Email), hashes password, creates `User` with `PATIENT` role.
- **Login**: Authenticates using Spring `AuthenticationManager`, generates and returns JWT.
- **Password Recovery**: Generates and saves token, calls `EmailService`, validates token on reset, updates password, deletes token.

#### [NEW] `AuthenticationContext/service/EmailService.java`
- Uses `JavaMailSender` to send the password reset email via Resend SMTP. Includes the configurable reset link.

#### [NEW] `AuthenticationContext/service/UserService.java`
- **Edit Profile**: Updates `phone_number` and `email`.
- Updates `medical_specialty` and `cmp` if the authenticated user has the `DOCTOR` role.

---

### Presentation Layer (Controllers & DTOs)

#### [NEW] `AuthenticationContext/dto/...`
- DTOs for requests: `RegisterRequest`, `LoginRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`, `EditProfileRequest`.
- DTOs for responses: `AuthResponse`, `MessageResponse`, `UserProfileResponse`.
- Proper Jakarta Validation annotations (`@NotBlank`, `@Email`, `@Pattern`).

#### [NEW] `AuthenticationContext/controller/AuthController.java`
- Endpoints: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`.

#### [NEW] `AuthenticationContext/controller/UserController.java`
- Endpoints: `PUT /api/users/profile` (secured, using `@AuthenticationPrincipal`).

## Verification Plan

### Automated/Manual Tests
- Start the application with an empty database (MySQL).
- Use `curl` or Postman to:
  1. Register a new patient.
  2. Attempt to register with the same email/DNI (expect error).
  3. Login with invalid credentials (expect error).
  4. Login with valid credentials and receive a JWT.
  5. Use the JWT to access the edit profile endpoint and update basic fields.
  6. Attempt to edit doctor-specific fields as a patient (expect them to be ignored or rejected).
  7. Initiate the password recovery flow (observe server logs for token and email logic).
  8. Reset password using the token, and log in with the new password.
