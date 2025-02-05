package eu.whitelistr.utils;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class Libraries {
    //Using Java Websocket version 1.6.1 Forked without SL4FJ
    //Using SQLite JDBC version 3.7.2 without SL4FJ
    private static final String LIB_DIR = "Whitelistr/libs";
    private static final String SQLITE_DRIVER_URL = "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.7.2/sqlite-jdbc-3.7.2.jar";
    private static final String WEB_SOCKET_URL = "https://github.com/Whitelistr/Java-WebSocket/releases/download/1.6.1-SNAPSHOT/Java-WebSocket-1.6.1-SNAPSHOT.jar";

    public static void loadLibraries() {
        try {
            File libDir = new File(LIB_DIR);
            if (!libDir.exists()) {
                libDir.mkdirs();
            }
            File sqliteFile = downloadJar(SQLITE_DRIVER_URL, new File(libDir, "sqlite-jdbc-3.7.2.jar"));
            File webSocketFile = downloadJar(WEB_SOCKET_URL, new File(libDir, "Java-WebSocket-1.6.1-SNAPSHOT.jar"));

            ClassLoader cl = net.minecraft.launchwrapper.Launch.classLoader;
            if (cl instanceof URLClassLoader) {
                Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURLMethod.setAccessible(true);
                addURLMethod.invoke(cl, sqliteFile.toURI().toURL());
                addURLMethod.invoke(cl, webSocketFile.toURI().toURL());
            } else {
                System.err.println("Class loader is not URLClassLoader. Libraries may not be loaded.");
            }

            System.out.println("Libraries loaded successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load libraries.", e);
        }
    }

    private static File downloadJar(String url, File outputFile) throws IOException {
        if (!outputFile.exists()) {
            System.out.println("Downloading " + outputFile.getName() + "...");
            try (InputStream in = new URL(url).openStream();
                 FileOutputStream out = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("Downloaded " + outputFile.getName() + " successfully.");
        }
        return outputFile;
    }
}
