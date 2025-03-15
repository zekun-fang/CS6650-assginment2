import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

@WebServlet(name = "SkierServlet", urlPatterns = "/skiers/*")
public class SkierServlet extends HttpServlet {

  private static final String RABBITMQ_HOST = "44.246.128.90";
  private static final int CHANNEL_POOL_CAPACITY = 300;
  private static final String TARGET_QUEUE = "skier_queue";
  private static final int FIXED_SEASON_ID = 2025;

  private Connection mqConnection;
  private BlockingQueue<Channel> channelPool;
  private final Gson jsonConverter = new Gson();

  @Override
  public void init() throws ServletException {
    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost(RABBITMQ_HOST);
      factory.setUsername("guest");
      factory.setPassword("guest");
      mqConnection = factory.newConnection();

      channelPool = new ArrayBlockingQueue<>(CHANNEL_POOL_CAPACITY);
      for (int i = 0; i < CHANNEL_POOL_CAPACITY; i++) {
        Channel channel = mqConnection.createChannel();
        channel.queueDeclare(TARGET_QUEUE, true, false, false, null);
        channelPool.offer(channel);
      }
    } catch (IOException | TimeoutException ex) {
      throw new ServletException("Failed to connect to RabbitMQ", ex);
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json");

    // URL 验证和参数提取
    String pathInfo = request.getPathInfo();
    if (pathInfo == null || !validatePathFormat(pathInfo)) {
      writeErrorResponse(response, "Invalid URL format! Expected: /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}", HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    String[] segments = pathInfo.split("/");
    int resortId = safeParseInt(segments[1], response, "Invalid resortID!");
    String dayId = segments[5];
    int skierId = safeParseInt(segments[7], response, "Invalid skierID!");

    if (!validateDay(dayId) || resortId < 1 || resortId > 10 || skierId < 1 || skierId > 100000) {
      writeErrorResponse(response, "Invalid parameters: resortID, dayID, or skierID out of range!", HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    // 请求体读取与 JSON 解析
    String reqBody = readRequestBody(request);
    System.out.println("Request body: " + reqBody);
    JsonObject payload;
    try {
      payload = jsonConverter.fromJson(reqBody, JsonObject.class);
      if (payload == null || !payload.isJsonObject()) {
        writeErrorResponse(response, "Invalid JSON format.", HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    } catch (JsonSyntaxException ex) {
      writeErrorResponse(response, "Invalid JSON syntax.", HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    // 必填字段验证
    if (!payload.has("liftID") || !payload.has("time")) {
      writeErrorResponse(response, "Missing required fields (liftID, time)", HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    // 添加 URL 参数到 JSON 数据
    payload.addProperty("resortID", resortId);
    payload.addProperty("dayID", dayId);
    payload.addProperty("skierID", skierId);
    payload.addProperty("seasonID", FIXED_SEASON_ID);
    payload.addProperty("time", payload.get("time").getAsString());
    payload.addProperty("liftID", payload.get("liftID").getAsInt());

    // 发布消息到 RabbitMQ
    try {
      publishToQueue(payload);
      writeSuccessResponse(response, "Skier processed successfully in queue: " + TARGET_QUEUE);
    } catch (Exception ex) {
      writeErrorResponse(response, "Failed to send message to RabbitMQ: " + ex.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void publishToQueue(JsonObject message) throws InterruptedException, IOException {
    Channel channel = channelPool.take();
    try {
      channel.basicPublish("", TARGET_QUEUE, null, message.toString().getBytes(StandardCharsets.UTF_8));
    } finally {
      channelPool.offer(channel);
    }
  }

  // 验证 URL 格式是否正确：预期格式 /{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
  private boolean validatePathFormat(String path) {
    String[] parts = path.split("/");
    return parts.length == 8;
  }

  // 验证 dayID 是否满足正则表达式要求
  private boolean validateDay(String day) {
    return day.matches("^([1-9]|[1-9][0-9]|[12][0-9][0-9]|3[0-5][0-9]|36[0-6])$");
  }

  private int safeParseInt(String value, HttpServletResponse response, String errorMessage) throws IOException {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      writeErrorResponse(response, errorMessage, HttpServletResponse.SC_BAD_REQUEST);
      return -1;
    }
  }

  private String readRequestBody(HttpServletRequest req) throws IOException {
    StringBuilder builder = new StringBuilder();
    try (BufferedReader br = req.getReader()) {
      String line;
      while ((line = br.readLine()) != null) {
        builder.append(line);
      }
    }
    return builder.toString();
  }

  private void writeErrorResponse(HttpServletResponse resp, String message, int statusCode) throws IOException {
    resp.setStatus(statusCode);
    PrintWriter writer = resp.getWriter();
    writer.write(jsonConverter.toJson(new ErrorResponse(message)));
  }

  private void writeSuccessResponse(HttpServletResponse resp, String message) throws IOException {
    resp.setStatus(HttpServletResponse.SC_CREATED);
    PrintWriter writer = resp.getWriter();
    writer.write(jsonConverter.toJson(new SuccessResponse(message)));
  }

  @Override
  public void destroy() {
    try {
      if (mqConnection != null) {
        for (Channel ch : channelPool) {
          ch.close();
        }
        mqConnection.close();
      }
    } catch (IOException | TimeoutException ex) {
      System.out.println("Failed to close RabbitMQ connection: " + ex.getMessage());
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("text/html");
    resp.setStatus(HttpServletResponse.SC_OK);
    PrintWriter out = resp.getWriter();
    out.println("<h1>Hello World!</h1>");
  }

  static class ErrorResponse {
    String message;
    ErrorResponse(String msg) {
      this.message = msg;
    }
  }

  static class SuccessResponse {
    String message;
    SuccessResponse(String msg) {
      this.message = msg;
    }
  }
}

