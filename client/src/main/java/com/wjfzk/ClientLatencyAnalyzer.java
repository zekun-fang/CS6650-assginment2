package com.wjfzk;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientLatencyAnalyzer {
  public static void latencyComputation(String logFile, int totalRequests) {
    List<Long> latencies = new ArrayList<>();
    long totalLatency = 0;
    long minLatency = Long.MAX_VALUE;
    long maxLatency = Long.MIN_VALUE;
    long globalStartTime = Long.MAX_VALUE; // 记录所有请求中最早的开始时间
    long globalEndTime = Long.MIN_VALUE;   // 记录所有请求中最晚的结束时间

    try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        if (parts.length < 4) {
            continue;
        }
        long startTime = Long.parseLong(parts[0]);
        long latency = Long.parseLong(parts[2]);
        long endTime = startTime + latency;

        // 更新全局开始和结束时间
        globalStartTime = Math.min(globalStartTime, startTime);
        globalEndTime = Math.max(globalEndTime, endTime);

        latencies.add(latency);
        minLatency = Math.min(minLatency, latency);
        maxLatency = Math.max(maxLatency, latency);
        totalLatency += latency;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (latencies.isEmpty()) {
      System.out.println("No latency data found!");
      return;
    }

    Collections.sort(latencies);
    double meanLatency = totalLatency / (double) totalRequests;
    long medianLatency = latencies.get(latencies.size() / 2);
    long p99Latency = latencies.get((int) (latencies.size() * 0.99));

    // 计算全局壁钟时间，单位毫秒
    long totalWallTime = globalEndTime - globalStartTime;
    // 计算整体吞吐量（请求数/秒）
    double overallThroughput = totalRequests / (totalWallTime / 1000.0);

    // Call the method to print stats
    System.out.println();
    System.out.println("======= Client Part2 Output ======= ");
    System.out.printf("Mean Response Time: %.2f ms%n", meanLatency);
    System.out.printf("Median Response Time: %d ms%n", medianLatency);
    System.out.printf("Min Response Time: %d ms%n", minLatency);
    System.out.printf("Max Response Time: %d ms%n", maxLatency);
    System.out.printf("Response Time at 99th Percentile: %d ms%n", p99Latency);
    System.out.printf("Overall Throughput (from logs): %.2f requests/sec%n", overallThroughput);
  }

}
