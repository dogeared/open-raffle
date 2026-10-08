package org.openraffle.service;

import org.openraffle.bgg.BggClient;
import org.openraffle.domain.Prize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps cached BoardGameGeek ratings current without anyone having to edit a prize: a few
 * times a day it fetches the rating of every linked prize whose rating is missing or a
 * month old, pausing between calls to stay polite to BGG. Off when there is no API token
 * (or {@code raffle.bgg.refresh=false}, as in tests).
 */
@Component
@ConditionalOnProperty(name = "raffle.bgg.refresh", havingValue = "true", matchIfMissing = true)
public class PrizeRatingRefresher {

    private static final Logger log = LoggerFactory.getLogger(PrizeRatingRefresher.class);

    private final PrizeService prizeService;
    private final BggClient bgg;
    private final long pauseMs;

    public PrizeRatingRefresher(PrizeService prizeService, BggClient bgg,
                                @Value("${raffle.bgg.pause-ms:1500}") long pauseMs) {
        this.prizeService = prizeService;
        this.bgg = bgg;
        this.pauseMs = pauseMs;
    }

    @Scheduled(initialDelayString = "${raffle.bgg.refresh-initial-delay:PT30S}", fixedDelayString = "${raffle.bgg.refresh-every:PT6H}")
    public void refreshStaleRatings() {
        if (!bgg.isEnabled()) {
            return;
        }
        List<Prize> stale = prizeService.findWithStaleRating();
        if (stale.isEmpty()) {
            return;
        }
        int refreshed = 0;
        for (Prize prize : stale) {
            if (prizeService.refreshRating(prize)) {
                refreshed++;
            }
            pause();
        }
        log.info("Refreshed BGG ratings for {} of {} prizes", refreshed, stale.size());
    }

    private void pause() {
        if (pauseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
