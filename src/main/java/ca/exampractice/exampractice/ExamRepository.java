package ca.exampractice.exampractice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Integer> {

    @Query("SELECT e.examName FROM Exam e WHERE e.cluster = :cluster")
    List<String> findAllExamNames(@Param("cluster") String cluster);

}
