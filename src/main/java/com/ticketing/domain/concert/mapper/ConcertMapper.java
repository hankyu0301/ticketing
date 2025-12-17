package com.ticketing.domain.concert.mapper;

import com.ticketing.domain.concert.model.Concert;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface ConcertMapper {

    // 콘서트 생성
    void insertConcert(Concert concert);

    // 콘서트 조회
    Optional<Concert> findById(Long concertId);

    boolean existsById(Long concertId);
}
