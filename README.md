# tiny-http-server

A single-class single-threaded Java HTTP server that can be used for serving dynamic content in a Java application.

## Example

Here is a HelloWorld example:

```java
  HttpServer server = new HttpServer(8080);
  server.addHandler(new HttpHandler() {
    @Override
    public void handle(final HttpRequest request, final HttpResponse response) {
      String name = request.getParameter("name");
      response.setContentType("text/html; charset=UTF-8");
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), "UTF-8"));
      pw.println("<html><body><h1>Hello " + (name != null ? name : "World") + "!</h1></body></html>");
      pw.flush();
    }
  });
  server.start();
```
