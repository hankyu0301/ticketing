package com.ticketing.domain.seat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.api.seat.controller.SeatController;
import com.ticketing.api.seat.dto.SeatCreateRequest;
import com.ticketing.application.seat.service.SeatService;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertScheduleNotFoundException;
import com.ticketing.domain.seat.exception.SeatBadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static com.ticketing.domain.seat.exception.SeatErrorMessage.SEAT_COUNT_MISMATCH;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.SEATS_ALREADY_CREATED;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.TOTAL_SEATS_MUST_NOT_EMPTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeatController.class)
class SeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SeatService seatService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("좌석을 생성하면 201 Created를 반환한다.")
    void createSeatsSuccess() throws Exception {
        SeatCreateRequest request = createRequest(50, "VIP", BigDecimal.valueOf(10000));

        mockMvc.perform(post("/api/v1/schedules/1/seats")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("존재하지 않는 회차에 좌석 생성을 요청하면 404 Not Found를 반환한다.")
    void createSeatsFailScheduleNotFound() throws Exception {
        SeatCreateRequest request = createRequest(50, "VIP", BigDecimal.valueOf(10000));

        willThrow(new ConcertScheduleNotFoundException(ConcertErrorMessage.NOT_EXIST_SCHEDULE))
                .given(seatService)
                .createSeats(eq(1L), any(SeatCreateRequest.class));

        mockMvc.perform(post("/api/v1/schedules/1/seats")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(ConcertErrorMessage.NOT_EXIST_SCHEDULE)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("회차 좌석 수와 요청 좌석 수가 다르면 400 Bad Request를 반환한다.")
    void createSeatsFailTotalSeatMismatch() throws Exception {
        SeatCreateRequest request = createRequest(50, "VIP", BigDecimal.valueOf(10000));

        willThrow(new SeatBadRequestException(SEAT_COUNT_MISMATCH))
                .given(seatService)
                .createSeats(eq(1L), any(SeatCreateRequest.class));

        mockMvc.perform(post("/api/v1/schedules/1/seats")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(SEAT_COUNT_MISMATCH)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("이미 좌석이 생성된 회차에 다시 요청하면 400 Bad Request를 반환한다.")
    void createSeatsFailAlreadyCreated() throws Exception {
        SeatCreateRequest request = createRequest(50, "VIP", BigDecimal.valueOf(10000));

        willThrow(new SeatBadRequestException(SEATS_ALREADY_CREATED))
                .given(seatService)
                .createSeats(eq(1L), any(SeatCreateRequest.class));

        mockMvc.perform(post("/api/v1/schedules/1/seats")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(SEATS_ALREADY_CREATED)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("좌석 생성 요청 검증에 실패하면 400 Bad Request를 반환한다.")
    void createSeatsValidationFailTotalSeatsNull() throws Exception {
        SeatCreateRequest request = new SeatCreateRequest();
        ReflectionTestUtils.setField(request, "grade", "VIP");
        ReflectionTestUtils.setField(request, "price", BigDecimal.valueOf(10000));

        mockMvc.perform(post("/api/v1/schedules/1/seats")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(TOTAL_SEATS_MUST_NOT_EMPTY)));
    }

    private SeatCreateRequest createRequest(int totalSeats, String grade, BigDecimal price) {
        SeatCreateRequest request = new SeatCreateRequest();
        ReflectionTestUtils.setField(request, "totalSeats", totalSeats);
        ReflectionTestUtils.setField(request, "grade", grade);
        ReflectionTestUtils.setField(request, "price", price);
        return request;
    }
}
