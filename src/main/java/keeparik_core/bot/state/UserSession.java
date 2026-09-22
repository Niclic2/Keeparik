package keeparik_core.bot.state;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class UserSession {
    private UserState state = UserState.IDLE;
    private Long targetServerId;
    private Long targetCheckId;
    private final Map<String, Object> data = new HashMap<>();

    public void clear() {
        this.state = UserState.IDLE;
        this.targetServerId = null;
        this.targetCheckId = null;
        this.data.clear();
    }
}