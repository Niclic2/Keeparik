package keeparik_core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Service
public class AlertNotificationService {

    private final TelegramClient telegramClient;
    private final Long adminChatId;

    public AlertNotificationService(
            TelegramClient telegramClient,
            @Value("${bot.admin-chat-id}") Long adminChatId
    ) {
        this.telegramClient = telegramClient;
        this.adminChatId = adminChatId;
    }

    public void notifyFailure(String serverName, String serviceName, String reason) {
        String text = String.format("""
                🚨 **ПАДЕНИЕ СЕРВИСА**
                Сервер: `%s`
                Цель: `%s`
                Причина: `%s`
                """, serverName, serviceName, reason);

        SendMessage message = SendMessage.builder()
                .chatId(adminChatId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить экстренный алерт", e);
        }
    }
}