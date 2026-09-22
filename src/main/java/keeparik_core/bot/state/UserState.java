package keeparik_core.bot.state;

public enum UserState {
    IDLE,
    // Шаги создания сервера
    SERVER_ADD_NAME,
    SERVER_ADD_HOST,
    SERVER_ADD_PROVIDER,
    SERVER_ADD_COST,
    SERVER_ADD_PAID_TILL,
    // Редактирование параметров сервера
    SERVER_EDIT_HOST,
    SERVER_EDIT_PROVIDER,
    SERVER_EDIT_COST,
    SERVER_EDIT_PAID_TILL,
    // Шаги добавления проверки
    TARGET_ADD_NAME,
    TARGET_ADD_PORT
}