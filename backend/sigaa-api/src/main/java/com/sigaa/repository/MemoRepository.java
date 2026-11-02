package com.sigaa.repository;

import com.sigaa.model.Memo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MemoRepository extends JpaRepository<Memo, Long> {
    List<Memo> findAllByOrderByUpdatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select memo from Memo memo where memo.id = :id")
    Optional<Memo> findByIdForUpdate(@Param("id") Long id);
}
