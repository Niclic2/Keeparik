package keeparik_core.bot;

import keeparik_core.repository.CheckTargetRepository;
import keeparik_core.repository.ServerRepository;
import keeparik_core.service.TcpProbeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.Executors;

@Slf4j
@Component
public class KeeparikBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final String botToken;
    private final Long adminChatId;
    private final TelegramClient telegramClient;
    private final ServerRepository serverRepository;
    private final CheckTargetRepository targetRepository;
    private final TcpProbeService probeService;

    public KeeparikBot(
            @Value("${bot.token}") String botToken,
            @Value("${bot.admin-chat-id}") Long adminChatId,
            TelegramClient telegramClient,
            ServerRepository serverRepository,
            CheckTargetRepository targetRepository,
            TcpProbeService probeService
    ) {
        this.botToken = botToken;
        this.adminChatId = adminChatId;
        this.telegramClient = telegramClient;
        this.serverRepository = serverRepository;
        this.targetRepository = targetRepository;
        this.probeService = probeService;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();

        // Проверка прав администратора (аналог AdminFilter в aiogram)
        if (!chatId.equals(adminChatId)) {
            sendTextMessage(chatId, "⛔ Доступ запрещен. Бот является приватным пультом мониторинга.");
            log.warn("Попытка доступа от неавторизованного пользователя: {}", chatId);
            return;
        }

        String messageText = update.getMessage().getText().trim();

        switch (messageText) {
            case "/start" -> sendTextMessage(chatId, """
                    🛡️ **Панель управления Keep'арик**
                    
                    Доступные команды:
                    /status — сводка по серверам и биллингу
                    /check  — принудительный сетевой опрос всех узлов
                    """);
            case "/status" -> handleStatusCommand(chatId);
            case "/check"  -> handleCheckCommand(chatId);
            default -> sendTextMessage(chatId, "Неизвестная команда. Используй /status или /check.");
        }
    }

    private void handleStatusCommand(Long chatId) {
        var servers = serverRepository.findAllByIsActiveTrue();
        if (servers.isEmpty()) {
            sendTextMessage(chatId, "В базе нет активных серверов.");
            return;
        }

        StringBuilder response = new StringBuilder("📊 **Серверы в мониторинге:**\n\n");
        for (var server : servers) {
            response.append("🖥️ **").append(server.getName()).append("** (").append(server.getHost()).append(")\n")
                    .append("Хостинг: ").append(server.getProvider() != null ? server.getProvider() : "—").append("\n")
                    .append("Оплачен до: ").append(server.getPaidTill()).append("\n\n");
        }
        sendTextMessage(chatId, response.toString());
    }

    private void handleCheckCommand(Long chatId) {
        sendTextMessage(chatId, "⚡ Выполняю параллельный опрос узлов...");

        var targets = targetRepository.findAllActiveWithServer();
        if (targets.isEmpty()) {
            sendTextMessage(chatId, "Нет активных целей для проверки.");
            return;
        }

        StringBuilder report = new StringBuilder("📋 **Результаты сетевого сканирования:**\n\n");

        // Параллельный опрос через виртуальные потоки Loom
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = targets.stream()
                    .map(target -> executor.submit(() -> probeService.probe(target)))
                    .toList();

            for (var future : futures) {
                try {
                    var result = future.get();
                    String icon = "UP".equals(result.getStatus()) ? "🟢" : "🔴";
                    report.append(icon).append(" ")
                            .append(result.getTarget().getServer().getName())
                            .append(" [").append(result.getTarget().getName()).append("]: ")
                            .append(result.getStatus())
                            .append(" (").append(result.getResponseTimeMs()).append(" мс)\n");
                } catch (Exception e) {
                    report.append("⚠️ Ошибка проверки узла: ").append(e.getMessage()).append("\n");
                }
            }
        }

        sendTextMessage(chatId, report.toString());
    }

    public void sendTextMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Сбой при отправке сообщения в Telegram", e);
        }
    }
}