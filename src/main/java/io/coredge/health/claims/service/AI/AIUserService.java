package io.coredge.health.claims.service.AI;

import io.coredge.health.claims.config.ai.AIUserProperties;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.db.repositories.permission.UserRepository;
import io.coredge.health.claims.exception.app.UserNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Getter
public class AIUserService {

    private final UserRepository userRepository;
    private final AIUserProperties aiUserProperties;

    private User aiUser;

    @Autowired
    public AIUserService(UserRepository userRepository,
                         AIUserProperties aiUserProperties) {
        this.userRepository = userRepository;
        this.aiUserProperties = aiUserProperties;
    }

    @PostConstruct
    public void init() {
        this.aiUser = userRepository
                .findByUsername(aiUserProperties.getUsername())
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "AI user not found: " + aiUserProperties.getUsername()
                        ));
    }

}