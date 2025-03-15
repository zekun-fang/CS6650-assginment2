package com.wjfzk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Consumer {
    private static final String HOST = "44.246.128.90"; // 本地测试时使用
    private static final String QUEUE_NAME = "skier_queue";
    private static final int THREAD_COUNT = 5;
    private static final int PREFETCH_COUNT = 100;

    private Connection connection;
    private ExecutorService executorService;
    // 用于存储处理过的消息（线程安全）
    private static final ConcurrentHashMap<Integer, List<Integer>> messageStore = new ConcurrentHashMap<>();
    // 用于优雅关闭消费者线程
    private CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        Consumer consumer = new Consumer();
        consumer.startConsuming();

        // 这里简单模拟运行一段时间后停止消费（例如运行 60 秒）
        Thread.sleep(600000);
        consumer.stopConsuming();
    }

    public void startConsuming() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setAutomaticRecoveryEnabled(true);
        factory.setRequestedHeartbeat(30);
        // 建立一个连接
        connection = factory.newConnection();

        // 使用固定线程池启动消费者线程
        executorService = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(new ConsumerTask(connection, QUEUE_NAME, shutdownLatch));
        }
    }

    public void stopConsuming() throws Exception {
        // 触发关闭信号，唤醒所有阻塞的消费者线程
        shutdownLatch.countDown();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (connection != null) {
            connection.close();
        }
    }

    // 内部静态类，作为消费者任务
    private static class ConsumerTask implements Runnable {

        private final Connection connection;
        private final String queueName;
        private final CountDownLatch shutdownLatch;

        public ConsumerTask(Connection connection, String queueName, CountDownLatch shutdownLatch) {
            this.connection = connection;
            this.queueName = queueName;
            this.shutdownLatch = shutdownLatch;
        }

        @Override
        public void run() {
            try {
                Channel channel = connection.createChannel();
                channel.queueDeclare(queueName, true, false, false, null);
                channel.basicQos(PREFETCH_COUNT); // 限制未确认的消息数

                System.out.println(" [*] Waiting for messages in " + queueName);

                channel.basicConsume(queueName, false, (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    System.out.println(" [x] Received from " + queueName + ": " + message);

                    storeMessage(message);
                    // 手动确认消息
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }, consumerTag -> {
                    System.out.println("Cancelled consumption of queue: " + queueName);
                });

                // 使用 CountDownLatch 阻塞线程，直到调用 stopConsuming 时唤醒退出
                shutdownLatch.await();
            } catch (IOException | InterruptedException e) {
                System.err.println("Consumer encountered error: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        private void storeMessage(String message) {
            try {
                JsonObject js = JsonParser.parseString(message).getAsJsonObject();
                int skierID = js.get("skierID").getAsInt();
                int liftID = js.get("liftID").getAsInt();
                messageStore.computeIfAbsent(skierID, k -> Collections.synchronizedList(new ArrayList<>())).add(liftID);
            } catch (Exception e) {
                System.err.println("Error storing message: " + e.getMessage());
            }
        }
    }
}

