package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.HttpVoiceServlet;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.*;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpVoiceEndpointTest {
    @Test void realHttpEndpointRequiresTokenAndRejectsCrossSiteAndOversizedRequests() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1"); connector.setPort(0); server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/voice"); server.setHandler(context);
        context.addServlet(new ServletHolder(new HttpVoiceServlet()), "/api/voice/*");
        try {
            server.start();
            String base = "http://127.0.0.1:" + connector.getLocalPort() + "/voice/api/voice/";
            var client = HttpClient.newHttpClient();
            var health = client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertEquals("https-poll-v1", new JSONObject(health.body()).getString("transport"));
            assertEquals("no-store", health.headers().firstValue("Cache-Control").orElse(""));
            assertTrue(health.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            assertEquals(401, post(client, base + "exchange", "{}", false).statusCode());
            assertEquals(401, post(client, base + "close", "{}", false).statusCode());
            assertEquals(415, post(client, base + "join", "{}", true).statusCode());
            assertEquals(400, post(client, base + "join", "not-json", false).statusCode());
            assertEquals(413, post(client, base + "join", "x".repeat(65537), false).statusCode());
            assertEquals(400, post(client, base + "join", new JSONObject().put("username", "x".repeat(65)).toString(), false).statusCode());
        } finally { server.stop(); }
    }
    private HttpResponse<String> post(HttpClient client, String url, String body, boolean crossSite) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        if (crossSite) request.header("Sec-Fetch-Site", "cross-site");
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
