package keeparik_core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "health_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "target")
public class HealthCheckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private CheckTargetEntity target;

    @Column(nullable = false, length = 16)
    private String status; // UP, DOWN, TIMEOUT

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "checked_at", insertable = false, updatable = false)
    private Instant checkedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}