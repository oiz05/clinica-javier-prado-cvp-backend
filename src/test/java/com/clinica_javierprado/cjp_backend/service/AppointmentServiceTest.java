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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentServiceTest {

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final DoctorProfileRepository doctorProfileRepository = mock(DoctorProfileRepository.class);
    private final ClinicRepository clinicRepository = mock(ClinicRepository.class);
    private final AppointmentTypeRepository appointmentTypeRepository = mock(AppointmentTypeRepository.class);
    private final DoctorClinicRepository doctorClinicRepository = mock(DoctorClinicRepository.class);
    private final DoctorScheduleRepository doctorScheduleRepository = mock(DoctorScheduleRepository.class);
    private final PricingService pricingService = mock(PricingService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final AppointmentService appointmentService = new AppointmentService(
            appointmentRepository,
            doctorProfileRepository,
            clinicRepository,
            appointmentTypeRepository,
            doctorClinicRepository,
            doctorScheduleRepository,
            pricingService,
            eventPublisher
    );

    @Test
    void getAvailabilityUsesDurationPlusBufferStepAndKeepsPriceOptional() {
        LocalDate date = LocalDate.now().plusDays(7);
        DoctorProfile doctor = DoctorProfile.builder()
                .id(1L)
                .medicalSpecialty("Cardiologia")
                .user(User.builder().firstName("Ana").lastName("Paz").build())
                .build();
        Clinic clinic = Clinic.builder().id(2L).name("Sede").address("Av. Test").build();
        AppointmentType appointmentType = AppointmentType.builder()
                .id(3L)
                .name("Consulta presencial")
                .description("Consulta medica")
                .durationMinutes(30)
                .active(true)
                .build();
        DoctorSchedule schedule = DoctorSchedule.builder()
                .doctorProfile(doctor)
                .clinic(clinic)
                .dayOfWeek(date.getDayOfWeek().getValue())
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .active(true)
                .build();
        Appointment existingAppointment = Appointment.builder()
                .id(10L)
                .doctorProfile(doctor)
                .clinic(clinic)
                .appointmentType(appointmentType)
                .appointmentDate(date.atTime(9, 0))
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(doctorProfileRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(clinicRepository.findById(2L)).thenReturn(Optional.of(clinic));
        when(appointmentTypeRepository.findById(3L)).thenReturn(Optional.of(appointmentType));
        when(doctorClinicRepository.existsByDoctorProfileIdAndClinicId(1L, 2L)).thenReturn(true);
        when(pricingService.resolvePrice(doctor, appointmentType, date))
                .thenThrow(new IllegalArgumentException("No price configured"));
        when(doctorScheduleRepository.findByDoctorProfileIdAndClinicIdAndDayOfWeekAndActiveTrue(
                1L,
                2L,
                date.getDayOfWeek().getValue()
        )).thenReturn(List.of(schedule));
        when(appointmentRepository.findByDoctorProfileIdAndClinicIdAndAppointmentDateBetween(
                1L,
                2L,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay().minusNanos(1)
        )).thenReturn(List.of(existingAppointment));

        List<AvailabilitySlotResponse> slots = appointmentService.getAvailability(1L, 2L, 3L, date);

        assertThat(slots).extracting(slot -> slot.getStartAt().toLocalTime())
                .containsExactly(
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 40),
                        LocalTime.of(9, 20),
                        LocalTime.of(10, 0),
                        LocalTime.of(10, 40),
                        LocalTime.of(11, 20)
                );
        assertThat(slots).filteredOn(AvailabilitySlotResponse::isAvailable)
                .extracting(slot -> slot.getStartAt().toLocalTime())
                .containsExactly(
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        LocalTime.of(10, 40),
                        LocalTime.of(11, 20)
                );
        assertThat(slots).allSatisfy(slot -> {
            assertThat(slot.getPrice()).isNull();
            assertThat(slot.getCurrency()).isNull();
        });
    }

    @Test
    void createAppointmentPublishesNotificationAfterSaving() {
        User patient = patient();
        DoctorProfile doctor = doctor();
        Clinic clinic = clinic();
        AppointmentType appointmentType = appointmentType();
        LocalDateTime appointmentDate = LocalDate.now().plusDays(8).atTime(8, 0);
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setDoctorProfileId(doctor.getId());
        request.setClinicId(clinic.getId());
        request.setAppointmentTypeId(appointmentType.getId());
        request.setAppointmentDate(appointmentDate);

        stubAvailableSlot(doctor, clinic, appointmentType, appointmentDate);
        stubPrice(doctor, appointmentType, appointmentDate.toLocalDate());
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0, Appointment.class);
            appointment.setId(100L);
            return appointment;
        });

        appointmentService.createAppointment(patient, request);

        AppointmentNotificationEvent event = captureNotificationEvent();
        assertThat(event.type()).isEqualTo(NotificationType.CREATED);
        assertThat(event.appointmentId()).isEqualTo(100L);
        assertThat(event.patientEmail()).isEqualTo("ana@test.com");
        assertThat(event.currentStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(event.previousStatus()).isNull();
    }

    @Test
    void updateStatusPublishesNotificationWhenStatusChanges() {
        User patient = patient();
        DoctorProfile doctor = doctor();
        Clinic clinic = clinic();
        AppointmentType appointmentType = appointmentType();
        LocalDateTime appointmentDate = LocalDate.now().plusDays(8).atTime(8, 0);
        Appointment appointment = appointment(100L, patient, doctor, clinic, appointmentType, appointmentDate, AppointmentStatus.PENDING);

        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        stubPrice(doctor, appointmentType, appointmentDate.toLocalDate());

        appointmentService.updateStatus(patient, 100L, AppointmentStatus.CONFIRMED);

        AppointmentNotificationEvent event = captureNotificationEvent();
        assertThat(event.type()).isEqualTo(NotificationType.STATUS_CHANGED);
        assertThat(event.previousStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(event.currentStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void reschedulePublishesNotificationEvenWhenStatusDoesNotChange() {
        User patient = patient();
        DoctorProfile doctor = doctor();
        Clinic clinic = clinic();
        AppointmentType appointmentType = appointmentType();
        LocalDateTime previousDate = LocalDate.now().plusDays(8).atTime(8, 0);
        LocalDateTime newDate = LocalDate.now().plusDays(9).atTime(8, 0);
        Appointment appointment = appointment(100L, patient, doctor, clinic, appointmentType, previousDate, AppointmentStatus.PENDING);
        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        request.setAppointmentDate(newDate);

        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        stubAvailableSlot(doctor, clinic, appointmentType, newDate);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        stubPrice(doctor, appointmentType, newDate.toLocalDate());

        appointmentService.reschedule(patient, 100L, request);

        AppointmentNotificationEvent event = captureNotificationEvent();
        assertThat(event.type()).isEqualTo(NotificationType.RESCHEDULED);
        assertThat(event.previousStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(event.currentStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(event.previousAppointmentDate()).isEqualTo(previousDate);
        assertThat(event.appointmentDate()).isEqualTo(newDate);
    }

    private AppointmentNotificationEvent captureNotificationEvent() {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(AppointmentNotificationEvent.class);
        return (AppointmentNotificationEvent) eventCaptor.getValue();
    }

    private void stubAvailableSlot(DoctorProfile doctor, Clinic clinic, AppointmentType appointmentType, LocalDateTime appointmentDate) {
        when(doctorProfileRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(clinicRepository.findById(clinic.getId())).thenReturn(Optional.of(clinic));
        when(appointmentTypeRepository.findById(appointmentType.getId())).thenReturn(Optional.of(appointmentType));
        when(doctorClinicRepository.existsByDoctorProfileIdAndClinicId(doctor.getId(), clinic.getId())).thenReturn(true);
        when(doctorScheduleRepository.findByDoctorProfileIdAndClinicIdAndDayOfWeekAndActiveTrue(
                doctor.getId(),
                clinic.getId(),
                appointmentDate.getDayOfWeek().getValue()
        )).thenReturn(List.of(DoctorSchedule.builder()
                .doctorProfile(doctor)
                .clinic(clinic)
                .dayOfWeek(appointmentDate.getDayOfWeek().getValue())
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .active(true)
                .build()));
        when(appointmentRepository.findByDoctorProfileIdAndClinicIdAndAppointmentDateBetween(
                doctor.getId(),
                clinic.getId(),
                appointmentDate.toLocalDate().atStartOfDay(),
                appointmentDate.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1)
        )).thenReturn(List.of());
    }

    private void stubPrice(DoctorProfile doctor, AppointmentType appointmentType, LocalDate date) {
        when(pricingService.resolvePrice(doctor, appointmentType, date))
                .thenReturn(AppointmentPrice.builder()
                        .appointmentType(appointmentType)
                        .medicalSpecialty(doctor.getMedicalSpecialty())
                        .price(BigDecimal.valueOf(120))
                        .currency("PEN")
                        .validFrom(date.minusDays(1))
                        .build());
    }

    private Appointment appointment(
            Long id,
            User patient,
            DoctorProfile doctor,
            Clinic clinic,
            AppointmentType appointmentType,
            LocalDateTime appointmentDate,
            AppointmentStatus status
    ) {
        return Appointment.builder()
                .id(id)
                .patient(patient)
                .doctorProfile(doctor)
                .clinic(clinic)
                .appointmentType(appointmentType)
                .appointmentDate(appointmentDate)
                .status(status)
                .price(BigDecimal.valueOf(120))
                .build();
    }

    private User patient() {
        return User.builder()
                .id(1L)
                .firstName("Ana")
                .lastName("Paz")
                .email("ana@test.com")
                .role(Role.PATIENT)
                .build();
    }

    private DoctorProfile doctor() {
        return DoctorProfile.builder()
                .id(2L)
                .medicalSpecialty("Cardiologia")
                .user(User.builder()
                        .firstName("Ricardo")
                        .lastName("Salazar")
                        .email("ricardo@test.com")
                        .role(Role.DOCTOR)
                        .build())
                .build();
    }

    private Clinic clinic() {
        return Clinic.builder()
                .id(3L)
                .name("Sede San Isidro")
                .address("Av. Javier Prado 123")
                .build();
    }

    private AppointmentType appointmentType() {
        return AppointmentType.builder()
                .id(4L)
                .name("Consulta presencial")
                .description("Consulta medica")
                .durationMinutes(30)
                .active(true)
                .build();
    }
}
