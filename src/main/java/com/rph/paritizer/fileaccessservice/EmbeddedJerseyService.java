package com.rph.paritizer.fileaccessservice;

import com.rph.paritizer.fileaccessservice.exceptions.NotDirectoryException;
import com.rph.paritizer.fileaccessservice.exceptions.NotReadableException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.eclipse.jetty.servlet.ServletContextHandler.NO_SESSIONS;

public class EmbeddedJerseyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedJerseyService.class);

    private static final String CONTEXT_PATH = "/FileAccessService";
    private static final String PATH_SPEC = "/api/*";
    private static final String REST_PACKAGE = "com.rph.paritizer.fileaccessservice";

    private static int portNumber = 0;

    public static void main(String[] args) throws Exception {
        new EmbeddedJerseyService().runServer(args);
    }

    private void runServer(String[] args)
            throws URISyntaxException, MalformedURLException,
            FileNotFoundException, NotDirectoryException, NotReadableException {
        processArgs(args);
        org.eclipse.jetty.server.Server server = new Server(portNumber);
        server.setStopAtShutdown(true);
        server.setStopTimeout(50);
        ServletContextHandler servletContextHandler = new ServletContextHandler(NO_SESSIONS);
        servletContextHandler.setContextPath(CONTEXT_PATH);
        HandlerList handlers = new HandlerList();
        handlers.addHandler(servletContextHandler);
        ServletHolder servletHolder = servletContextHandler.addServlet(ServletContainer.class, PATH_SPEC);
        servletHolder.setInitOrder(0);
        servletHolder.setInitParameter("jersey.config.server.provider.packages", REST_PACKAGE);

        URL url = EmbeddedJerseyService.class.getResource("/webapp/index.html");
        if (url == null) {
            throw new FileNotFoundException("Unable to find required /webapp/index.html");
        }
        URI uri = url.toURI();
        String uriString = uri.toString();
        uriString = uriString.substring(0, uriString.lastIndexOf('/'));
        URI baseUri = new URI(uriString);
        servletContextHandler.setBaseResource(Resource.newResource(baseUri));
        ServletHolder staticHolder = new ServletHolder("default", DefaultServlet.class);
        servletContextHandler.addServlet(staticHolder, "/");
        handlers.addHandler(new DefaultHandler());   // always last handler

        server.setHandler(handlers);
        try {
            server.start();
            LOGGER.info("File access service listening for requests at " + server.getURI());
            try {
                System.out.println("DefaultDisk: " + FileAccessor.getDefaultDiskTop());
            } catch (FileNotFoundException e) {
                // ignore
            }
            System.out.println("File access service listening for requests at " + server.getURI());
            System.out.println("For a good time, start with " + server.getURI() + "/api/fileAccessor/fileList");
            server.join();
        } catch (Exception ex) {
            try {
                LOGGER.error("main: Error occurred while starting Jetty: ", ex.getMessage());
                LOGGER.error("main: server exiting");
                server.destroy();
            } catch (Exception e) {
                // carry on
            }
            System.exit(1);
        }
        finally {
            if (server.isRunning()) {
                try {
                    server.stop();
                } catch (Exception e) {
                    // carry on
                }
            }
            try {
                server.destroy();
            } catch (Exception e) {
                // carry on
            }
            LOGGER.info("main: server exiting");
        }
    }

    private void processArgs(String[] args) throws FileNotFoundException, NotDirectoryException, NotReadableException {
        int n = 0;
        while (n < args.length) {
            String arg = args[n++];
            if ("-p".equals(arg) || "--port".equals(arg)) {
                if (n >= args.length) {
                    throw new IllegalArgumentException("missing port number");
                }
                EmbeddedJerseyService.setPortNumber(Integer.parseInt(args[n++]));
                continue;
            }
            if ("-d".equals(arg) || "--disk".equals(arg)) {
                if (n >= args.length) {
                    throw new IllegalArgumentException("missing disk parameters");
                }
                String diskName = args[n++];
                if (diskName.startsWith("-")) {
                    throw new IllegalArgumentException(diskName + ": illegal disk name, or missing disk name");
                }
                if (n >= args.length) {
                    throw new IllegalArgumentException("missing disk top directory");
                }
                String topDirectory = args[n++];
                if (topDirectory.startsWith("-")) {
                    throw new IllegalArgumentException(diskName + ": illegal top directory, or missing top directory");
                }
                FileAccessor.addNewDisk(diskName, topDirectory);
            }
        }
    }

    private static void setPortNumber(int pn) {
        portNumber = pn;
    }
}
