package managerAgent.service;

import managerAgent.dto.InfrastructureStatusDto;
import org.springframework.stereotype.Service;
import utils.NacosUtil;

import java.net.InetSocketAddress;
import java.net.Socket;

@Service
public class InfrastructureStatusService {

    public InfrastructureStatusDto check() {
        InfrastructureStatusDto status = new InfrastructureStatusDto();
        status.setNacos(checkNacos());
        status.setRouteMakingAgent(checkPort(8082));
        status.setTripPlannerAgent(checkPort(8085));
        return status;
    }

    private boolean checkNacos() {
        try {
            NacosUtil.getNacosClient();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean checkPort(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 1500);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
