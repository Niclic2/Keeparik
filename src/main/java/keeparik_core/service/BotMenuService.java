package keeparik_core.service;

import keeparik_core.entity.CheckTargetEntity;
import keeparik_core.entity.ServerEntity;
import keeparik_core.repository.CheckTargetRepository;
import keeparik_core.repository.ServerRepository;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BotMenuService {

    private final ServerRepository serverRepository;
    private final CheckTargetRepository targetRepository;

    public static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());

    public BotMenuService(ServerRepository serverRepository, CheckTargetRepository targetRepository) {
        this.serverRepository = serverRepository;
        this.targetRepository = targetRepository;
    }

    /**
     * Текст списка серверов с итоговой стоимостью в конце
     */
    public String buildServerListText(List<ServerEntity> servers) {
        if (servers.isEmpty()) {
            return "В базе нет активных серверов.";
        }

        StringBuilder sb = new StringBuilder("⚙️ **Управление серверами:**\n\n");
        for (var server : servers) {
            String cost = server.getMonthlyCost() != null ? server.getMonthlyCost().toString() : "0";
            String currency = server.getCurrency() != null ? server.getCurrency() : "RUB";
            String provider = server.getProvider() != null ? server.getProvider() : "—";

            sb.append("🖥️ **").append(server.getName()).append("** (`").append(server.getHost()).append("`)\n")
              .append("   • Хостинг: ").append(provider).append("\n")
              .append("   • Стоимость: ").append(cost).append(" ").append(currency).append("/мес\n\n");
        }

        sb.append("💳 **Общая стоимость:** ").append(calculateTotalCostString(servers));
        return sb.toString();
    }

    /**
     * Подсчет и форматирование суммарной стоимости по всем валютам
     */
    public String calculateTotalCostString(List<ServerEntity> servers) {
        Map<String, BigDecimal> totalByCurrency = servers.stream()
                .filter(s -> s.getMonthlyCost() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getCurrency() != null ? s.getCurrency() : "RUB",
                        Collectors.reducing(BigDecimal.ZERO, ServerEntity::getMonthlyCost, BigDecimal::add)
                ));

        if (totalByCurrency.isEmpty()) {
            return "0.00 RUB/мес";
        }

        return totalByCurrency.entrySet().stream()
                .map(entry -> String.format("%.2f %s/мес", entry.getValue(), entry.getKey()))
                .collect(Collectors.joining(" + "));
    }

    /**
     * Клавиатура выбора сервера
     */
    public InlineKeyboardMarkup buildServerListKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        var servers = serverRepository.findAllByIsActiveTrue();

        for (var server : servers) {
            String btnText = String.format("🖥️ %s (%s)", server.getName(), server.getHost());
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text(btnText)
                            .callbackData("srv_view:" + server.getId())
                            .build()
            ));
        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("➕ Добавить сервер")
                        .callbackData("srv_add")
                        .build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /**
     * Подробная карточка сервера
     */
    public String buildServerCardText(ServerEntity server) {
        String paidTillStr = DATE_FORMATTER.format(server.getPaidTill());
        return String.format("""
                🖥️ **Сервер:** `%s`
                🌐 **Хост/IP:** `%s`
                🏢 **Хостинг / Где куплен:** %s
                💰 **Стоимость:** %s %s/мес
                📅 **Оплачен до:** *%s*
                """,
                server.getName(),
                server.getHost(),
                server.getProvider() != null ? server.getProvider() : "—",
                server.getMonthlyCost() != null ? server.getMonthlyCost() : "0",
                server.getCurrency() != null ? server.getCurrency() : "RUB",
                paidTillStr
        );
    }

    /**
     * Меню действий с возможностью редактирования хоста, провайдера, стоимости и срока оплаты
     */
    public InlineKeyboardMarkup buildServerCardKeyboard(Long serverId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🌐 Изм. IP").callbackData("srv_edithost:" + serverId).build(),
                        InlineKeyboardButton.builder().text("🏢 Изм. Хостинг").callbackData("srv_editprov:" + serverId).build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("💰 Изм. Цену").callbackData("srv_editcost:" + serverId).build(),
                        InlineKeyboardButton.builder().text("📅 Изм. Дату").callbackData("srv_editpaid:" + serverId).build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🎯 Проверки портов").callbackData("tgt_list:" + serverId).build(),
                        InlineKeyboardButton.builder().text("➕ Порт").callbackData("tgt_add:" + serverId).build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🗑️ Удалить сервер").callbackData("srv_del:" + serverId).build(),
                        InlineKeyboardButton.builder().text("⬅️ К списку").callbackData("srv_list").build()
                ))
                .build();
    }

    public InlineKeyboardMarkup buildTargetsKeyboard(Long serverId, List<CheckTargetEntity> targets) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (var t : targets) {
            String text = String.format("❌ Удалить [%s : %d]", t.getName(), t.getPort());
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text(text)
                            .callbackData("tgt_del:" + t.getId() + ":" + serverId)
                            .build()
            ));
        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("➕ Добавить проверку").callbackData("tgt_add:" + serverId).build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⬅️ Назад к серверу").callbackData("srv_view:" + serverId).build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public InlineKeyboardMarkup buildCancelKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("❌ Отмена").callbackData("cancel_fsm").build()
                ))
                .build();
    }
}