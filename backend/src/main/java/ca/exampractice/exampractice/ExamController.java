package ca.exampractice.exampractice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class ExamController {
    @Autowired
    private ExamService examService;

    @GetMapping("api/getExams/{cluster}")
    public ResponseEntity<List<String>> getAllExams(@PathVariable String cluster){
        return new ResponseEntity<List<String>>(examService.allExamNames(cluster), HttpStatus.OK);
    }


}
