package com.ticketing.api.concert.dto;

import com.ticketing.domain.concert.model.Concert;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConcertCreateRequest {

    @NotBlank(message = ConcertErrorMessage.TITLE_MUST_NOT_EMPTY)
    private String title;

    @NotBlank(message = ConcertErrorMessage.DESCRIPTION_MUST_NOT_EMPTY)
    private String description;

    public Concert toEntity() {
        return Concert.create(this.title, this.description);
    }
}