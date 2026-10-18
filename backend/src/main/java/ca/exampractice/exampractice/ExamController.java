package ca.exampractice.exampractice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class ExamController {
    @Autowired
    private ExamService examService;

    @GetMapping("api/getExams/{cluster}")
    public List<String> getAllExams(@PathVariable String cluster) {
        return examService.allExamNames(cluster);
    }

    @GetMapping("api/clusters")
    public List<String> getClusters() {
        return examService.allClusters();
    }

    /** Upload a DECA exam PDF (questions + answer key). Cluster is detected from the PDF unless given. */
    @PostMapping(value = "api/exams/upload", consumes = "multipart/form-data")
    public ResponseEntity<ExamService.UploadResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "examName", required = false) String examName,
            @RequestParam(value = "cluster", required = false) String cluster) throws IOException {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (file.isEmpty() || !original.toLowerCase().endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a .pdf file.");
        }
        String name = examName == null || examName.isBlank() ? original : examName.strip();
        return ResponseEntity.status(HttpStatus.CREATED).body(examService.importPdf(file.getBytes(), name, cluster));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", String.valueOf(e.getReason())));
    }
}
