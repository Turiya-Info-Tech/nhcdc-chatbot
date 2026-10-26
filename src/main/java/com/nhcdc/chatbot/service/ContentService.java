package com.nhcdc.chatbot.service;

import com.nhcdc.chatbot.entity.Centre;
import com.nhcdc.chatbot.entity.ServiceOffering;
import com.nhcdc.chatbot.repository.CentreRepository;
import com.nhcdc.chatbot.repository.ServiceOfferingRepository;
import com.nhcdc.chatbot.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Read access to the admin-managed content the bot shows (centres, services). All lookups ignore inactive rows.
 * Results are cached (see CacheConfig) so most messages never touch the DB; empty lookups are not cached.
 */
@Service
@RequiredArgsConstructor
public class ContentService {

    private final CentreRepository centreRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    @Cacheable(CacheConfig.CENTRES)
    public List<Centre> getActiveCentres() {
        return centreRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
    }

    @Cacheable(CacheConfig.SERVICES)
    public List<ServiceOffering> getActiveServices() {
        return serviceOfferingRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
    }

    @Cacheable(value = CacheConfig.CENTRES, key = "'id:' + #id", unless = "#result == null")
    public Optional<Centre> findActiveCentre(Long id) {
        return centreRepository.findByIdAndActiveTrue(id);
    }

    @Cacheable(value = CacheConfig.SERVICES, key = "'id:' + #id", unless = "#result == null")
    public Optional<ServiceOffering> findActiveService(Long id) {
        return serviceOfferingRepository.findByIdAndActiveTrue(id);
    }
}
