package ca.exampractice.exampractice;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Questions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(columnDefinition = "TEXT")
    private String question;
    private String answerA;
    private String answerB;
    private String answerC;
    private String answerD;
    @Column(name = "correct_answer")
    private String correctAnswer;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String specificIA;
    @Column(columnDefinition = "TEXT")
    private String ia;
    private String cluster;
    @ManyToOne
    @JoinColumn(name = "examset_id")
    @JsonIgnore
    private Exam exam;
}
