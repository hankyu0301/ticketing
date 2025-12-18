package com.ticketing.domain.concert.model;

import lombok.*;
import com.ticketing.global.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Concert extends BaseEntity {

    private Long concertId;
    private String title;
    private String description;

    @Builder
    private Concert(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public static Concert create(String title, String description) {
        return Concert.builder()
                .title(title)
                .description(description)
                .build();
    }
}
