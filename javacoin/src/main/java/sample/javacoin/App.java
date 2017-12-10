package sample.javacoin;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class App {

	public static void main(String[] args) throws Exception {

        Server server = new Server();
		int port = 5000;
		if(args.length > 0 && args[0]!=null){
			port = Integer.valueOf(args[0]);
		}
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("localhost");
        connector.setPort(port);
        connector.setIdleTimeout(30000);
        server.addConnector(connector);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        
        ServletHolder jerseyServlet = context.addServlet(
             org.glassfish.jersey.servlet.ServletContainer.class, "/*");
        jerseyServlet.setInitOrder(0);

        // Tells the Jersey Servlet which REST service/class to load.
        jerseyServlet.setInitParameter(
           "jersey.config.server.provider.classnames",
           EntryPoint.class.getCanonicalName());

        try {
            server.start();
            server.join();
        } finally {
            server.destroy();
        }
    }

 }

