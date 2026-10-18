package ca.exampractice.exampractice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Service
public class ExamService {
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private ExamPdfParser parser;

    public record UploadResult(Integer id, String examName, String cluster, int questionCount, List<String> warnings) {}

    public List<String> allExamNames(String cluster) {
        return examRepository.findAllExamNames(cluster);
    }

    public List<String> allClusters() {
        return examRepository.findAllClusters();
    }

    @Transactional
    public UploadResult importPdf(byte[] pdf, String examName, String cluster) {
        ExamPdfParser.ParsedExam parsed;
        try {
            parsed = parser.parse(pdf, cluster);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "That file couldn't be read as a PDF.");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
        if (parsed.questions().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No numbered questions were found in this PDF.");
        }
        if (examRepository.existsByClusterAndExamName(parsed.cluster(), examName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "\"" + examName + "\" is already in the " + parsed.cluster() + " question bank.");
        }

        Exam exam = new Exam();
        exam.setCluster(parsed.cluster());
        exam.setExamName(examName);
        for (var pq : parsed.questions()) {
            Questions q = new Questions();
            q.setQuestion(pq.question());
            q.setAnswerA(pq.answers().get(0));
            q.setAnswerB(pq.answers().get(1));
            q.setAnswerC(pq.answers().get(2));
            q.setAnswerD(pq.answers().get(3));
            q.setCorrectAnswer(pq.correctAnswer());
            q.setDescription(pq.description());
            q.setSpecificIA(pq.specificIA());
            q.setIa(pq.ia());
            q.setCluster(parsed.cluster());
            q.setExam(exam);
            exam.getQuestions().add(q);
        }
        Exam saved = examRepository.save(exam);
        return new UploadResult(saved.getId(), examName, parsed.cluster(), parsed.questions().size(), parsed.warnings());
    }
}
