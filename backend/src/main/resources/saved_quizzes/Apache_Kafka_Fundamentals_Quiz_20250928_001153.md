# Apache Kafka Fundamentals Quiz
총 5문항 · 정답 5 · 오답 0

## Q1. What is Apache Kafka primarily designed for?
- A. A relational database management system
- B. A distributed streaming platform
- C. A NoSQL document store
- D. An in-memory cache
✅ 내 답: A distributed streaming platform
설명: Apache Kafka is a distributed streaming platform capable of handling trillions of events a day, designed for building real-time data pipelines and streaming applications.

## Q2. In Kafka, what is a "Topic"?
- A. A single message sent by a producer
- B. A category or feed name to which records are published
- C. A unique identifier for a consumer group
- D. The physical server where Kafka is installed
✅ 내 답: A category or feed name to which records are published
설명: Topics are the fundamental logical channels in Kafka where data records are published by producers and read by consumers. They are broken down into partitions.

## Q3. Which component in Apache Kafka is responsible for storing streams of records?
- A. Zookeeper
- B. Producer
- C. Broker
- D. Consumer
✅ 내 답: Broker
설명: A Kafka broker is a server that runs the Kafka service. It stores the published data for specified retention periods and handles requests from producers and consumers.

## Q4. How does Apache Kafka typically achieve high throughput and scalability?
- A. By relying on a single, powerful server
- B. Through vertical scaling only
- C. By partitioning topics across multiple brokers
- D. By storing all data in RAM
✅ 내 답: By partitioning topics across multiple brokers
설명: Kafka achieves scalability and high throughput by dividing topics into partitions, which can be distributed and replicated across multiple brokers in a cluster, allowing for parallel processing.

## Q5. What is the role of a "Producer" in Apache Kafka?
- A. To read and process messages from topics
- B. To store messages on disk
- C. To publish (write) records to topics
- D. To manage the Kafka cluster configuration
✅ 내 답: To publish (write) records to topics
설명: Producers are client applications that write (publish) data records to Kafka topics. They decide which topic and partition a record should be sent to.