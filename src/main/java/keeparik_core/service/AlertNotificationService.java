package keeparik_core.service;

import keeparik_core.entity.BotUserEntity;
import keeparik_core.entity.Role;
import keeparik_core.repository.BotUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Slf4j
@Service
public class AlertNotificationService {

    private final TelegramClient telegramClient;
    private final BotUserRepository userRepository;

    public AlertNotificationService(
            TelegramClient telegramClient,
            BotUserRepository userRepository
    ) {
        this.telegramClient = telegramClient;
        this.userRepository = userRepository;
    }

    public void notifyFailure(String serverName, String serviceName, String reason) {
        String text = String.format("""
                🚨 **ПАДЕНИЕ СЕРВИСА**
                Сервер: `%s`
                Цель: `%s`
                Причина: `%s`
                """, serverName, serviceName, reason);

        // Достаем всех активных администраторов из БД
        List<BotUserEntity> admins = userRepository.findAllByRoleAndIsActiveTrue(Role.ADMIN);

        for (BotUserEntity admin : admins) {
            SendMessage message = SendMessage.builder()
                    .chatId(admin.getTelegramId().toString())
                    .text(text)
                    .parseMode("Markdown")
                    .build();

            try {
                telegramClient.execute(message);
            } catch (TelegramApiException e) {
                log.error("Не удалось отправить экстренный алерт администратору {}", admin.getTelegramId(), e);
            }
        }
    }
}