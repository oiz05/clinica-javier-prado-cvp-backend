package com.clinica_javierprado.cjp_backend.service;

import com.clinica_javierprado.cjp_backend.domain.DoctorProfile;
import com.clinica_javierprado.cjp_backend.domain.Role;
import com.clinica_javierprado.cjp_backend.domain.User;
import com.clinica_javierprado.cjp_backend.dto.EditProfileRequest;
import com.clinica_javierprado.cjp_backend.dto.UserProfileResponse;
import com.clinica_javierprado.cjp_backend.repository.DoctorProfileRepository;
import com.clinica_javierprado.cjp_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    @Transactional
    public UserProfileResponse updateProfile(User authenticatedUser, EditProfileRequest request) {
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email is already in use by another account.");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        userRepository.save(user);

        DoctorProfile doctorProfile = null;
        if (user.getRole() == Role.DOCTOR) {
            doctorProfile = doctorProfileRepository.findByUserId(user.getId()).orElse(new DoctorProfile());
            doctorProfile.setUser(user);
            
            if (request.getMedicalSpecialty() != null) {
                doctorProfile.setMedicalSpecialty(request.getMedicalSpecialty());
            }
            if (request.getCmp() != null) {
                doctorProfile.setCmp(request.getCmp());
            }
            doctorProfileRepository.save(doctorProfile);
        }

        return buildProfileResponse(user, doctorProfile);
    }

    public UserProfileResponse getProfile(User authenticatedUser) {
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        DoctorProfile doctorProfile = null;
        if (user.getRole() == Role.DOCTOR) {
            doctorProfile = doctorProfileRepository.findByUserId(user.getId()).orElse(null);
        }
        return buildProfileResponse(user, doctorProfile);
    }

    private UserProfileResponse buildProfileResponse(User user, DoctorProfile doctorProfile) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dni(user.getDni())
                .email(user.getEmail())
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .phoneNumber(user.getPhoneNumber())
                .profilePhoto(user.getProfilePhoto())
                .role(user.getRole().name())
                .medicalSpecialty(doctorProfile != null ? doctorProfile.getMedicalSpecialty() : null)
                .cmp(doctorProfile != null ? doctorProfile.getCmp() : null)
                .build();
    }
}
