package at.vlcek.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javax.net.ssl.SSLServerSocketFactory;

public class HttpServer {

   private final int port;

   private final boolean secure;

   private final AtomicBoolean running = new AtomicBoolean(false);

   private final List<HandlerMapping> mappings = new ArrayList<HttpServer.HandlerMapping>();

   private final Executor executor;

   public HttpServer(final int port) {
      this(port, false, null);
   }

   public HttpServer(final int port, final boolean secure) {
      this(port, secure, null);
   }

   public HttpServer(final int port, final Executor executor) {
      this(port, false, executor);
   }

   public HttpServer(final int port, final boolean secure, final Executor executor) {
      this.port = port;
      this.secure = secure;
      this.executor = executor;
   }

   public HttpServer addHandler(final HttpHandler handler) {
      this.mappings.add(new HandlerMapping(handler, null));
      return this;
   }

   public HttpServer addHandler(final HttpHandler handler, final Pattern urlPattern) {
      this.mappings.add(new HandlerMapping(handler, urlPattern));
      return this;
   }

   public void start() {
      if (this.running.compareAndSet(false, true)) {
         new Thread(this::run).start();
      }
   }

   private void run() {
      try (ServerSocket s = this.newServerSocket()) {
         s.setSoTimeout(1000);
         while (this.running.get() && !Thread.currentThread().isInterrupted()) {
            try {
               final Socket remote = s.accept();
               if (this.executor != null) {
                  this.executor.execute(() -> this.handle(remote));
               } else {
                  HttpServer.this.handle(remote);
               }
            } catch (final SocketTimeoutException e) {
               // ignore
            }
         }
      } catch (final Exception e) {
         this.logError(e.getMessage());
      }
   }

   protected ServerSocket newServerSocket() throws IOException {
      if (this.secure) {
         final SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
         return factory.createServerSocket(this.port);
      } else {
         return new ServerSocket(this.port);
      }
   }

   protected void handle(final Socket remote) {
      try (BufferedReader in = new BufferedReader(new InputStreamReader(remote.getInputStream()))) {
         String line = in.readLine();
         if (line == null) {
            throw new RuntimeException("No HTTP header!");
         }
         final String parts[] = line.split("\\s+");
         final String method = parts[0];
         String url = parts[1];
         final int qpos = url.indexOf('?');
         final Map<String, String[]> parameters;
         if (qpos >= 0) {
            parameters = this.getParameters(url.substring(qpos + 1));
            url = url.substring(0, qpos);
         } else {
            parameters = new HashMap<String, String[]>();
         }
         final Map<String, String[]> headers = new HashMap<String, String[]>();
         while ((line = in.readLine()) != null && !("".equals(line))) {
            final int pos = line.indexOf(':');
            if (pos > 0) {
               final String name = line.substring(0, pos).trim();
               final String value = line.substring(pos + 1).trim();
               headers.put(name, this.addString(headers.get(name), value));
            }
         }
         final HttpRequest request = new HttpRequest(method, url, parameters, headers, remote.getInputStream());
         final HttpResponse response = new HttpResponse(remote.getOutputStream());
         final HttpHandler handler = this.getHandler(url);
         if (handler != null) {

            handler.handle(request, response);
         }
         remote.getOutputStream().flush();
         remote.close();
      } catch (final Exception e) {
         this.logError(e.getMessage());
      }
   }

   public void stop() {
      this.running.set(false);
   }

   protected void logError(final String message) {
      System.err.println(message);
   }

   private String[] addString(final String[] values, final String value) {
      if (values != null) {
         final String[] newValues = Arrays.copyOf(values, values.length + 1);
         newValues[values.length] = value;
         return newValues;
      } else {
         return new String[] { value };
      }
   }

   private Map<String, String[]> getParameters(final String s) throws UnsupportedEncodingException {
      final Map<String, String[]> parameters = new HashMap<String, String[]>();
      for (final String param : s.split("&")) {
         final int pos = param.indexOf('=');
         if (pos >= 0) {
            final String name = URLDecoder.decode(param.substring(0, pos), "UTF-8");
            final String value = URLDecoder.decode(param.substring(pos + 1), "UTF-8");
            parameters.put(name, this.addString(parameters.get(name), value));
         }
      }
      return parameters;
   }

   private HttpHandler getHandler(final String url) {
      for (final HandlerMapping mapping : this.mappings) {
         if (mapping.pattern == null || mapping.pattern.matcher(url).matches()) {
            return mapping.handler;
         }
      }
      return null;
   }

   public static class HttpRequest {

      private final String method;

      private final String url;

      private final Map<String, String[]> parameters;

      private final Map<String, String[]> headers;

      private final InputStream is;

      private HttpRequest(final String method, final String url, final Map<String, String[]> parameters,
            final Map<String, String[]> headers, final InputStream is) {
         this.method = method;
         this.url = url;
         this.parameters = parameters != null ? parameters : new HashMap<String, String[]>();
         this.headers = headers != null ? headers : new HashMap<String, String[]>();
         this.is = is;
      }

      public String getMethod() {
         return this.method;
      }

      public String getUrl() {
         return this.url;
      }

      public Map<String, String[]> getParameters() {
         return this.parameters;
      }

      public String[] getParameters(final String name) {
         return this.parameters.get(name);
      }

      public String getParameter(final String name) {
         return this.parameters.containsKey(name) ? this.parameters.get(name)[0] : null;
      }

      public Map<String, String[]> getHeaders() {
         return this.headers;
      }

      public String[] getHeaders(final String name) {
         return this.headers.get(name);
      }

      public String getHeader(final String name) {
         return this.headers.containsKey(name) ? this.headers.get(name)[0] : null;
      }

      public InputStream getInputStream() {
         return this.is;
      }

   }

   public static class HttpResponse {

      private final OutputStream os;

      private final OutputStream wrapper;

      private int status = 200;

      private String statusText = "OK";

      private final Map<String, String[]> headers = new HashMap<String, String[]>();

      private boolean headersWritten = false;

      public HttpResponse(final OutputStream os) {
         this.os = os;
         this.wrapper = new HttpOutputStreamWrapper();
      }

      public void setStatus(final int status, final String statusText) {
         this.status = status;
         this.statusText = statusText;
      }

      public void setContentType(final String contentType) {
         this.setHeader("Content-Type", contentType);
      }

      public void setHeader(final String name, final String value) {
         this.headers.put(name, new String[] { value });
      }

      public void addHeader(final String name, final String value) {
         if (this.headers.containsKey(name)) {
            final String[] oldHeaders = this.headers.get(name);
            final String[] newHeaders = Arrays.copyOf(oldHeaders, oldHeaders.length + 1);
            newHeaders[oldHeaders.length] = value;
            this.headers.put(name, newHeaders);
         } else {
            this.headers.put(name, new String[] { value });
         }
      }

      public OutputStream getOutputStream() {
         return this.wrapper;
      }

      private void writeHeadersIfNotWritten() {
         if (!this.headersWritten) {
            final PrintWriter out = new PrintWriter(new OutputStreamWriter(this.os));
            out.println("HTTP/1.0 " + this.status + " " + this.statusText);
            for (final Entry<String, String[]> header : this.headers.entrySet()) {
               for (final String value : header.getValue()) {
                  out.println(header.getKey() + ": " + value);
               }
            }
            out.println();
            out.flush();
            this.headersWritten = true;
         }
      }

      private class HttpOutputStreamWrapper extends OutputStream {

         @Override
         public void flush() throws IOException {
            HttpResponse.this.writeHeadersIfNotWritten();
            HttpResponse.this.os.flush();
         }

         @Override
         public void write(final byte[] b) throws IOException {
            HttpResponse.this.writeHeadersIfNotWritten();
            HttpResponse.this.os.write(b);
         }

         @Override
         public void write(final byte[] b, final int off, final int len) throws IOException {
            HttpResponse.this.writeHeadersIfNotWritten();
            HttpResponse.this.os.write(b, off, len);
         }

         @Override
         public void write(final int b) throws IOException {
            HttpResponse.this.writeHeadersIfNotWritten();
            HttpResponse.this.os.write(b);
         }

      }

   }

   public interface HttpHandler {

      public void handle(final HttpRequest request, final HttpResponse response) throws Exception;

   }

   private static class HandlerMapping {

      private final HttpHandler handler;

      private final Pattern pattern;

      private HandlerMapping(final HttpHandler handler, final Pattern pattern) {
         this.handler = handler;
         this.pattern = pattern;
      }

   }

}
