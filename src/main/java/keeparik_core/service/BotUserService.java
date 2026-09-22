package keeparik_core.service;

import keeparik_core.entity.BotUserEntity;
import keeparik_core.entity.Role;
import keeparik_core.repository.BotUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class BotUserService {

    private final BotUserRepository userRepository;
    private final Long rootAdminChatId;

    public BotUserService(
            BotUserRepository userRepository,
            @Value("${bot.admin-chat-id}") Long rootAdminChatId
    ) {
        this.userRepository = userRepository;
        this.rootAdminChatId = rootAdminChatId;
    }

    /**
     * При старте приложения гарантируем, что главный администратор из конфигурации
     * присутствует в базе с ролью ADMIN.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initRootAdmin() {
        if (rootAdminChatId == null || rootAdminChatId == 0) {
            log.warn("bot.admin-chat-id не задан в конфигурации!");
            return;
        }

        userRepository.findById(rootAdminChatId).ifPresentOrElse(
                user -> {
                    if (user.getRole() != Role.ADMIN || !user.getIsActive()) {
                        user.setRole(Role.ADMIN);
                        user.setIsActive(true);
                        userRepository.save(user);
                        log.info("Права супер-администратора восстановлены для ID: {}", rootAdminChatId);
                    }
                },
                () -> {
                    BotUserEntity rootAdmin = BotUserEntity.builder()
                            .telegramId(rootAdminChatId)
                            .username("RootAdmin")
                            .role(Role.ADMIN)
                            .isActive(true)
                            .build();
                    userRepository.save(rootAdmin);
                    log.info("Создан супер-администратор с ID: {}", rootAdminChatId);
                }
        );
    }

    @Transactional(readOnly = true)
    public Optional<BotUserEntity> getActiveUser(Long telegramId) {
        return userRepository.findByTelegramIdAndIsActiveTrue(telegramId);
    }

    @Transactional(readOnly = true)
    public boolean hasAccess(Long telegramId) {
        return userRepository.findByTelegramIdAndIsActiveTrue(telegramId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(Long telegramId) {
        return userRepository.findByTelegramIdAndIsActiveTrue(telegramId)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }

    @Transactional
    public BotUserEntity grantAccess(Long telegramId, String username, Role role) {
        BotUserEntity user = userRepository.findById(telegramId)
                .orElseGet(() -> BotUserEntity.builder()
                        .telegramId(telegramId)
                        .build());

        user.setUsername(username != null ? username : user.getUsername());
        user.setRole(role);
        user.setIsActive(true);

        return userRepository.save(user);
    }

    @Transactional
    public boolean revokeAccess(Long telegramId) {
        if (telegramId.equals(rootAdminChatId)) {
            throw new IllegalArgumentException("Нельзя отозвать доступ у главного администратора!");
        }

        return userRepository.findById(telegramId)
                .map(user -> {
                    user.setIsActive(false);
                    userRepository.save(user);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<BotUserEntity> getAllActiveUsers() {
        return userRepository.findAllByIsActiveTrue();
    }
}