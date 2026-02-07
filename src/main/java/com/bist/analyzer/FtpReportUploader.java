package com.bist.analyzer;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FtpReportUploader {
    
    private String host;
    private int port;
    private String username;
    private String password;
    private String remotePath;
    private boolean useFtps;
    
    public FtpReportUploader(String host, int port, String username, String password, String remotePath) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.remotePath = remotePath;
        this.useFtps = false;
    }
    
    /**
     * Upload report.html and all chart files to FTP server
     */
    public void uploadReports(String localReportDir) {
        FTPClient ftpClient = new FTPClient();
        try {
            // Remove ftp:// prefix if exists
            String cleanHost = host.replace("ftp://", "").replace("https://", "");
            
            System.out.println("🔗 FTP Sunucusuna bağlanıyor: " + cleanHost + ":" + port);
            ftpClient.connect(cleanHost, port);
            
            // Login
            if (!ftpClient.login(username, password)) {
                throw new IOException("FTP login başarısız: " + username);
            }
            System.out.println("✅ FTP'ye giriş yapıldı");
            
            // Set file type to binary
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            
            // Change to remote directory
            if (!ftpClient.changeWorkingDirectory(remotePath)) {
                throw new IOException("Klasöre erişilemiyor: " + remotePath);
            }
            System.out.println("📂 Klasör değiştirildi: " + remotePath);
            
            // Upload report.html
            String reportPath = localReportDir + "/report.html";
            uploadFile(ftpClient, reportPath, "report.html");
            
            // Create charts subdirectory if not exists
            if (!ftpClient.changeWorkingDirectory("charts")) {
                if (!ftpClient.makeDirectory("charts")) {
                    System.out.println("⚠️  charts klasörü oluşturulamadı, devam ediliyor...");
                } else {
                    ftpClient.changeWorkingDirectory("charts");
                    System.out.println("📁 charts klasörü oluşturuldu");
                }
            } else {
                System.out.println("📁 charts klasörüne giriliyor");
            }
            
            // Upload all chart files
            Path chartsDir = Paths.get(localReportDir, "charts");
            if (Files.exists(chartsDir)) {
                try (Stream<Path> stream = Files.list(chartsDir)) {
                    stream.filter(path -> path.toString().endsWith(".html"))
                          .forEach(path -> uploadFile(ftpClient, path.toString(), path.getFileName().toString()));
                }
                System.out.println("✅ Tüm chart dosyaları yüklendi");
            }
            
            System.out.println("✅ FTP yükleme tamamlandı!");
            
        } catch (IOException ex) {
            System.err.println("❌ FTP yükleme hatası: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try {
                if (ftpClient != null && ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                    System.out.println("🔌 FTP bağlantısı kapatıldı");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    private void uploadFile(FTPClient ftpClient, String localPath, String remoteFileName) {
        try {
            Path path = Paths.get(localPath);
            if (!Files.exists(path)) {
                System.out.println("⚠️  Dosya bulunamadı: " + localPath);
                return;
            }
            
            try (FileInputStream inputStream = new FileInputStream(localPath)) {
                if (ftpClient.storeFile(remoteFileName, inputStream)) {
                    System.out.println("📤 Yüklendi: " + remoteFileName);
                } else {
                    System.err.println("❌ Yükleme başarısız: " + remoteFileName);
                }
            }
        } catch (IOException ex) {
            System.err.println("❌ Dosya yükleme hatası (" + remoteFileName + "): " + ex.getMessage());
        }
    }
}
