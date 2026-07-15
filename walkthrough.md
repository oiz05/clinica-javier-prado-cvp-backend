# Authentication Context Walkthrough

The Authentication Context implementation is complete! The system uses database-backed users, JWT authentication, and Resend for email notifications.

## Changes Made

### 1. Dependencies and Configuration
- **Database**: Replaced `mssql-jdbc` with `mysql-connector-j` in `pom.xml`.
- **Other Dependencies**: Added `spring-boot-starter-validation`, `spring-boot-starter-mail`, and JJWT dependencies for authentication.
- **Environment**: Replaced `application.properties` with `application.yml` mapped to environment variables. Also created an `.env.example` file for reference.

### 2. Domain Entities & Repositories
- Created the entities representing the domain: `Role` (Enum), `User`, `DoctorProfile`, and `PasswordResetToken`.
- Created Spring Data JPA repositories: `UserRepository`, `DoctorProfileRepository`, `PasswordResetTokenRepository`.

### 3. Security Configuration
- Configured Spring Security to use stateless JWT-based authentication (`SecurityConfig.java`).
- Implemented `JwtService` to handle token generation and parsing.
- Built a `JwtAuthenticationFilter` to validate tokens on incoming requests.

### 4. Service Layer
- **`AuthService`**: Handles user registration, authentication (login), and password recovery logic (forgot and reset password).
- **`UserService`**: Handles profile updating, which also encapsulates role-based logic (saving medical specialties and CMP for doctors).
- **`EmailService`**: Sends reset password links via Resend's SMTP server using Spring's `JavaMailSender`.

### 5. Controllers & DTOs
- Designed validation-enforced DTOs (e.g., `RegisterRequest`, `LoginRequest`, `EditProfileRequest`).
- **`AuthController`**: Exposes the `/api/auth/register`, `/api/auth/login`, `/api/auth/forgot-password`, and `/api/auth/reset-password` endpoints.
- **`UserController`**: Exposes the `/api/users/profile` endpoints, retrieving user details through the `@AuthenticationPrincipal`.

## Verification 

The application successfully compiled and built without issues.

To start testing the application:
1. Ensure your MySQL server is running.
2. Fill in the environment variables defined in `.env.example` (or set them in your local environment/IDE).
3. Start the application. The tables will be auto-generated due to `spring.jpa.hibernate.ddl-auto: update`.

You can then hit the `/api/auth/register` endpoint with tools like Postman to create your first user.
