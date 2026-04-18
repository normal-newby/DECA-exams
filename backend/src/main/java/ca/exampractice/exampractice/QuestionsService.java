package ca.exampractice.exampractice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class QuestionsService {
    @Autowired
    private QuestionsRepository questionsRepository;

    public List<String> allIAsFromCluster(String cluster){
        return questionsRepository.findAllDistinctIAsByCluster(cluster);
    }

    public Optional<List<Questions>> questionsByIAs(
            String cluster,
            String exam,
            List<String> iaList){
        if (exam.equals("all")){
            if (iaList.contains("all")) {
                System.out.println("all, all");
                return questionsRepository.findRandomQuestions(cluster);
            } else {
                System.out.println("all, " + iaList);
                return questionsRepository.findByIAs(cluster, iaList);
            }
        } else {
            if (iaList.contains("all")){
                System.out.println(exam + ", all");
                return questionsRepository.findQuestionsByExam(cluster, exam);
            }
        }
        System.out.println(exam + ", " + iaList);
        return questionsRepository.findQuestionsByParams(cluster, exam, iaList);
    }
}
