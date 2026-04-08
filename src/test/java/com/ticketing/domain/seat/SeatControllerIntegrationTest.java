package com.ticketing.domain.seat;

import com.ticketing.api.seat.dto.SeatResponse;
import com.ticketing.application.seat.service.SeatService;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.global.security.service.CustomUserDetails;
import com.ticketing.global.security.service.SessionMember;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "scheduler.fixed-delay=100000000")
class SeatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueRedisRepository queueRedisRepository;

    @MockitoBean
    private SeatService seatService;

    @BeforeEach
    void setUp() {
        Mockito.reset(queueRedisRepository, seatService);
    }

    @Test
    @DisplayName("active 사용자가 좌석 조회를 요청하면 controller, aop, service를 거쳐 처리한다.")
    void getSeatsSuccessWithActiveToken() throws Exception {
        Authentication authentication = createAuthentication(1L);
        Seat seat = Seat.create(1L, 1, "VIP", BigDecimal.valueOf(10000));

        given(queueRedisRepository.validateAndExtendActiveMember(1L, 1L)).willReturn(true);
        given(seatService.getSeats(1L)).willReturn(List.of(new SeatResponse(seat)));

        mockMvc.perform(get("/api/v1/schedules/1/seats")
                        .with(authentication(authentication)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(queueRedisRepository).validateAndExtendActiveMember(1L, 1L);
        verify(seatService).getSeats(1L);
    }

    @Test
    @DisplayName("active token이 없으면 좌석 조회는 controller 단계에서 차단된다.")
    void getSeatsFailWithoutActiveToken() throws Exception {
        Authentication authentication = createAuthentication(1L);

        given(queueRedisRepository.validateAndExtendActiveMember(1L, 1L)).willReturn(false);

        mockMvc.perform(get("/api/v1/schedules/1/seats")
                        .with(authentication(authentication)))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(queueRedisRepository).validateAndExtendActiveMember(1L, 1L);
        verify(seatService, never()).getSeats(anyLong());
    }

    private Authentication createAuthentication(Long memberId) {
        CustomUserDetails userDetails = new CustomUserDetails(
                SessionMember.create(memberId, "user@test.com", "tester"),
                "password",
                Collections.emptyList()
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
