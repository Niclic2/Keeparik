package keeparik_core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "servers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(length = 64)
    private String provider;

    @Column(name = "monthly_cost", precision = 10, scale = 2)
    private BigDecimal monthlyCost;

    @Column(length = 3)
    private String currency;

    @Column(name = "paid_till", nullable = false)
    private Instant paidTill;

    @Column(name = "ssh_port")
    private Integer sshPort;

    @Column(name = "ssh_user", length = 64)
    private String sshUser;

    @Column(name = "ssh_key_alias", length = 64)
    private String sshKeyAlias;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<CheckTargetEntity> targets = new ArrayList<>();

    // Удобный хелпер-метод для добавления таргета и поддержки двусторонней связи
    public void addTarget(CheckTargetEntity target) {
        targets.add(target);
        target.setServer(this);
    }
}