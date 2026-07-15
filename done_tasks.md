- [x] **Dependencies & Configuration**
  - [x] Add `mysql-connector-j`, `spring-boot-starter-validation`, `spring-boot-starter-mail`, and `jjwt` dependencies in `pom.xml`
  - [x] Remove `mssql-jdbc` from `pom.xml`
  - [x] Create `.env.example` and setup `application.yml` for database, JWT, and Resend properties

- [x] **Domain Entities**
  - [x] Create `Role` enum (`PATIENT`, `DOCTOR`, `ADMINISTRATOR`)
  - [x] Create `User` entity (`id`, `firstName`, `lastName`, `dni`, `email`, `phoneNumber`, `password`, `profilePhoto`, `role`)
  - [x] Create `DoctorProfile` entity (`id`, `medicalSpecialty`, `cmp`, `user`)
  - [x] Create `PasswordResetToken` entity (`id`, `token`, `expiryDate`, `user`)

- [x] **Repositories**
  - [x] Create `UserRepository` with methods `findByEmail`, `findByDni`, `existsByEmail`, `existsByDni`
  - [x] Create `DoctorProfileRepository`
  - [x] Create `PasswordResetTokenRepository`

- [x] **Security Layer**
  - [x] Create `JwtService` (generate, extract, validate tokens)
  - [x] Create `JwtAuthenticationFilter` (process `Authorization` header)
  - [x] Create `UserDetailsServiceImpl` (load user for Spring Security)
  - [x] Create `SecurityConfig` (SecurityFilterChain, BCrypt, endpoints access)

- [x] **Service Layer**
  - [x] Create `EmailService` (Resend SMTP for password recovery links)
  - [x] Create `AuthService` (Register, Login, ForgotPassword, ResetPassword)
  - [x] Create `UserService` (EditProfile handling unique email logic)

- [x] **Controllers & DTOs**
  - [x] Create Auth DTOs (`RegisterRequest`, `LoginRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`, `AuthResponse`)
  - [x] Create User DTOs (`EditProfileRequest`, `UserProfileResponse`, `MessageResponse`)
  - [x] Create `AuthController` (`/api/auth/register`, `/api/auth/login`, `/api/auth/forgot-password`, `/api/auth/reset-password`)
  - [x] Create `UserController` (`/api/users/profile`)

- [x] **Verification**
  - [x] Run application and verify all endpoints work correctly (database, JWT, Resend)
