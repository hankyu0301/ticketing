package com.ticketing.infrastructure.lock.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

@Mapper
public interface NamedLockMapper {

    Integer getLock(@Param("lockName") String lockName,
                    @Param("timeoutSeconds") int timeoutSeconds);

    Integer releaseLock(@Param("lockName") String lockName);

}
