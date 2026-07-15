package com.clinica_javierprado.cjp_backend.event;

import com.clinica_javierprado.cjp_backend.domain.AppointmentStatus;
import com.clinica_javierprado.cjp_backend.event.AppointmentNotificationEvent.NotificationType;
import com.clinica_javierprado.cjp_backend.exception.EmailDeliveryException;
import com.clinica_javierprado.cjp_backend.service.EmailService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AppointmentNotificationListenerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final AppointmentNotificationListener listener = new AppointmentNotificationListener(emailService);

    @Test
    void emailFailureDoesNotPropagate() {
        AppointmentNotificationEvent event = new AppointmentNotificationEvent(
                1L,
                NotificationType.STATUS_CHANGED,
                "ana@test.com",
                "Ana",
                AppointmentStatus.PENDING,
                AppointmentStatus.CONFIRMED,
                null,
                LocalDateTime.now().plusDays(1),
                "Ricardo Salazar",
                "Cardiologia",
                "Sede San Isidro",
                "Av. Javier Prado 123",
                "Consulta presencial"
        );
        doThrow(new EmailDeliveryException("No se pudo enviar el correo.", new RuntimeException("resend down")))
                .when(emailService)
                .sendAppointmentNotificationEmail(event);

        assertThatCode(() -> listener.handleAppointmentNotification(event))
                .doesNotThrowAnyException();
    }
}
