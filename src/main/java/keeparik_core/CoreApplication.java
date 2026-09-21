package keeparik_core;

import keeparik_core.repository.CheckTargetRepository;
import keeparik_core.repository.HealthCheckRepository;
import keeparik_core.repository.ServerRepository;
import keeparik_core.service.TcpProbeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executors;

@SpringBootApplication
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}