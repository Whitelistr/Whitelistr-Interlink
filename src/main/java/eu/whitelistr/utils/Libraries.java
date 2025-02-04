package eu.whitelistr.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class Libraries {

    private static final String LIB_DIR = "Whitelistr/libs";
    private static final String SQLITE_DRIVER_URL = "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.44.1.0/sqlite-jdbc-3.44.1.0.jar";
    private static final String SLF4J_API_URL = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar";
    private static final String SLF4J_SIMPLE_URL = "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar";
    private static final String WEB_SOCKET_URL = "https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.6.0/Java-WebSocket-1.6.0.jar";

    public static void loadLibraries() {
        try {
            File libDir = new File(LIB_DIR);
            if (!libDir.exists()) {
                libDir.mkdirs();
            }

            File sqliteFile = downloadJar(SQLITE_DRIVER_URL, new File(libDir, "sqlite-jdbc-3.44.1.0.jar"));
            File slf4jApiFile = downloadJar(SLF4J_API_URL, new File(libDir, "slf4j-api-2.0.13.jar"));
            File slf4jSimpleFile = downloadJar(SLF4J_SIMPLE_URL, new File(libDir, "slf4j-simple-2.0.13.jar"));
            File webSocketFile = downloadJar(WEB_SOCKET_URL, new File(libDir, "Java-WebSocket-1.6.0.jar"));

            ClassLoader cl = Libraries.class.getClassLoader();
            if (cl instanceof URLClassLoader) {
                Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURLMethod.setAccessible(true);
                addURLMethod.invoke(cl, sqliteFile.toURI().toURL());
                addURLMethod.invoke(cl, slf4jApiFile.toURI().toURL());
                addURLMethod.invoke(cl, slf4jSimpleFile.toURI().toURL());
                addURLMethod.invoke(cl, webSocketFile.toURI().toURL());

            } else {
                System.err.println("Current class loader is not an instance of URLClassLoader. " +
                    "External libraries might not be injected correctly.");
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
