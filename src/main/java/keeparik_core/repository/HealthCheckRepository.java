package keeparik_core.repository;

import keeparik_core.entity.HealthCheckEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealthCheckRepository extends JpaRepository<HealthCheckEntity, Long> {

    // Для будущего сценария графиков: достать последние N проверок по конкретному порту
    List<HealthCheckEntity> findTop50ByTargetIdOrderByCheckedAtDesc(Long targetId);
}
