package com.ticketing.domain.concert.dto;

import com.ticketing.domain.concert.model.Concert;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConcertDto {

    private Long concertId;
    private String title;
    private String description;

    public ConcertDto(Concert concert) {
        this.concertId = concert.getConcertId();
        this.title = concert.getTitle();
        this.description = concert.getDescription();
    }
}
