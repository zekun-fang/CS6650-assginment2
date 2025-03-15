# CS6650 Assignment 2 - Deployment README

This document provides step-by-step instructions to deploy and run the three independent modules: server, consumer, and client. Follow the steps below to set up your environment on AWS EC2 instances.

---

## Overview

- **server:** 
  A servlet-based modulepackaged as a WAR file. It receives skier lift ride events, validates requests, and publishes messages to RabbitMQ.

- **consumer:** 
  A standalone Java module(packaged as a JAR) that consumes messages from the RabbitMQ queue and processes them.

- **client:** 
  A load testing module(multithreaded client) that sends HTTP POST requests to the ServerAPI. It logs response times and throughput statistics.

---

## Prerequisites

1. **AWS EC2 Instances:**  
   You will need four EC2 instances:
   - Two instances running Tomcat for ServerAPI (tomcat and tomcat2)
   - One instance for running the Consumer application
   - One instance for hosting RabbitMQ (ensure RabbitMQ is installed and running)

2. **RabbitMQ Configuration:**  
   - Install RabbitMQ on the dedicated instance.
   - Adjust the **RABBITMQ_HOST**  in class `SkierServlet` and **HOST** in class `Consumer`  to point to the IP address of the RabbitMQ instance.
   - Optionally, enable the RabbitMQ management plugin for monitoring.

3. **Load Balancer (ELB):**  
   - Set up an Elastic Load Balancer (ELB) in front of the two Tomcat instances to distribute incoming traffic.
   - Note the ELB DNS name as it will be used to update the **SERVER_URL** in the class `MultiThreadedLiftRideClient`

## Deployment Steps

### 1. Deploy Server

1. **Find the WAR file:**  
   Navigate to the server module and find war file in :
   
   ```bash
   target/server-1.0-SNAPSHOT.war

1. **Upload WAR to EC2:**
    Transfer the WAR file to both Tomcat instances (tomcat and tomcat2) using SCP, SFTP, or your preferred method.

2. **Deploy in Tomcat:**
    Place the WAR file in the Tomcat `webapps` directory. Tomcat will automatically deploy and extract the application.

3. **Configuration:**

   - If RabbitMQ is not running on localhost, modify the `RABBITMQ_HOST  ` configuration in the ServerAPI code accordingly.

   - Verify the deployment by accessing:

     ```
     http://<ec2instance-IP>:8080/server-1.0-SNAPSHOT/skiers/
     ```

     You should see a simple "Hello World!" message for GET requests.

### 2. Deploy Consumer

1. **Find the Consumer JAR:**
    Navigate to the Consumer module directory and find:

   ```bash
   target/consumer-1.0-SNAPSHOT.jar
   ```

2. **Upload JAR to EC2:**
    Transfer the JAR file to the Consumer instance.

3. **Run the Consumer:**
    SSH into the Consumer instance and run:

   ```bash
   java -jar consumer-1.0-SNAPSHOT.jar
   ```

   - Make sure to update the `HOST` configuration in the Consumer code if RabbitMQ is not on localhost.
   - The Consumer should connect to RabbitMQ and start processing messages.

### 3. Deploy and Run Client (Load Testing)

1. **Find the Client JAR:**
    Navigate to the Client project directory and find:

   ```bash
   target/client-1.0-SNAPSHOT.jar
   ```

2. **Update SERVER_URL:**
    In the `MultiThreadedLiftRideClient` class, modify:

   ```java
   private static final String SERVER_URL = "http://<ELB-DNS>:8080/assignment1_war_exploded";
   ```

   Replace `<ELB-DNS>` with your Elastic Load Balancer DNS name so that the Client sends requests to the load-balanced environment.

3. **Run the Client:**
    Run the Client from your local machine or an EC2 instance:

   ```bash
   java -jar MultiThreadedLiftRideClient.jar
   ```

   Monitor the output for performance metrics (throughput, latency, etc.).

------

## Additional Configuration Notes

- **RabbitMQ Settings:**
   The RabbitMQ queue is declared as durable and uses a prefetch count (configured via `channel.basicQos`) to ensure balanced message consumption. A channel pool (size 300) is maintained to minimize connection overhead and improve throughput.
- **Load Balancer:**
   The ELB distributes incoming HTTP requests between the two Tomcat instances, improving availability and fault tolerance. Ensure that your security groups and firewall rules allow communication between the ELB and the Tomcat instances.
- **Environment Variables:**
   If needed, set environment variables or update configuration files to reflect your specific deployment IP addresses and port numbers.

------

## Running the Project

1. **Start RabbitMQ:**
    Ensure RabbitMQ is running on its dedicated instance.
2. **Start ServerAPI:**
    Ensure both Tomcat instances have deployed the WAR file and are accessible.
3. **Start Consumer:**
    Run the Consumer JAR on its dedicated EC2 instance.
4. **Run Client:**
    Execute the Client JAR to perform load testing. Monitor console output and RabbitMQ management console for metrics and queue size.
