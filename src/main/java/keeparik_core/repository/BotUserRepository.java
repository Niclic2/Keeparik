package keeparik_core.repository;

import keeparik_core.entity.BotUserEntity;
import keeparik_core.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BotUserRepository extends JpaRepository<BotUserEntity, Long> {

    Optional<BotUserEntity> findByTelegramIdAndIsActiveTrue(Long telegramId);

    List<BotUserEntity> findAllByIsActiveTrue();

    List<BotUserEntity> findAllByRoleAndIsActiveTrue(Role role);
}