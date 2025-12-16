package com.auknowlog.backend.question.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDate;

@Document(indexName = "questions")
@Setting(settingPath = "/elasticsearch/settings.json")
public class QuestionDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String topic;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String questionText;

    @Field(type = FieldType.Keyword)
    private String questionHash;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String options;  // JSON 문자열

    @Field(type = FieldType.Text)
    private String correctAnswer;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String explanation;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate createdAt;

    // 기본 생성자
    public QuestionDocument() {}

    // 생성자
    public QuestionDocument(String id, String topic, String questionText, String questionHash,
                           String options, String correctAnswer, String explanation) {
        this.id = id;
        this.topic = topic;
        this.questionText = questionText;
        this.questionHash = questionHash;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.createdAt = LocalDate.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getQuestionHash() { return questionHash; }
    public void setQuestionHash(String questionHash) { this.questionHash = questionHash; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}

