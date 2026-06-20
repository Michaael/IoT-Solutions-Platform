package com.ispf.driver.ipmi;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/**
 * Minimal RMCP+ ping (Get Channel Authentication Capabilities) for IPMI reachability.
 */
final class RmcpPingClient {

    private static final byte RMCP_CLASS_IPMI = 0x07;
    private static final byte RMCP_ACK = (byte) 0x80;
    private static final byte IPMI_GET_CHANNEL_AUTH_CAP = 0x38;

    private RmcpPingClient() {
    }

    static boolean ping(String host, int port, int timeoutMs) {
        byte[] request = buildAuthCapabilitiesRequest();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(request, request.length, address, port);
            socket.send(packet);
            byte[] buffer = new byte[256];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            return response.getLength() >= 4 && buffer[0] == RMCP_CLASS_IPMI;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] buildAuthCapabilitiesRequest() {
        return new byte[] {
                RMCP_CLASS_IPMI,
                RMCP_ACK,
                0x00,
                (byte) 0xFF,
                0x07,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                (byte) 0x81,
                0x00,
                0x00,
                0x00,
                0x08,
                IPMI_GET_CHANNEL_AUTH_CAP,
                0x0E,
                0x04
        };
    }
}
