package io.coredge.health.claims.db.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.coredge.health.claims.enums.AuthType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Table(name = "user_consumer")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserConsumer extends AuditableBase<String>{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String url;

    @Column(nullable = false)
    private String consumer;

    @Column(unique = true, nullable = false)
    private String username;

    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    private AuthType authType;

    @Column
    private String token;

    @Column(name = "is_callback",nullable = false)
    private Boolean isCallBack;

    @Column(name = "is_active" ,nullable = false )
    private Boolean isActive;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "callback_type")
    private String callbackType;

}

