# Spring Boot Fundamentals Quiz
총 5문항 · 정답 5 · 오답 0

## Q1. What is Spring Boot primarily used for in modern application development?
- A. Building desktop applications
- B. Simplifying the creation of production-ready, stand-alone Spring applications
- C. Developing complex algorithms for scientific computing
- D. Designing frontend user interfaces
✅ 내 답: Simplifying the creation of production-ready, stand-alone Spring applications
설명: Spring Boot's main goal is to get you up and running with a production-grade Spring application with minimal setup, focusing on stand-alone, 'just run' applications, often for microservices.

## Q2. Which annotation combines @Configuration, @EnableAutoConfiguration, and @ComponentScan into a single convenience annotation?
- A. @EnableSpringBoot
- B. @SpringBootConfig
- C. @SpringBootApplication
- D. @AutoConfigureSpring
✅ 내 답: @SpringBootApplication
설명: @SpringBootApplication is the primary annotation used on a Spring Boot application's main class. It enables auto-configuration, component scanning, and registers additional configuration classes.

## Q3. What is the main benefit of using Spring Boot Starters?
- A. To generate boilerplate code for new projects
- B. To provide a set of pre-defined database schemas
- C. To simplify dependency management and configuration for specific functionalities
- D. To create a graphical user interface for the application
✅ 내 답: To simplify dependency management and configuration for specific functionalities
설명: Starters are a set of convenient dependency descriptors that you can include in your application. They provide all the dependencies needed for a particular feature (e.g., web, data JPA) with compatible versions, simplifying your build configuration.

## Q4. Which of the following embedded servers is NOT directly supported by Spring Boot out-of-the-box for web applications?
- A. Tomcat
- B. Jetty
- C. WebLogic
- D. Undertow
✅ 내 답: WebLogic
설명: Spring Boot provides default support for embedded Tomcat, Jetty, and Undertow, which are lightweight and can be easily bundled within an executable JAR. WebLogic is an enterprise application server that typically requires traditional WAR deployment.

## Q5. How do you typically run a Spring Boot application that has been packaged as an executable JAR file?
- A. `mvn spring-boot:run`
- B. `java -jar myapp.jar`
- C. `gradle bootRun`
- D. `npm start`
✅ 내 답: `java -jar myapp.jar`
설명: Spring Boot applications can be packaged as executable JAR files, which contain all dependencies and an embedded server. These can be run directly from the command line using `java -jar`.