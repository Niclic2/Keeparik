package keeparik_core.repository;

import keeparik_core.entity.CheckTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheckTargetRepository extends JpaRepository<CheckTargetEntity, Long> {

    List<CheckTargetEntity> findAllByIsActiveTrue();

    List<CheckTargetEntity> findAllByServerId(Long serverId);

    // JOIN FETCH за один SQL-запрос забирает и Target, и связанный с ним Server
    @Query("SELECT ct FROM CheckTargetEntity ct JOIN FETCH ct.server WHERE ct.isActive = true")
    List<CheckTargetEntity> findAllActiveWithServer();
}