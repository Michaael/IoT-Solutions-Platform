package com.ispf.driver.dhcp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal DHCP DISCOVER/OFFER client for server discovery and lease status.
 */
final class DhcpDiscoverClient {

    private static final int OP_BOOTREQUEST = 1;
    private static final int HTYPE_ETHERNET = 1;
    private static final int DHCP_SERVER_PORT = 67;
    private static final int DHCP_CLIENT_PORT = 68;
    private static final byte DHCP_MESSAGE_TYPE = 53;
    private static final byte DHCP_OPTION_REQUESTED_IP = 50;
    private static final byte DHCP_OPTION_PARAMETER_LIST = 55;
    private static final byte DHCP_OPTION_SERVER_ID = 54;
    private static final byte DHCP_OPTION_LEASE_TIME = 51;
    private static final byte DHCP_END = (byte) 255;

    private DhcpDiscoverClient() {
    }

    static DhcpProbeResult probe(String interfaceName, String bindAddress, int timeoutMs) throws Exception {
        byte[] xid = randomXid();
        byte[] discover = buildDiscoverPacket(xid);
        try (DatagramSocket socket = createSocket(interfaceName, bindAddress, timeoutMs)) {
            DatagramPacket request = new DatagramPacket(
                    discover,
                    discover.length,
                    InetAddress.getByName("255.255.255.255"),
                    DHCP_SERVER_PORT
            );
            socket.send(request);

            byte[] buffer = new byte[576];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(response);
            } catch (SocketTimeoutException e) {
                return new DhcpProbeResult(false, "", 0);
            }
            return parseOffer(response.getData(), response.getLength(), xid);
        }
    }

    private static DatagramSocket createSocket(String interfaceName, String bindAddress, int timeoutMs)
            throws Exception {
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.setSoTimeout(timeoutMs);
        InetAddress local = resolveBindAddress(interfaceName, bindAddress);
        socket.bind(new InetSocketAddress(local, DHCP_CLIENT_PORT));
        return socket;
    }

    private static InetAddress resolveBindAddress(String interfaceName, String bindAddress) throws Exception {
        if (bindAddress != null && !bindAddress.isBlank()) {
            return InetAddress.getByName(bindAddress.trim());
        }
        if (interfaceName != null && !interfaceName.isBlank()) {
            NetworkInterface nic = NetworkInterface.getByName(interfaceName.trim());
            if (nic != null) {
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getAddress().length == 4) {
                        return address;
                    }
                }
            }
        }
        return InetAddress.getByName("0.0.0.0");
    }

    private static byte[] randomXid() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt((int) uuid.getMostSignificantBits());
        return buffer.array();
    }

    private static byte[] buildDiscoverPacket(byte[] xid) {
        byte[] packet = new byte[300];
        packet[0] = OP_BOOTREQUEST;
        packet[1] = HTYPE_ETHERNET;
        packet[2] = 6;
        packet[3] = 0;
        System.arraycopy(xid, 0, packet, 4, 4);
        packet[28] = (byte) 0xFF;
        packet[29] = (byte) 0xFF;
        packet[30] = (byte) 0xFF;
        packet[31] = (byte) 0xFF;
        packet[236] = (byte) 0x63;
        packet[237] = (byte) 0x82;
        packet[238] = (byte) 0x53;
        packet[239] = (byte) 0x63;
        int index = 240;
        packet[index++] = DHCP_MESSAGE_TYPE;
        packet[index++] = 1;
        packet[index++] = 1;
        packet[index++] = DHCP_OPTION_REQUESTED_IP;
        packet[index++] = 4;
        packet[index++] = 0;
        packet[index++] = 0;
        packet[index++] = 0;
        packet[index++] = 0;
        packet[index++] = DHCP_OPTION_PARAMETER_LIST;
        packet[index++] = 4;
        packet[index++] = 1;
        packet[index++] = 3;
        packet[index++] = 6;
        packet[index++] = 15;
        packet[index] = DHCP_END;
        return packet;
    }

    private static DhcpProbeResult parseOffer(byte[] data, int length, byte[] xid) {
        if (length < 240) {
            return new DhcpProbeResult(false, "", 0);
        }
        for (int i = 0; i < 4; i++) {
            if (data[4 + i] != xid[i]) {
                return new DhcpProbeResult(false, "", 0);
            }
        }
        String serverIp = "";
        long leaseSeconds = 0;
        int index = 240;
        while (index < length) {
            int option = data[index] & 0xFF;
            if (option == DHCP_END) {
                break;
            }
            if (option == 0) {
                index++;
                continue;
            }
            if (index + 1 >= length) {
                break;
            }
            int optionLen = data[index + 1] & 0xFF;
            if (index + 2 + optionLen > length) {
                break;
            }
            if (option == DHCP_OPTION_SERVER_ID && optionLen == 4) {
                serverIp = formatIpv4(data, index + 2);
            } else if (option == DHCP_OPTION_LEASE_TIME && optionLen == 4) {
                leaseSeconds = ((data[index + 2] & 0xFFL) << 24)
                        | ((data[index + 3] & 0xFFL) << 16)
                        | ((data[index + 4] & 0xFFL) << 8)
                        | (data[index + 5] & 0xFFL);
            }
            index += 2 + optionLen;
        }
        boolean leased = !serverIp.isBlank() || leaseSeconds > 0;
        if (serverIp.isBlank() && leased && length >= 20) {
            serverIp = formatIpv4(data, 20);
        }
        return new DhcpProbeResult(leased, serverIp, leaseSeconds);
    }

    private static String formatIpv4(byte[] data, int offset) {
        return (data[offset] & 0xFF) + "." + (data[offset + 1] & 0xFF) + "."
                + (data[offset + 2] & 0xFF) + "." + (data[offset + 3] & 0xFF);
    }

    record DhcpProbeResult(boolean leased, String serverIp, long leaseSeconds) {
        Optional<String> serverIpOptional() {
            return serverIp == null || serverIp.isBlank() ? Optional.empty() : Optional.of(serverIp);
        }
    }
}
