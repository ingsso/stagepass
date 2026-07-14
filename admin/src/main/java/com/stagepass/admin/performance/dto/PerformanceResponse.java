package com.stagepass.admin.performance.dto;

import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class PerformanceResponse {
    private final Long id;
    private final String title;
    private final String genre;
    private final String description;
    private final String posterUrl;
    private final String venueName;
    private final String venueAddress;
    private final Integer runningTime;
    private final List<ShowSummary> shows;

    public PerformanceResponse(Performance performance, List<Show> shows) {
        this.id = performance.getId();
        this.title = performance.getTitle();
        this.genre = performance.getGenre();
        this.description = performance.getDescription();
        this.posterUrl = performance.getPosterUrl();
        this.venueName = performance.getVenueName();
        this.venueAddress = performance.getVenueAddress();
        this.runningTime = performance.getRunningTime();
        this.shows = shows.stream().map(ShowSummary::new).toList();
    }

    @Getter
    public static class ShowSummary {
        private final Long id;
        private final LocalDateTime showDatetime;
        private final String status;
        private final Integer totalSeats;

        public ShowSummary(Show show) {
            this.id = show.getId();
            this.showDatetime = show.getShowDatetime();
            this.status = show.getStatus().name();
            this.totalSeats = show.getTotalSeats();
        }
    }
}
