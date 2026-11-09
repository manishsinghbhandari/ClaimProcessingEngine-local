package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.entity.ProviderAttributes;
import io.coredge.health.claims.db.repositories.ProviderAttributeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderAttributeService {

    private final ProviderAttributeRepository providerAttributeRepository;

    @Transactional
    public ProviderAttributes createOrUpdateProviderAttributes(Provider provider, boolean isVemsSupported) {

        ProviderAttributes existing = providerAttributeRepository.findByProvider(provider);

        if (existing != null) {
            existing.setVemsSupported(isVemsSupported);
            return providerAttributeRepository.save(existing);
        }

        ProviderAttributes newAttr = ProviderAttributes.builder()
                .provider(provider)
                .isVemsSupported(isVemsSupported)
                .build();

        return providerAttributeRepository.save(newAttr);
    }


    /**
     * Returns provider attributes if present.
     */
    public ProviderAttributes getProviderAttributes(Provider provider) {
        return providerAttributeRepository.findByProvider(provider);
    }

}
