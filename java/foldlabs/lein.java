package foldlabs;


import com.google.common.collect.ImmutableSet;

import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static java.nio.file.attribute.PosixFilePermission.*;

public class lein {

    static String port = System.getProperty("coloneltrain.port", "3000");

    public static void main(String[] args) throws IOException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");

        try {

            System.out.print("downloading lein script...");
            Path leinScript = download(Paths.get("lein" + (isWindows ? ".bat" : "")), "https://raw.github.com/technomancy/leiningen/stable/bin/lein" + (isWindows ? ".bat" : ""));
            System.out.println("done");

            Path leinjar = Paths.get("leiningen-2.3.2-standalone.jar");
            if (!leinjar.toFile().exists()) {
                System.out.print("downloading lein uberjar...");
                leinjar = download(leinjar, "https://leiningen.s3.amazonaws.com/downloads/leiningen-2.3.2-standalone.jar");
                System.out.println("done");
            } else {
                System.out.println("uberjar exists, skipping download");
            }

            if (!isWindows)
                Files.setPosixFilePermissions(leinScript, ImmutableSet.of(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE));

            System.out.print("updating dependencies ...");
            run(leinScript, leinjar, "deps");
            System.out.println("done");

            System.out.print("running app on port "+port +"...");
            run(leinScript, leinjar, "run");
            System.out.println("done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Path download(Path path, String uri) throws IOException {
        InputStream lein = ClientBuilder.newClient().target(uri).request().get(InputStream.class);
        Files.copy(lein, path, StandardCopyOption.REPLACE_EXISTING);
        return path;
    }

    private static void run(Path lein1, Path leinjar, String target) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(lein1.toAbsolutePath().toString(), target).redirectErrorStream(true);
        Map<String,String> environment = processBuilder.environment();
        environment.put("LEIN_JAR", leinjar.toAbsolutePath().toString());
        environment.put("PORT", port);
        Process install = processBuilder.start();

        Thread pump = pump(install.getInputStream());
        pump.start();
        install.waitFor();
        pump.join();
    }

    private static Thread pump(final InputStream in) {
        return new Thread() {
            @Override
            public void run() {
                try {
                    byte[] buffer = new byte[1024];
                    int ln;
                    while ((ln = in.read(buffer)) > -1) {
                        System.out.write(buffer, 0, ln);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
