package com.clinica_javierprado.cjp_backend.service;

import com.clinica_javierprado.cjp_backend.domain.EmailVerificationToken;
import com.clinica_javierprado.cjp_backend.domain.PasswordResetToken;
import com.clinica_javierprado.cjp_backend.domain.Role;
import com.clinica_javierprado.cjp_backend.domain.User;
import com.clinica_javierprado.cjp_backend.dto.AuthResponse;
import com.clinica_javierprado.cjp_backend.dto.LoginRequest;
import com.clinica_javierprado.cjp_backend.dto.MessageResponse;
import com.clinica_javierprado.cjp_backend.dto.RegisterRequest;
import com.clinica_javierprado.cjp_backend.exception.EmailNotVerifiedException;
import com.clinica_javierprado.cjp_backend.repository.EmailVerificationTokenRepository;
import com.clinica_javierprado.cjp_backend.repository.PasswordResetTokenRepository;
import com.clinica_javierprado.cjp_backend.repository.UserRepository;
import com.clinica_javierprado.cjp_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.token-expiration-hours}")
    private long passwordResetTokenExpirationHours;

    @Value("${app.email-verification.code-expiration-minutes}")
    private long emailVerificationCodeExpirationMinutes;

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El email ya está en uso.");
        }
        if (userRepository.existsByDni(request.getDni())) {
            throw new IllegalArgumentException("El DNI ya está registrado.");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("El número de teléfono ya está registrado.");
        }
        if (userRepository.existsByFirstNameAndLastName(request.getFirstName(), request.getLastName())) {
            throw new IllegalArgumentException("Ya existe un usuario con los mismos nombres y apellidos.");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dni(request.getDni())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .emailVerified(false)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.PATIENT) // Default role for registration
                .build();

        userRepository.save(user);
        sendVerificationCode(user);

        return new MessageResponse("Registro exitoso. Te enviamos un codigo de verificacion a tu correo.");
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            System.out.println("Email not verified for user: " + user.getEmail());
            throw new EmailNotVerifiedException(user.getEmail());
        }

        String jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder().token(jwtToken).build();
    }

    @Transactional
    public AuthResponse verifyEmail(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("El correo ya fue verificado.");
        }

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Codigo de verificacion invalido."));

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            emailVerificationTokenRepository.delete(verificationToken);
            throw new IllegalArgumentException("El codigo de verificacion expiro. Solicita uno nuevo.");
        }

        if (!passwordEncoder.matches(code, verificationToken.getCodeHash())) {
            throw new IllegalArgumentException("Codigo de verificacion invalido.");
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        emailVerificationTokenRepository.delete(verificationToken);
        emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());

        String jwtToken = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(jwtToken)
                .message("Correo verificado correctamente.")
                .build();
    }

    @Transactional
    public void resendVerificationCode(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return;
            }
            sendVerificationCode(user);
        });
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        tokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiryDate(LocalDateTime.now().plusHours(passwordResetTokenExpirationHours))
                .build();

        tokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        tokenRepository.delete(resetToken);
    }

    private void sendVerificationCode(User user) {
        emailVerificationTokenRepository.deleteByUserId(user.getId());
        emailVerificationTokenRepository.flush();

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .expiryDate(LocalDateTime.now().plusMinutes(emailVerificationCodeExpirationMinutes))
                .createdAt(LocalDateTime.now())
                .build();

        emailVerificationTokenRepository.save(verificationToken);
        emailService.sendVerificationCodeEmail(
                user.getEmail(),
                user.getFirstName(),
                code,
                emailVerificationCodeExpirationMinutes
        );
    }
}
