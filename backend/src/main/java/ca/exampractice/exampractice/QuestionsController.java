package ca.exampractice.exampractice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class QuestionsController {
    @Autowired
    private QuestionsService questionsService;

    @GetMapping("api/{cluster}/getIAs/")
    public ResponseEntity<List<String>> getAllIAsFromCluster(@PathVariable String cluster){
        return new ResponseEntity<List<String>>(questionsService.allIAsFromCluster(cluster), HttpStatus.OK);
    }

    @PostMapping("api/getExams/{cluster}/{exam}/questions")
    public ResponseEntity<Optional<List<Questions>>> getQuestions(
            @PathVariable String cluster,
            @PathVariable String exam,
            @RequestBody Map<String, List<String>> payload){
        List<String> iaList = payload.get("ias");
        return new ResponseEntity<Optional<List<Questions>>>(questionsService.questionsByIAs(cluster, exam, iaList), HttpStatus.OK);
    }
}
