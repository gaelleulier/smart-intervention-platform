package io.smartip.dashboard;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Component
class DashboardCacheEvictor {

    @CacheEvict(cacheNames = {"dashboard-summary", "dashboard-status-trends", "dashboard-technician-load", "dashboard-map"}, allEntries = true)
    public void evictAll() {
        // No-op, annotation handles eviction
    }
}
