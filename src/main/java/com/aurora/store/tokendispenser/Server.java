package com.aurora.store.tokendispenser;

import static javax.servlet.SessionTrackingMode.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class Server {

    private static final Logger LOG = LoggerFactory.getLogger(Server.class.getName());
    private static final String AURORA_URL = "https://auroraoss.com/api/auth";
    private static final String USER_AGENT = "com.aurora.store-4.1.1-41";
    private static final int CONNECTION_TIMEOUT = 22;

    static Map<String, String> authMap = new HashMap<>();

    public static void main(String[] args) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(System.getProperty("credentials", "auth.txt")));
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                String[] credential = line.split(" ");
                authMap.put(credential[0], credential[1]);
            }
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String host = System.getProperty("host", "0.0.0.0");
        // Google auth requests are not fast, so lets limit max simultaneous threads
        Spark.threadPool(32, 2, 5000);
        Spark.ipAddress(host);
        Spark.port(Integer.parseInt(System.getProperty("port", "6060")));

        if (System.getProperty("keystore") != null) {
            Spark.secure(System.getProperty("keystore"), System.getProperty("keystore_password"), null, null);
        }

        Spark.before((req, res) -> {
            LOG.info(req.requestMethod() + " " + req.url());
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Request-Method", "GET");
        });

        Spark.after((req, res) -> res.type("text/plain"));
        Spark.get("/", (req, res) -> "Aurora Token Dispenser");
        Spark.get("/api/auth", Server::getAuth);
        Spark.post("/api/auth", Server::postAuth);
        Spark.get("/api/authOrigin", Server::getAuthOrigin);
        Spark.post("/api/authOrigin", Server::postAuthOrigin);
        Spark.after("/api/auth", Server::afterGetAuth);
        Spark.notFound((req, res) -> "You are lost !");
    }

    private static Response getAuth(Request request, Response response) {
        Object[] keyArray = authMap.keySet().toArray();
        response.type("application/json");
        if (keyArray.length == 0) {
            String body = "{\"message\":\"Could not retrieve key from server\"}";
            response.status(500);
            response.body(body);
        } else {
            Object key = keyArray[new Random().nextInt(keyArray.length)];
            String email = key.toString();
            if (email == null || email.isEmpty()) {
                String body = "{\"message\":\"Could not retrieve email from server\"}";
                response.status(500);
                response.body(body);
            } else {
                String token = authMap.get(email);
                if (token == null || token.isEmpty()) {
                    String body = "{\"message\":\"Could not retrieve token from server\"}";
                    response.status(500);
                    response.body(body);
                } else {
                    String body = String.format("{\"email\":\"%s\",\"auth\":\"%s\"}", email, token);
                    response.status(200);
                    response.body(body);
                }
            }
        }
        return response;
    }

    private static Response postAuth(Request request, Response response) {
        return getAuth(request, response);
    }

    private static Response getAuthOrigin(Request request, Response response) {
        try {
            java.net.URL url = new URL(AURORA_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (connection != null) {
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setConnectTimeout(CONNECTION_TIMEOUT * 1000);
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader buffer = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line = null;
                    StringBuilder body = new StringBuilder();
                    while ((line = buffer.readLine()) != null) {
                        body.append(line);
                    }
                    buffer.close();
                    response.status(200);
                    response.body(body.toString());
                } else {
                    String body = "{\"message\":\"Server response code not ok\"}";
                    response.status(500);
                    response.body(body);
                }
            } else {
                String body = "{\"message\":\"Could not open coonection to server\"}";
                response.status(500);
                response.body(body);
            }
        } catch (SocketTimeoutException ex) {
            String body = "{\"message\":\"Connection with the server got time out\"}";
            response.status(500);
            response.body(body);
        } catch (Exception ex) {
            String body = "{\"message\":\"Failed to fetch token from server\"}";
            response.status(500);
            response.body(body);
        }
        return response;
    }

    private static Response postAuthOrigin(Request request, Response response) {
        try {
            String reqBody = request.body();
            if (reqBody != null && reqBody.length() != 0) {
                java.net.URL url = new URL(AURORA_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                if (connection != null) {
                    connection.setRequestProperty("User-Agent", USER_AGENT);
                    connection.setConnectTimeout(CONNECTION_TIMEOUT * 1000);
                    connection.setRequestMethod("POST");
                    connection.setUseCaches(false);
                    connection.setDoOutput(true);
                    OutputStream output = connection.getOutputStream();
                    OutputStreamWriter outputStream = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                    outputStream.write(reqBody);
                    outputStream.flush();
                    outputStream.close();
                    output.close();
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader buffer = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String line = null;
                        StringBuilder body = new StringBuilder();
                        while ((line = buffer.readLine()) != null) {
                            body.append(line);
                        }
                        buffer.close();
                        response.status(200);
                        response.body(body.toString());
                    } else {
                        String body = "{\"message\":\"Server response code not ok\"}";
                        response.status(500);
                        response.body(body);
                    }
                } else {
                    String body = "{\"message\":\"Could not open coonection to server\"}";
                    response.status(500);
                    response.body(body);
                }
            } else {
                String body = "{\"message\":\"Request body is empty\"}";
                response.status(500);
                response.body(body);
            }
        } catch (SocketTimeoutException ex) {
            String body = "{\"message\":\"Connection with the server got time out\"}";
            response.status(500);
            response.body(body);
        } catch (Exception ex) {
            String body = "{\"message\":\"Failed to fetch token from server\"}";
            response.status(500);
            response.body(body);
        }
        return response;
    }

    private static void afterGetAuth(Request request, Response response) {
        response.type("application/json");
    }

    private static int getHerokuAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return 4567;
    }
}
