package ca.exampractice.exampractice;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_sets")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String cluster;
    @Column(name = "exam_name")
    private String examName;
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL)
    private List<Questions> questions = new ArrayList<>();
}
