package keeparik_core.bot;

import keeparik_core.service.BotMenuService;
import keeparik_core.bot.state.UserSession;
import keeparik_core.bot.state.UserState;
import keeparik_core.bot.state.UserStateService;
import keeparik_core.entity.BotUserEntity;
import keeparik_core.entity.CheckTargetEntity;
import keeparik_core.entity.Role;
import keeparik_core.entity.ServerEntity;
import keeparik_core.repository.CheckTargetRepository;
import keeparik_core.repository.ServerRepository;
import keeparik_core.service.BotUserService;
import keeparik_core.service.TcpProbeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class KeeparikBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final String botToken;
    private final TelegramClient telegramClient;
    private final ServerRepository serverRepository;
    private final CheckTargetRepository targetRepository;
    private final TcpProbeService probeService;
    private final BotUserService userService;
    private final UserStateService stateService;
    private final BotMenuService menuService;

    private static final DateTimeFormatter DATE_INPUT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public KeeparikBot(
            @Value("${bot.token}") String botToken,
            TelegramClient telegramClient,
            ServerRepository serverRepository,
            CheckTargetRepository targetRepository,
            TcpProbeService probeService,
            BotUserService userService,
            UserStateService stateService,
            BotMenuService menuService
    ) {
        this.botToken = botToken;
        this.telegramClient = telegramClient;
        this.serverRepository = serverRepository;
        this.targetRepository = targetRepository;
        this.probeService = probeService;
        this.userService = userService;
        this.stateService = stateService;
        this.menuService = menuService;
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
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        Optional<BotUserEntity> userOpt = userService.getActiveUser(chatId);
        if (userOpt.isEmpty()) {
            sendTextMessage(chatId, String.format("""
                    ⛔ **Доступ запрещен**
                    
                    Для получения доступа передайте администратору ваш ID:
                    `%d`
                    """, chatId));
            return;
        }

        BotUserEntity currentUser = userOpt.get();
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        UserSession session = stateService.getSession(chatId);
        if (session.getState() != UserState.IDLE) {
            handleFsmTextInput(chatId, text, session);
            return;
        }

        String[] parts = text.split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            case "/start" -> handleStartCommand(chatId, currentUser);
            case "/manage" -> {
                if (ensureAdmin(chatId, isAdmin)) {
                    var servers = serverRepository.findAllByIsActiveTrue();
                    sendInlineMenu(chatId, menuService.buildServerListText(servers), menuService.buildServerListKeyboard());
                }
            }
            case "/status", "/servers" -> handleStatusCommand(chatId, isAdmin);
            case "/check" -> handleCheckCommand(chatId);
            case "/users" -> {
                if (ensureAdmin(chatId, isAdmin)) handleUsersCommand(chatId);
            }
            case "/grant" -> {
                if (ensureAdmin(chatId, isAdmin)) handleGrantCommand(chatId, parts);
            }
            case "/revoke" -> {
                if (ensureAdmin(chatId, isAdmin)) handleRevokeCommand(chatId, parts);
            }
            default -> sendTextMessage(chatId, "Неизвестная команда. Нажмите /start для вывода меню.");
        }
    }

    // =========================================================================
    //  CALLBACK QUERY HANDLER (НАЖАТИЯ КНОПОК)
    // =========================================================================

    private void handleCallbackQuery(Update update) {
        var callback = update.getCallbackQuery();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();
        String data = callback.getData();

        acknowledgeCallback(callback.getId());

        if (!userService.isAdmin(chatId)) {
            sendTextMessage(chatId, "⛔ Управление серверами доступно только администраторам.");
            return;
        }

        if (data.equals("cancel_fsm")) {
            stateService.clearSession(chatId);
            sendTextMessage(chatId, "❌ Действие отменено.");
            var servers = serverRepository.findAllByIsActiveTrue();
            sendInlineMenu(chatId, menuService.buildServerListText(servers), menuService.buildServerListKeyboard());
            return;
        }

        if (data.equals("srv_list")) {
            var servers = serverRepository.findAllByIsActiveTrue();
            editMessage(chatId, messageId, menuService.buildServerListText(servers), menuService.buildServerListKeyboard());
            return;
        }

        if (data.startsWith("srv_view:")) {
            Long serverId = Long.parseLong(data.split(":")[1]);
            serverRepository.findById(serverId).ifPresent(server ->
                    editMessage(chatId, messageId, menuService.buildServerCardText(server), menuService.buildServerCardKeyboard(serverId))
            );
            return;
        }

        if (data.equals("srv_add")) {
            UserSession session = stateService.getSession(chatId);
            session.setState(UserState.SERVER_ADD_NAME);
            sendInlineMenu(chatId, "📝 **Шаг 1/5:** Введите имя сервера (например: `NL-VLESS` или `Worker-01`):", menuService.buildCancelKeyboard());
            return;
        }

        // Обработка кнопок редактирования
        if (data.startsWith("srv_edithost:")) {
            Long serverId = Long.parseLong(data.split(":")[1]);
            prepareEditState(chatId, serverId, UserState.SERVER_EDIT_HOST, "🌐 Введите новый IP или хост сервера:");
            return;
        }

        if (data.startsWith("srv_editprov:")) {
            Long serverId = Long.parseLong(data.split(":")[1]);
            prepareEditState(chatId, serverId, UserState.SERVER_EDIT_PROVIDER, "🏢 Введите хостинг / сервис покупки (например: `Aeza`, `Hetzner`):");
            return;
        }

        if (data.startsWith("srv_editcost:")) {
            Long serverId = Long.parseLong(data.split(":")[1]);
            prepareEditState(chatId, serverId, UserState.SERVER_EDIT_COST, "💰 Введите новую стоимость (например: `450` или `5.50 USD`):");
            return;
        }

        if (data.startsWith("srv_editpaid:")) {
            Long serverId = Long.parseLong(data.split(":")[1]);
            prepareEditState(chatId, serverId, UserState.SERVER_EDIT_PAID_TILL, "📅 Введите новую дату окончания аренды (`ДД.ММ.ГГГГ`):");
            return;
        }

        if (data.startsWith("srv_del:")) {
            Long serverId = Long.parseLong(data.split(":")[1]);
            serverRepository.findById(serverId).ifPresent(server -> {
                server.setIsActive(false);
                serverRepository.save(server);
            });
            var servers = serverRepository.findAllByIsActiveTrue();
            editMessage(chatId, messageId, "🗑️ Сервер удален из мониторинга.\n\n" + menuService.buildServerListText(servers), menuService.buildServerListKeyboard());
            return;
        }

        if (data.startsWith("tgt_list:")) {
            Long serverId = Long.parseLong(data.split(":")[1]);
            var targets = targetRepository.findAllByServerId(serverId);
            String title = targets.isEmpty()
                    ? "У сервера пока нет проверок портов."
                    : "🎯 **Проверки портов:**\n_Нажмите на проверку для её удаления:_";
            editMessage(chatId, messageId, title, menuService.buildTargetsKeyboard(serverId, targets));
            return;
        }

        if (data.startsWith("tgt_add:")) {
            Long serverId = Long.parseLong(data.split(":")[1]);
            UserSession session = stateService.getSession(chatId);
            session.setState(UserState.TARGET_ADD_NAME);
            session.setTargetServerId(serverId);
            sendInlineMenu(chatId, "🎯 **Шаг 1/2:** Введите название службы (например: `SSH`, `VLESS`, `HTTP`):", menuService.buildCancelKeyboard());
            return;
        }

        if (data.startsWith("tgt_del:")) {
            String[] parts = data.split(":");
            Long targetId = Long.parseLong(parts[1]);
            Long serverId = Long.parseLong(parts[2]);

            targetRepository.deleteById(targetId);

            var targets = targetRepository.findAllByServerId(serverId);
            editMessage(chatId, messageId, "✅ Проверка удалена.\n\n🎯 **Оставшиеся проверки:**", menuService.buildTargetsKeyboard(serverId, targets));
        }
    }

    private void prepareEditState(Long chatId, Long serverId, UserState state, String prompt) {
        UserSession session = stateService.getSession(chatId);
        session.setState(state);
        session.setTargetServerId(serverId);
        sendInlineMenu(chatId, prompt, menuService.buildCancelKeyboard());
    }

    // =========================================================================
    //  FSM INPUT HANDLER (ВИЗАРД ДОБАВЛЕНИЯ И ИЗМЕНЕНИЯ)
    // =========================================================================

    private void handleFsmTextInput(Long chatId, String text, UserSession session) {
        switch (session.getState()) {
            // Визард создания сервера
            case SERVER_ADD_NAME -> {
                session.getData().put("name", text);
                session.setState(UserState.SERVER_ADD_HOST);
                sendInlineMenu(chatId, "🌐 **Шаг 2/5:** Введите IP-адрес или домен:", menuService.buildCancelKeyboard());
            }
            case SERVER_ADD_HOST -> {
                session.getData().put("host", text);
                session.setState(UserState.SERVER_ADD_PROVIDER);
                sendInlineMenu(chatId, "🏢 **Шаг 3/5:** Укажите хостинг / место покупки (например: `Aeza`, `Hetzner`, `Selectel`):", menuService.buildCancelKeyboard());
            }
            case SERVER_ADD_PROVIDER -> {
                session.getData().put("provider", text);
                session.setState(UserState.SERVER_ADD_COST);
                sendInlineMenu(chatId, "💰 **Шаг 4/5:** Введите стоимость в месяц (например: `490` или `5.50 USD`):", menuService.buildCancelKeyboard());
            }
            case SERVER_ADD_COST -> {
                try {
                    ParsedCost cost = parseCostInput(text);
                    session.getData().put("cost", cost.amount());
                    session.getData().put("currency", cost.currency());

                    session.setState(UserState.SERVER_ADD_PAID_TILL);
                    sendInlineMenu(chatId, "📅 **Шаг 5/5:** Введите дату окончания аренды в формате `ДД.ММ.ГГГГ` (например: `28.11.2026`):", menuService.buildCancelKeyboard());
                } catch (Exception e) {
                    sendInlineMenu(chatId, "❌ Неверный формат стоимости! Введите число (например `500` или `10 USD`):", menuService.buildCancelKeyboard());
                }
            }
            case SERVER_ADD_PAID_TILL -> {
                try {
                    LocalDate date = LocalDate.parse(text, DATE_INPUT_FORMAT);
                    ServerEntity newServer = ServerEntity.builder()
                            .name((String) session.getData().get("name"))
                            .host((String) session.getData().get("host"))
                            .provider((String) session.getData().get("provider"))
                            .monthlyCost((BigDecimal) session.getData().get("cost"))
                            .currency((String) session.getData().get("currency"))
                            .paidTill(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
                            .isActive(true)
                            .build();

                    serverRepository.save(newServer);
                    stateService.clearSession(chatId);

                    sendTextMessage(chatId, "🎉 **Сервер успешно добавлен!**");
                    var servers = serverRepository.findAllByIsActiveTrue();
                    sendInlineMenu(chatId, menuService.buildServerListText(servers), menuService.buildServerListKeyboard());
                } catch (DateTimeParseException e) {
                    sendInlineMenu(chatId, "❌ Неверный формат даты! Используйте `ДД.ММ.ГГГГ` (например: `28.11.2026`):", menuService.buildCancelKeyboard());
                }
            }

            // Редактирование
            case SERVER_EDIT_HOST -> {
                Long serverId = session.getTargetServerId();
                serverRepository.findById(serverId).ifPresent(s -> {
                    s.setHost(text);
                    serverRepository.save(s);
                    sendTextMessage(chatId, "✅ IP/Хост обновлен.");
                    sendInlineMenu(chatId, menuService.buildServerCardText(s), menuService.buildServerCardKeyboard(s.getId()));
                });
                stateService.clearSession(chatId);
            }
            case SERVER_EDIT_PROVIDER -> {
                Long serverId = session.getTargetServerId();
                serverRepository.findById(serverId).ifPresent(s -> {
                    s.setProvider(text);
                    serverRepository.save(s);
                    sendTextMessage(chatId, "✅ Хостинг обновлен.");
                    sendInlineMenu(chatId, menuService.buildServerCardText(s), menuService.buildServerCardKeyboard(s.getId()));
                });
                stateService.clearSession(chatId);
            }
            case SERVER_EDIT_COST -> {
                try {
                    ParsedCost cost = parseCostInput(text);
                    Long serverId = session.getTargetServerId();
                    serverRepository.findById(serverId).ifPresent(s -> {
                        s.setMonthlyCost(cost.amount());
                        s.setCurrency(cost.currency());
                        serverRepository.save(s);
                        sendTextMessage(chatId, "✅ Стоимость обновлена.");
                        sendInlineMenu(chatId, menuService.buildServerCardText(s), menuService.buildServerCardKeyboard(s.getId()));
                    });
                    stateService.clearSession(chatId);
                } catch (Exception e) {
                    sendInlineMenu(chatId, "❌ Неверный формат стоимости! Введите число (например `500` или `10 USD`):", menuService.buildCancelKeyboard());
                }
            }
            case SERVER_EDIT_PAID_TILL -> {
                try {
                    LocalDate date = LocalDate.parse(text, DATE_INPUT_FORMAT);
                    Long serverId = session.getTargetServerId();
                    serverRepository.findById(serverId).ifPresent(s -> {
                        s.setPaidTill(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
                        serverRepository.save(s);
                        sendTextMessage(chatId, "✅ Дата окончания аренды обновлена.");
                        sendInlineMenu(chatId, menuService.buildServerCardText(s), menuService.buildServerCardKeyboard(s.getId()));
                    });
                    stateService.clearSession(chatId);
                } catch (DateTimeParseException e) {
                    sendInlineMenu(chatId, "❌ Неверный формат даты! Используйте `ДД.ММ.ГГГГ`:", menuService.buildCancelKeyboard());
                }
            }

            // Добавление проверки
            case TARGET_ADD_NAME -> {
                session.getData().put("target_name", text);
                session.setState(UserState.TARGET_ADD_PORT);
                sendInlineMenu(chatId, "🔌 **Шаг 2/2:** Введите номер порта (1..65535):", menuService.buildCancelKeyboard());
            }
            case TARGET_ADD_PORT -> {
                try {
                    int port = Integer.parseInt(text);
                    if (port < 1 || port > 65535) throw new NumberFormatException();

                    Long serverId = session.getTargetServerId();
                    ServerEntity server = serverRepository.findById(serverId).orElseThrow();

                    CheckTargetEntity target = CheckTargetEntity.builder()
                            .server(server)
                            .name((String) session.getData().get("target_name"))
                            .port(port)
                            .checkType("TCP")
                            .isActive(true)
                            .build();

                    targetRepository.save(target);
                    stateService.clearSession(chatId);

                    sendTextMessage(chatId, String.format("🎉 Проверка для порта `%d` добавлена!", port));
                    sendInlineMenu(chatId, menuService.buildServerCardText(server), menuService.buildServerCardKeyboard(serverId));
                } catch (NumberFormatException e) {
                    sendInlineMenu(chatId, "❌ Введите корректный номер порта (от 1 до 65535):", menuService.buildCancelKeyboard());
                }
            }
            default -> stateService.clearSession(chatId);
        }
    }

    private record ParsedCost(BigDecimal amount, String currency) {}

    private ParsedCost parseCostInput(String text) {
        String[] parts = text.trim().split("\\s+");
        BigDecimal amount = new BigDecimal(parts[0].replace(",", "."));
        String currency = parts.length > 1 ? parts[1].toUpperCase() : "RUB";
        return new ParsedCost(amount, currency);
    }

    // =========================================================================
    //  СТАНДАРТНЫЕ КОМАНДЫ
    // =========================================================================

    private void handleStartCommand(Long chatId, BotUserEntity user) {
        if (user.getRole() == Role.ADMIN) {
            sendTextMessage(chatId, """
                    🛡️ **Панель Администратора Keep'арик**

                    **Управление серверами:**
                    /manage — интерактивное управление, добавление, изменение и расчет расходов
                    /status — быстрая сводка серверов
                    /check  — параллельный опрос доступности

                    **Пользователи:**
                    /users — список пользователей
                    /grant `<id>` `<VIEWER|ADMIN>` — предоставить доступ
                    /revoke `<id>` — отозвать доступ
                    """);
        } else {
            sendTextMessage(chatId, """
                    👁️ **Мониторинг серверов Keep'арик (Зритель)**

                    /servers — список активных серверов
                    /check   — сетевой опрос доступности
                    """);
        }
    }

    private void handleStatusCommand(Long chatId, boolean isAdmin) {
        var servers = serverRepository.findAllByIsActiveTrue();
        if (servers.isEmpty()) {
            sendTextMessage(chatId, "В базе нет активных серверов.");
            return;
        }

        StringBuilder response = new StringBuilder("📊 **Серверы в мониторинге:**\n\n");
        for (var server : servers) {
            response.append("🖥️ **").append(server.getName()).append("** (`").append(server.getHost()).append("`)\n");
            if (isAdmin) {
                String paidTillStr = BotMenuService.DATE_FORMATTER.format(server.getPaidTill());
                response.append("   • Хостинг: ").append(server.getProvider() != null ? server.getProvider() : "—").append("\n")
                        .append("   • Оплата: ").append(server.getMonthlyCost() != null ? server.getMonthlyCost() : "0")
                        .append(" ").append(server.getCurrency() != null ? server.getCurrency() : "RUB").append("/мес\n")
                        .append("   • Оплачен до: *").append(paidTillStr).append("*\n");
            }
            response.append("\n");
        }

        if (isAdmin) {
            response.append("💳 **Общая стоимость:** ").append(menuService.calculateTotalCostString(servers));
        }

        sendTextMessage(chatId, response.toString());
    }

    private void handleCheckCommand(Long chatId) {
        sendTextMessage(chatId, "⚡ Выполняю сетевой опрос узлов...");

        var targets = targetRepository.findAllActiveWithServer();
        if (targets.isEmpty()) {
            sendTextMessage(chatId, "Нет активных целей для проверки.");
            return;
        }

        StringBuilder report = new StringBuilder("📋 **Результаты сканирования:**\n\n");
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
                    report.append("⚠️ Ошибка проверки: ").append(e.getMessage()).append("\n");
                }
            }
        }
        sendTextMessage(chatId, report.toString());
    }

    private void handleUsersCommand(Long chatId) {
        var users = userService.getAllActiveUsers();
        StringBuilder sb = new StringBuilder("👥 **Список пользователей:**\n\n");
        for (var u : users) {
            String roleBadge = u.getRole() == Role.ADMIN ? "👑 ADMIN" : "👁️ VIEWER";
            String username = u.getUsername() != null ? "@" + u.getUsername() : "—";
            sb.append(String.format("• `%d` | %s | %s\n", u.getTelegramId(), username, roleBadge));
        }
        sendTextMessage(chatId, sb.toString());
    }

    private void handleGrantCommand(Long chatId, String[] parts) {
        if (parts.length < 3) {
            sendTextMessage(chatId, "Формат: `/grant <telegram_id> <VIEWER|ADMIN>`");
            return;
        }
        try {
            Long targetChatId = Long.parseLong(parts[1]);
            Role role = Role.valueOf(parts[2].toUpperCase());
            userService.grantAccess(targetChatId, null, role);
            sendTextMessage(chatId, String.format("✅ Пользователю `%d` выдана роль **%s**.", targetChatId, role));
            sendTextMessage(targetChatId, "🎉 Вам выдан доступ к мониторингу Keep'арик с ролью **" + role + "**.\nНажмите /start");
        } catch (IllegalArgumentException e) {
            sendTextMessage(chatId, "❌ Ошибка в ID или роли (допустимо: VIEWER или ADMIN).");
        }
    }

    private void handleRevokeCommand(Long chatId, String[] parts) {
        if (parts.length < 2) {
            sendTextMessage(chatId, "Формат: `/revoke <telegram_id>`");
            return;
        }
        try {
            Long targetChatId = Long.parseLong(parts[1]);
            boolean revoked = userService.revokeAccess(targetChatId);
            sendTextMessage(chatId, revoked ? "🚫 Доступ успешно отозван." : "Пользователь не найден.");
        } catch (IllegalArgumentException e) {
            sendTextMessage(chatId, "❌ " + e.getMessage());
        }
    }

    private boolean ensureAdmin(Long chatId, boolean isAdmin) {
        if (!isAdmin) {
            sendTextMessage(chatId, "⛔ Доступно только администраторам.");
            return false;
        }
        return true;
    }

    public void sendTextMessage(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Сбой отправки сообщения", e);
        }
    }

    public void sendInlineMenu(Long chatId, String text, InlineKeyboardMarkup markup) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(markup)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Сбой отправки меню", e);
        }
    }

    public void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup markup) {
        try {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(markup)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Сбой редактирования сообщения", e);
        }
    }

    private void acknowledgeCallback(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .build());
        } catch (TelegramApiException ignored) {}
    }
}