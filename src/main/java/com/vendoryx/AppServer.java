package com.vendoryx;

import com.vendoryx.config.EnvConfig;
import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;

public class AppServer {

    public static void main(String[] args) throws Exception {
        EnvConfig.validateRequired();

        int port = EnvConfig.port();
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setHostname("0.0.0.0");

        Connector connector = tomcat.getConnector();
        connector.setProperty("address", "0.0.0.0");

        File webappDir = resolveWebappDir();
        File classesDir = resolveClassesDir();

        if (!webappDir.isDirectory()) {
            throw new IllegalStateException("Webapp directory not found: " + webappDir.getAbsolutePath());
        }

        Context context = tomcat.addWebapp("", webappDir.getAbsolutePath());

        if (classesDir.isDirectory()) {
            WebResourceRoot resources = new StandardRoot(context);
            resources.addPreResources(new DirResourceSet(
                    resources,
                    "/WEB-INF/classes",
                    classesDir.getAbsolutePath(),
                    "/"
            ));
            context.setResources(resources);
        }

        tomcat.start();
        System.out.println("========================================");
        System.out.println(" Vendoryx backend is running");
        System.out.println(" http://0.0.0.0:" + port);
        System.out.println(" GET  /health");
        System.out.println(" Auth: Bearer JWT on /api/* (except login)");
        System.out.println("========================================");
        tomcat.getServer().await();
    }

    private static File resolveWebappDir() {
        String fromEnv = System.getenv("VENDORYX_WEBAPP_DIR");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return new File(fromEnv.trim());
        }
        File container = new File("/app/webapp");
        if (container.isDirectory()) {
            return container;
        }
        return new File("src/main/webapp");
    }

    private static File resolveClassesDir() {
        String fromEnv = System.getenv("VENDORYX_CLASSES_DIR");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return new File(fromEnv.trim());
        }
        File container = new File("/app/classes");
        if (container.isDirectory()) {
            return container;
        }
        return new File("target/classes");
    }
}
