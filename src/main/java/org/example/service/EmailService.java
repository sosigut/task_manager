package org.example.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendTeamInvitation(String toEmail, String teamName, String inviterName, Long invitationId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@taskmanager.local");
        message.setTo(toEmail);
        message.setSubject(String.format("Вас пригласили в команду %s", teamName));

        message.setText("Здравствуйте!\n\n" +
                "Пользователь " + inviterName + " пригласил вас в команду \"" + teamName + "\".\n\n" +
                "Чтобы принять приглашение, используйте API (Swagger).\n" +
                "Ваш ID инвайта: " + invitationId + "\n\n" + // <-- Выводим ID прямо в текст
                "С уважением, система Task Manager.");

        mailSender.send(message);

    }
}
