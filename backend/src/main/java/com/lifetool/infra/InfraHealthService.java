package com.lifetool.infra;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InfraHealthService {

    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;

    public InfraHealthService(
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${spring.data.redis.host:}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort,
            @Value("${spring.data.redis.password:}") String redisPassword) {
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
    }

    public Map<String, Object> check() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postgres", checkPostgres());
        result.put("redis", checkRedis());
        return result;
    }

    private Map<String, Object> checkPostgres() {
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            return status("not_configured", "spring.datasource.url is empty");
        }
        try (var conn = DriverManager.getConnection(datasourceUrl, datasourceUsername, datasourcePassword);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT version()")) {
            rs.next();
            return status("up", rs.getString(1));
        } catch (Exception ex) {
            return status("down", ex.getMessage());
        }
    }

    private Map<String, Object> checkRedis() {
        if (redisHost == null || redisHost.isBlank()) {
            return status("not_configured", "spring.data.redis.host is empty");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost, redisPort), 3000);
            socket.setSoTimeout(3000);
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            if (redisPassword != null && !redisPassword.isBlank()) {
                writer.write("*2\r\n$4\r\nAUTH\r\n$" + redisPassword.length() + "\r\n" + redisPassword + "\r\n");
                writer.flush();
                String auth = reader.readLine();
                if (auth == null || !auth.startsWith("+OK")) {
                    return status("down", "AUTH failed: " + auth);
                }
            }
            writer.write("*1\r\n$4\r\nPING\r\n");
            writer.flush();
            String pong = reader.readLine();
            if ("+PONG".equals(pong)) {
                return status("up", "PONG");
            }
            return status("down", "Unexpected response: " + pong);
        } catch (Exception ex) {
            return status("down", ex.getMessage());
        }
    }

    private Map<String, Object> status(String status, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("detail", detail);
        return result;
    }
}
