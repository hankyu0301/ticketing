package com.ticketing.domain.member.mapper;

import com.ticketing.domain.member.model.Member;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface MemberMapper {

    Optional<Member> findByEmail(String email);

}
