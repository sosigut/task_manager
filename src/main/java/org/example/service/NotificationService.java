package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.NotificationDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendPersonalNotification(String targetUsername, NotificationDto notification){

        messagingTemplate.convertAndSendToUser(targetUsername, "/queue/personalNotification", notification);

    }

}
