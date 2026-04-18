package ca.exampractice.exampractice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionsRepository extends JpaRepository<Questions, Integer> {
    @Query("SELECT DISTINCT q.ia FROM Questions q WHERE q.cluster = :cluster")
    List<String> findAllDistinctIAsByCluster(@Param("cluster") String cluster);

    @Query("SELECT q FROM Questions q WHERE q.cluster = :cluster ORDER BY function('RAND')")
    Optional<List<Questions>> findRandomQuestions(@Param("cluster") String cluster);

    @Query("SELECT q FROM Questions q WHERE q.cluster = :cluster AND q.ia IN :ias ORDER BY function('RAND')")
    Optional<List<Questions>> findByIAs(@Param("cluster") String cluster, @Param("ias") List<String> ias);

    @Query("SELECT q FROM Questions q WHERE q.cluster = :cluster AND q.exam.examName = :examName ORDER BY function('RAND')")
    Optional<List<Questions>> findQuestionsByExam(@Param("cluster") String cluster, @Param("examName") String examName);

    @Query("SELECT q FROM Questions q WHERE q.cluster = :cluster AND q.exam.examName = :examName AND q.ia IN :ias ORDER BY function('RAND')")
    Optional<List<Questions>> findQuestionsByParams(@Param("cluster") String cluster, @Param("examName") String examName, @Param("ias") List<String> ias);
}
