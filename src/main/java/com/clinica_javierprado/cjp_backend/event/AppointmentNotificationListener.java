package com.clinica_javierprado.cjp_backend.event;

import com.clinica_javierprado.cjp_backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentNotificationListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAppointmentNotification(AppointmentNotificationEvent event) {
        try {
            emailService.sendAppointmentNotificationEmail(event);
        } catch (RuntimeException ex) {
            log.warn("Could not send appointment notification email for appointment {}", event.appointmentId(), ex);
        }
    }
}
