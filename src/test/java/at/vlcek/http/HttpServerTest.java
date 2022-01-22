package at.vlcek.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.stream.Collectors;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import at.vlcek.http.HttpServer.HttpHandler;
import at.vlcek.http.HttpServer.HttpRequest;
import at.vlcek.http.HttpServer.HttpResponse;

public class HttpServerTest {

   @Test
   public void test() throws IOException, InterruptedException {
      final HttpServer server = new HttpServer(8089).addHandler(new HelloHandler());
      server.start();
      final URL url = new URL("http://localhost:8089/?name=Joe");
      final String result = new BufferedReader(new InputStreamReader(url.openStream())).lines()
            .collect(Collectors.joining("\r\n"));
      server.stop();
      Assert.assertThat(result, CoreMatchers.containsString("Hello Joe"));
   }

   private static final class HelloHandler implements HttpHandler {
      @Override
      public void handle(final HttpRequest request, final HttpResponse response) throws IOException {
         final String name = request.getParameter("name");
         final String html = new StringBuilder()
               .append("<html><body><p>Hello ")
               .append(name != null ? name : "World")
               .append("</p></body></html>")
               .toString();
         response.setContentType("text/html; charset=UTF-8");
         response.getOutputStream().write(html.getBytes());
      }
   }

}
