package keeparik_core.repository;

import keeparik_core.entity.ServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ServerRepository extends JpaRepository<ServerEntity, Long> {

    // Spring сам поймёт этот метод по его названию и построит SQL:
    // SELECT * FROM servers WHERE is_active = true
    List<ServerEntity> findAllByIsActiveTrue();

    // Запрос для нашего сценария Биллинга (найти тех, у кого оплата до заданной даты):
    // SELECT * FROM servers WHERE is_active = true AND paid_till <= :deadline
    List<ServerEntity> findAllByIsActiveTrueAndPaidTillLessThanEqual(Instant deadline);
}