package keeparik_core.service;

import keeparik_core.entity.CheckTargetEntity;
import keeparik_core.entity.HealthCheckEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

@Service
public class TcpProbeService {

    public HealthCheckEntity probe(CheckTargetEntity target) {
        String host = target.getServer().getHost();
        int port = target.getPort();
        int timeout = target.getTimeoutMs();

        long startTime = System.currentTimeMillis();

        try (Socket socket = new Socket()) {
            // Подключаемся к целевому хосту и порту
            socket.connect(new InetSocketAddress(host, port), timeout);
            long latency = System.currentTimeMillis() - startTime;

            return HealthCheckEntity.builder()
                    .target(target)
                    .status("UP")
                    .responseTimeMs((int) latency)
                    .build();

        } catch (SocketTimeoutException e) {
            long latency = System.currentTimeMillis() - startTime;
            return HealthCheckEntity.builder()
                    .target(target)
                    .status("TIMEOUT")
                    .responseTimeMs((int) latency)
                    .errorMessage("Таймаут соединения (" + timeout + " мс)")
                    .build();

        } catch (IOException e) {
            long latency = System.currentTimeMillis() - startTime;
            return HealthCheckEntity.builder()
                    .target(target)
                    .status("DOWN")
                    .responseTimeMs((int) latency)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}