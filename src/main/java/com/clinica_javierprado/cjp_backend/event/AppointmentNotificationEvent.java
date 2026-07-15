package com.clinica_javierprado.cjp_backend.event;

import com.clinica_javierprado.cjp_backend.domain.AppointmentStatus;

import java.time.LocalDateTime;

public record AppointmentNotificationEvent(
        Long appointmentId,
        NotificationType type,
        String patientEmail,
        String patientFirstName,
        AppointmentStatus previousStatus,
        AppointmentStatus currentStatus,
        LocalDateTime previousAppointmentDate,
        LocalDateTime appointmentDate,
        String doctorName,
        String medicalSpecialty,
        String clinicName,
        String clinicAddress,
        String appointmentTypeName
) {
    public enum NotificationType {
        CREATED,
        RESCHEDULED,
        STATUS_CHANGED
    }
}
