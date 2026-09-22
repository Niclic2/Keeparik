package keeparik_core.bot.state;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserStateService {

    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession getSession(Long chatId) {
        return sessions.computeIfAbsent(chatId, k -> new UserSession());
    }

    public void setState(Long chatId, UserState state) {
        getSession(chatId).setState(state);
    }

    public void clearSession(Long chatId) {
        sessions.remove(chatId);
    }
}