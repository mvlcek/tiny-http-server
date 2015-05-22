# tiny-http-server
Single-class Java HTTP server

## Example

```java
  HttpServer server = new HttpServer(8080);
  server.addHandler(new HttpHandler() {
    @Override
    public void handle(final HttpRequest request, final HttpResponse response) {
      String name = request.getParameter("name");
      response.setContentType("text/html; charset=UTF-8");
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream()));
      pw.println("<html><body><h1>Hello " + (name != null ? name : "guest") + "!</h1></body></html>");
      pw.flush();
    }
  });
```
