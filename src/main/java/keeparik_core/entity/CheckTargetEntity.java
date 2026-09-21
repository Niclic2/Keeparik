package keeparik_core.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "check_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "server") // ВАЖНО: исключаем server, чтобы не получить бесконечную рекурсию в toString()
public class CheckTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Внешний ключ на таблицу servers
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private ServerEntity server;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "check_type", nullable = false, length = 16)
    @Builder.Default
    private String checkType = "TCP";

    @Column(nullable = false)
    private Integer port;

    @Column(name = "http_path")
    private String httpPath;

    @Column(name = "expected_status")
    @Builder.Default
    private Integer expectedStatus = 200;

    @Column(name = "timeout_ms")
    @Builder.Default
    private Integer timeoutMs = 2500;

    @Column(name = "interval_sec")
    @Builder.Default
    private Integer intervalSec = 30;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
