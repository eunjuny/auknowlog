package com.auknowlog.backend.learning.entity;

import com.auknowlog.backend.roadmap.entity.LearningObjective;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_question")
public class LearningQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private LearningQuiz quiz;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String options;

    @Column(name = "correct_answer", nullable = false)
    private String correctAnswer;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "option_explanations", nullable = false, columnDefinition = "TEXT")
    private String optionExplanations;

    @Column(name = "source_references", columnDefinition = "TEXT")
    private String sourceReferences;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_objective_id")
    private LearningObjective learningObjective;

    protected LearningQuestion() {
    }

    public LearningQuestion(LearningQuiz quiz, int questionOrder, String questionText, String options,
                            String correctAnswer, String explanation, String optionExplanations, String sourceReferences) {
        this(quiz, questionOrder, questionText, options, correctAnswer, explanation, optionExplanations, sourceReferences, null);
    }

    public LearningQuestion(LearningQuiz quiz, int questionOrder, String questionText, String options,
                            String correctAnswer, String explanation, String optionExplanations, String sourceReferences,
                            LearningObjective learningObjective) {
        this.quiz = quiz;
        this.questionOrder = questionOrder;
        this.questionText = questionText;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.optionExplanations = optionExplanations;
        this.sourceReferences = sourceReferences;
        this.learningObjective = learningObjective;
    }

    public Long getId() {
        return id;
    }

    public LearningQuiz getQuiz() {
        return quiz;
    }

    public int getQuestionOrder() {
        return questionOrder;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getOptions() {
        return options;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getOptionExplanations() {
        return optionExplanations;
    }

    public String getSourceReferences() {
        return sourceReferences;
    }

    public LearningObjective getLearningObjective() {
        return learningObjective;
    }
}
