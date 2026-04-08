package com.ticketing.domain.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.api.reservation.dto.ReservationResponse;
import com.ticketing.application.reservation.facade.ReservationFacade;
import com.ticketing.application.reservation.service.ReservationService;
import com.ticketing.domain.reservation.model.Reservation;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "scheduler.fixed-delay=100000000")
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QueueRedisRepository queueRedisRepository;

    @MockitoBean
    private ReservationFacade reservationFacade;

    @MockitoSpyBean
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        Mockito.reset(queueRedisRepository, reservationFacade, reservationService);
    }

    @Test
    @DisplayName("active 사용자가 예약 API를 호출하면 controller, aop, service를 거쳐 처리한다.")
    void createReservationSuccessWithActiveToken() throws Exception {
        ReservationCreateRequest request = createRequest(1L, 1L);
        Authentication authentication = createAuthentication(1L);

        Reservation reservation = Reservation.create(
                1L,
                1L,
                1L,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(5)
        );
        ReflectionTestUtils.setField(reservation, "reservationId", 1L);

        given(queueRedisRepository.validateAndExtendActiveMember(1L, 1L)).willReturn(true);
        given(reservationFacade.reserve(eq(1L), any(ReservationCreateRequest.class)))
                .willReturn(new ReservationResponse(reservation));

        mockMvc.perform(post("/api/v1/reservations")
                        .with(csrf())
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated());

        verify(queueRedisRepository).validateAndExtendActiveMember(1L, 1L);
        verify(reservationService).reserve(eq(1L), any(ReservationCreateRequest.class));
        verify(reservationFacade).reserve(eq(1L), any(ReservationCreateRequest.class));
    }

    @Test
    @DisplayName("active token이 없으면 예약 API는 controller 단계에서 차단된다.")
    void createReservationFailWithoutActiveToken() throws Exception {
        ReservationCreateRequest request = createRequest(1L, 1L);
        Authentication authentication = createAuthentication(1L);

        given(queueRedisRepository.validateAndExtendActiveMember(1L, 1L)).willReturn(false);

        mockMvc.perform(post("/api/v1/reservations")
                        .with(csrf())
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(queueRedisRepository).validateAndExtendActiveMember(1L, 1L);
        verify(reservationService, never()).reserve(eq(1L), any(ReservationCreateRequest.class));
        verify(reservationFacade, never()).reserve(eq(1L), any(ReservationCreateRequest.class));
    }

    private ReservationCreateRequest createRequest(Long scheduleId, Long seatId) {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", scheduleId);
        ReflectionTestUtils.setField(request, "seatId", seatId);
        return request;
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
