package com.sigaa.repository;

import com.sigaa.model.MemoHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoHistorialRepository extends JpaRepository<MemoHistorial, Long> {
    List<MemoHistorial> findByMemoIdOrderByCreatedAtAsc(Long memoId);
}
