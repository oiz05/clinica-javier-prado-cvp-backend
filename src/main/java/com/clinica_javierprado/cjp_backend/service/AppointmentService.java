package com.clinica_javierprado.cjp_backend.service;

import com.clinica_javierprado.cjp_backend.domain.Appointment;
import com.clinica_javierprado.cjp_backend.domain.AppointmentPrice;
import com.clinica_javierprado.cjp_backend.domain.AppointmentStatus;
import com.clinica_javierprado.cjp_backend.domain.AppointmentType;
import com.clinica_javierprado.cjp_backend.domain.Clinic;
import com.clinica_javierprado.cjp_backend.domain.DoctorProfile;
import com.clinica_javierprado.cjp_backend.domain.DoctorSchedule;
import com.clinica_javierprado.cjp_backend.domain.Role;
import com.clinica_javierprado.cjp_backend.domain.User;
import com.clinica_javierprado.cjp_backend.dto.AppointmentQuoteResponse;
import com.clinica_javierprado.cjp_backend.dto.AppointmentResponse;
import com.clinica_javierprado.cjp_backend.dto.AvailabilitySlotResponse;
import com.clinica_javierprado.cjp_backend.dto.CreateAppointmentRequest;
import com.clinica_javierprado.cjp_backend.dto.RescheduleAppointmentRequest;
import com.clinica_javierprado.cjp_backend.event.AppointmentNotificationEvent;
import com.clinica_javierprado.cjp_backend.event.AppointmentNotificationEvent.NotificationType;
import com.clinica_javierprado.cjp_backend.repository.AppointmentRepository;
import com.clinica_javierprado.cjp_backend.repository.AppointmentTypeRepository;
import com.clinica_javierprado.cjp_backend.repository.ClinicRepository;
import com.clinica_javierprado.cjp_backend.repository.DoctorClinicRepository;
import com.clinica_javierprado.cjp_backend.repository.DoctorProfileRepository;
import com.clinica_javierprado.cjp_backend.repository.DoctorScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final int APPOINTMENT_BUFFER_MINUTES = 10;

    private final AppointmentRepository appointmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ClinicRepository clinicRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final DoctorClinicRepository doctorClinicRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final PricingService pricingService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments(User user) {
        requirePatient(user);
        return appointmentRepository.findByPatientIdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                        user.getId(),
                        LocalDateTime.now()
                ).stream()
                .map(this::toAppointmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentQuoteResponse quote(Long doctorProfileId, Long appointmentTypeId, LocalDate appointmentDate) {
        DoctorProfile doctorProfile = getDoctor(doctorProfileId);
        AppointmentType appointmentType = getAppointmentType(appointmentTypeId);
        return pricingService.buildQuote(doctorProfile, appointmentType, appointmentDate);
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getAvailability(Long doctorProfileId, Long clinicId, Long appointmentTypeId, LocalDate date) {
        DoctorProfile doctorProfile = getDoctor(doctorProfileId);
        getClinic(clinicId);
        AppointmentType appointmentType = getAppointmentType(appointmentTypeId);
        validateDoctorWorksAtClinic(doctorProfileId, clinicId);

        AppointmentPrice price = resolvePriceOrNull(doctorProfile, appointmentType, date);
        List<DoctorSchedule> schedules = doctorScheduleRepository.findByDoctorProfileIdAndClinicIdAndDayOfWeekAndActiveTrue(
                doctorProfileId,
                clinicId,
                date.getDayOfWeek().getValue()
        );

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay().minusNanos(1);
        List<Appointment> appointments = appointmentRepository.findByDoctorProfileIdAndClinicIdAndAppointmentDateBetween(
                doctorProfileId,
                clinicId,
                dayStart,
                dayEnd
        );

        return schedules.stream()
                .sorted(Comparator.comparing(DoctorSchedule::getStartTime))
                .flatMap(schedule -> buildSlots(date, schedule, appointmentType, appointments, price).stream())
                .toList();
    }

    @Transactional
    public AppointmentResponse createAppointment(User user, CreateAppointmentRequest request) {
        requirePatient(user);
        DoctorProfile doctorProfile = getDoctor(request.getDoctorProfileId());
        Clinic clinic = getClinic(request.getClinicId());
        AppointmentType appointmentType = getAppointmentType(request.getAppointmentTypeId());
        validateDoctorWorksAtClinic(doctorProfile.getId(), clinic.getId());
        validateSlotAvailable(doctorProfile.getId(), clinic.getId(), appointmentType, request.getAppointmentDate(), null);

        AppointmentPrice price = pricingService.resolvePrice(doctorProfile, appointmentType, request.getAppointmentDate().toLocalDate());
        Appointment appointment = Appointment.builder()
                .patient(user)
                .doctorProfile(doctorProfile)
                .clinic(clinic)
                .appointmentType(appointmentType)
                .appointmentDate(request.getAppointmentDate())
                .status(AppointmentStatus.PENDING)
                .price(price.getPrice())
                .build();

        Appointment savedAppointment = appointmentRepository.save(appointment);
        publishAppointmentNotification(savedAppointment, NotificationType.CREATED, null, null);
        return toAppointmentResponse(savedAppointment);
    }

    @Transactional
    public AppointmentResponse updateStatus(User user, Long appointmentId, AppointmentStatus status) {
        requirePatient(user);
        Appointment appointment = getPatientAppointment(user, appointmentId);
        if (status == AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Patients cannot mark appointments as completed.");
        }
        AppointmentStatus previousStatus = appointment.getStatus();
        appointment.setStatus(status);
        Appointment savedAppointment = appointmentRepository.save(appointment);
        if (previousStatus != status) {
            publishAppointmentNotification(savedAppointment, NotificationType.STATUS_CHANGED, previousStatus, null);
        }
        return toAppointmentResponse(savedAppointment);
    }

    @Transactional
    public AppointmentResponse reschedule(User user, Long appointmentId, RescheduleAppointmentRequest request) {
        requirePatient(user);
        Appointment appointment = getPatientAppointment(user, appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled appointments cannot be rescheduled.");
        }
        AppointmentStatus previousStatus = appointment.getStatus();
        LocalDateTime previousAppointmentDate = appointment.getAppointmentDate();
        validateSlotAvailable(
                appointment.getDoctorProfile().getId(),
                appointment.getClinic().getId(),
                appointment.getAppointmentType(),
                request.getAppointmentDate(),
                appointment.getId()
        );
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStatus(AppointmentStatus.PENDING);
        Appointment savedAppointment = appointmentRepository.save(appointment);
        publishAppointmentNotification(savedAppointment, NotificationType.RESCHEDULED, previousStatus, previousAppointmentDate);
        return toAppointmentResponse(savedAppointment);
    }

    private List<AvailabilitySlotResponse> buildSlots(
            LocalDate date,
            DoctorSchedule schedule,
            AppointmentType appointmentType,
            List<Appointment> appointments,
            AppointmentPrice price
    ) {
        int durationMinutes = appointmentType.getDurationMinutes();
        int slotStepMinutes = durationMinutes + APPOINTMENT_BUFFER_MINUTES;
        LocalTime current = schedule.getStartTime();
        LocalTime latestStart = schedule.getEndTime().minusMinutes(durationMinutes);
        List<AvailabilitySlotResponse> slots = new java.util.ArrayList<>();

        while (!current.isAfter(latestStart)) {
            LocalDateTime startAt = LocalDateTime.of(date, current);
            LocalDateTime endAt = startAt.plusMinutes(durationMinutes);
            boolean available = isSlotAvailable(startAt, endAt, appointments, null) && startAt.isAfter(LocalDateTime.now());
            slots.add(AvailabilitySlotResponse.builder()
                    .startAt(startAt)
                    .endAt(endAt)
                    .available(available)
                    .price(price != null ? price.getPrice() : null)
                    .currency(price != null ? price.getCurrency() : null)
                    .build());
            current = current.plusMinutes(slotStepMinutes);
        }

        return slots;
    }

    private void validateSlotAvailable(Long doctorProfileId, Long clinicId, AppointmentType appointmentType, LocalDateTime startAt, Long ignoredAppointmentId) {
        validateScheduleCoversSlot(doctorProfileId, clinicId, appointmentType, startAt);
        LocalDate date = startAt.toLocalDate();
        List<Appointment> appointments = appointmentRepository.findByDoctorProfileIdAndClinicIdAndAppointmentDateBetween(
                doctorProfileId,
                clinicId,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay().minusNanos(1)
        );
        LocalDateTime endAt = startAt.plusMinutes(appointmentType.getDurationMinutes());
        if (!isSlotAvailable(startAt, endAt, appointments, ignoredAppointmentId)) {
            throw new IllegalArgumentException("Selected time slot is no longer available or does not meet the 10-minute buffer.");
        }
    }

    private void validateScheduleCoversSlot(Long doctorProfileId, Long clinicId, AppointmentType appointmentType, LocalDateTime startAt) {
        if (!startAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Appointment date must be in the future.");
        }
        LocalTime endTime = startAt.toLocalTime().plusMinutes(appointmentType.getDurationMinutes());
        boolean covered = doctorScheduleRepository.findByDoctorProfileIdAndClinicIdAndDayOfWeekAndActiveTrue(
                        doctorProfileId,
                        clinicId,
                        startAt.getDayOfWeek().getValue()
                ).stream()
                .anyMatch(schedule -> isCoveredByScheduleStep(schedule, startAt.toLocalTime(), endTime, appointmentType.getDurationMinutes()));
        if (!covered) {
            throw new IllegalArgumentException("Doctor does not have an available schedule for this slot.");
        }
    }

    private boolean isCoveredByScheduleStep(DoctorSchedule schedule, LocalTime startTime, LocalTime endTime, int durationMinutes) {
        if (startTime.isBefore(schedule.getStartTime()) || endTime.isAfter(schedule.getEndTime())) {
            return false;
        }
        long minutesFromScheduleStart = Duration.between(schedule.getStartTime(), startTime).toMinutes();
        int slotStepMinutes = durationMinutes + APPOINTMENT_BUFFER_MINUTES;
        return minutesFromScheduleStart % slotStepMinutes == 0;
    }

    private boolean isSlotAvailable(LocalDateTime startAt, LocalDateTime endAt, List<Appointment> appointments, Long ignoredAppointmentId) {
        return appointments.stream()
                .filter(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED)
                .filter(appointment -> ignoredAppointmentId == null || !appointment.getId().equals(ignoredAppointmentId))
                .noneMatch(appointment -> {
                    LocalDateTime appointmentStart = appointment.getAppointmentDate();
                    LocalDateTime appointmentEnd = appointmentStart.plusMinutes(appointment.getAppointmentType().getDurationMinutes());
                    return startAt.isBefore(appointmentEnd.plusMinutes(APPOINTMENT_BUFFER_MINUTES))
                            && endAt.isAfter(appointmentStart.minusMinutes(APPOINTMENT_BUFFER_MINUTES));
                });
    }

    private AppointmentPrice resolvePriceOrNull(DoctorProfile doctorProfile, AppointmentType appointmentType, LocalDate date) {
        try {
            return pricingService.resolvePrice(doctorProfile, appointmentType, date);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Appointment getPatientAppointment(User user, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));
        if (!appointment.getPatient().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Appointment does not belong to the authenticated patient.");
        }
        return appointment;
    }

    private DoctorProfile getDoctor(Long doctorProfileId) {
        return doctorProfileRepository.findById(doctorProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found."));
    }

    private Clinic getClinic(Long clinicId) {
        return clinicRepository.findById(clinicId)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found."));
    }

    private AppointmentType getAppointmentType(Long appointmentTypeId) {
        return appointmentTypeRepository.findById(appointmentTypeId)
                .filter(AppointmentType::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Appointment type not found."));
    }

    private void validateDoctorWorksAtClinic(Long doctorProfileId, Long clinicId) {
        if (!doctorClinicRepository.existsByDoctorProfileIdAndClinicId(doctorProfileId, clinicId)) {
            throw new IllegalArgumentException("Doctor does not attend at the selected clinic.");
        }
    }

    private void requirePatient(User user) {
        if (user.getRole() != Role.PATIENT) {
            throw new IllegalArgumentException("Only patients can manage appointments.");
        }
    }

    private void publishAppointmentNotification(
            Appointment appointment,
            NotificationType type,
            AppointmentStatus previousStatus,
            LocalDateTime previousAppointmentDate
    ) {
        String doctorName = appointment.getDoctorProfile().getUser().getFirstName() + " " + appointment.getDoctorProfile().getUser().getLastName();
        eventPublisher.publishEvent(new AppointmentNotificationEvent(
                appointment.getId(),
                type,
                appointment.getPatient().getEmail(),
                appointment.getPatient().getFirstName(),
                previousStatus,
                appointment.getStatus(),
                previousAppointmentDate,
                appointment.getAppointmentDate(),
                doctorName,
                appointment.getDoctorProfile().getMedicalSpecialty(),
                appointment.getClinic().getName(),
                appointment.getClinic().getAddress(),
                appointment.getAppointmentType().getName()
        ));
    }

    private AppointmentResponse toAppointmentResponse(Appointment appointment) {
        AppointmentPrice price = pricingService.resolvePrice(
                appointment.getDoctorProfile(),
                appointment.getAppointmentType(),
                appointment.getAppointmentDate().toLocalDate()
        );
        String doctorName = appointment.getDoctorProfile().getUser().getFirstName() + " " + appointment.getDoctorProfile().getUser().getLastName();
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .doctorProfileId(appointment.getDoctorProfile().getId())
                .doctorName(doctorName)
                .medicalSpecialty(appointment.getDoctorProfile().getMedicalSpecialty())
                .clinicId(appointment.getClinic().getId())
                .clinicName(appointment.getClinic().getName())
                .clinicAddress(appointment.getClinic().getAddress())
                .appointmentTypeId(appointment.getAppointmentType().getId())
                .appointmentTypeName(appointment.getAppointmentType().getName())
                .durationMinutes(appointment.getAppointmentType().getDurationMinutes())
                .appointmentDate(appointment.getAppointmentDate())
                .status(appointment.getStatus().name())
                .price(appointment.getPrice())
                .currency(price.getCurrency())
                .build();
    }
}
