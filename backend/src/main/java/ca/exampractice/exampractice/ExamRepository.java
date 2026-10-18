package ca.exampractice.exampractice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Integer> {

    @Query("SELECT e.examName FROM Exam e WHERE e.cluster = :cluster ORDER BY e.examName")
    List<String> findAllExamNames(@Param("cluster") String cluster);

    @Query("SELECT DISTINCT e.cluster FROM Exam e ORDER BY e.cluster")
    List<String> findAllClusters();

    boolean existsByClusterAndExamName(String cluster, String examName);
}
