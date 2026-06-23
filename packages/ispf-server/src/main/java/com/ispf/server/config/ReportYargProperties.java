package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ispf.reports.yarg")
public class ReportYargProperties {

    private LibreOffice libreOffice = new LibreOffice();

    public LibreOffice getLibreOffice() {
        return libreOffice;
    }

    public void setLibreOffice(LibreOffice libreOffice) {
        this.libreOffice = libreOffice;
    }

    public static class LibreOffice {
        /** Path to Libre/OpenOffice program dir (contains soffice). Empty = auto-detect on Linux. */
        private String path = "";
        private List<Integer> ports = new ArrayList<>(List.of(8100, 8101));
        private int timeoutSeconds = 120;
        private boolean displayDeviceAvailable = false;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<Integer> getPorts() {
            return ports;
        }

        public void setPorts(List<Integer> ports) {
            this.ports = ports;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isDisplayDeviceAvailable() {
            return displayDeviceAvailable;
        }

        public void setDisplayDeviceAvailable(boolean displayDeviceAvailable) {
            this.displayDeviceAvailable = displayDeviceAvailable;
        }
    }
}
