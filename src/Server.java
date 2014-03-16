import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.security.*;
import javax.net.*;
import javax.net.ssl.*;

public final class Server {
    private final int serverPort;
    private Map<String, byte[]> resourceMap;
    private Map<String, String> redirectMap;
    private ServerSocket socket;
    private DataOutputStream toClientStream;
    private DataInputStream fromClientStream;

    public Server(int serverPort) {
        this.serverPort = serverPort;
    }

    public void loadResources() throws IOException {
        resourceMap = ResourceMap.loadFiles();
        redirectMap = ResourceMap.loadRedirects();
    }

    /**
     * Creates a socket + binds to the desired server-side port #.
     *
     * @throws {@link IOException} if the port is already in use.
     */
    public void bind() throws IOException {
        socket = new ServerSocket(serverPort);
        System.out.println("Server bound and listening to port " + serverPort);
    }

    public void bindSSL() throws IOException {
        try {
            SSLContext sslc = SSLContext.getInstance("TLS");
            char [] pswd = "testing".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fin = new FileInputStream("server.jks");
            ks.load(fin,pswd);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, pswd);
            sslc.init(kmf.getKeyManagers(), null, null);
            SSLServerSocketFactory sslf = sslc.getServerSocketFactory();
            SSLServerSocket serverSocket;
            socket = (SSLServerSocket) sslf.createServerSocket(serverPort);
            System.out.println("Server bound and listening to port " + serverPort);
        } catch (Exception e) {
            System.out.println("Exception");
        }
    }

    /**
     * Waits for a client to connect, and then sets up stream objects for communication
     * in both directions.
     *
     * @return The newly-created client {@link Socket} if the connection is successfully
     *     established, or {@code null} otherwise.
     * @throws {@link IOException} if the server fails to accept the connection.
     */
    public Socket acceptFromClient() throws IOException {
        Socket clientSocket;
        try {
            clientSocket = socket.accept();


        } catch (SecurityException e) {
            System.out.println("The security manager intervened; your config is very wrong. " + e);
            return null;
        } catch (IllegalArgumentException e) {
            System.out.println("Probably an invalid port number. " + e);
            return null;
        } catch (IOException e) {
            System.out.println("IOException in socket.accept()");
            return null;
        }

        try {
            toClientStream = new DataOutputStream(clientSocket.getOutputStream());
            fromClientStream = new DataInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            System.out.println("exception creating the stream objects.");
        }
        return clientSocket;
    }

    public void handleRequest() throws IOException {
        List<String> rawRequest = new ArrayList<String>();

        String inputLine;
        do {
            inputLine = fromClientStream.readLine();

            //if (inputLine == null) {
            //	System.out.println("inputLine was null!\n");
            //		break;
            //	}

            rawRequest.add(inputLine);

        } while ((inputLine != null) && (inputLine.length() > 0));

        System.out.println(String.format("[%s]", rawRequest));
        HTTPRequest request = new HTTPRequest(rawRequest);
        System.out.println(request);





        // TODO(ajn): support POST along with GET/HEAD
        if (request.getType() != HTTPRequest.Command.GET &&
                request.getType() != HTTPRequest.Command.HEAD) {
            send403(request, String.format("%s not supported.", request.getType()));
            return;
        }

        // See if this is supposed to be a redirect, first.
        if (redirectMap.containsKey(request.getPath())) {
            send301(request, redirectMap.get(request.getPath()));
        } else if (!resourceMap.containsKey(request.getPath())) {
            send404(request);
        } else {
            byte[] content = resourceMap.get(request.getPath());
            send200(request, content);
        }
    }

    private void send301(HTTPRequest request, String newUrl) throws IOException {
        String responseBody = new StringBuilder()
                .append("<HTML><HEAD><TITLE>301 Moved</TITLE></HEAD>\r\n")
                .append("<BODY><H1>These aren't the droids you're looking for.</H1>\r\n")
                .append(String.format("This resource has moved <A HREF=\"%s\">here</A>.\r\n", newUrl))
                .append("</BODY></HTML>\r\n")
                .toString();

        StringBuilder response = new StringBuilder()
                .append("HTTP/1.1 301 Moved Permanently\r\n")
                .append(String.format("Location: %s\r\n", newUrl))
                .append(String.format("Content-Type: text/html; charset=UTF-8\r\n"))
                .append("Connection: close\r\n")
                .append(String.format("Content-Length: %d\r\n", responseBody.length()));
        if (request.getType() == HTTPRequest.Command.GET) {
            response.append(String.format("\r\n%s", responseBody));
        }
        toClientStream.writeBytes(response.toString());
    }

    private void send404(HTTPRequest request) throws IOException {
        String responseBody = new StringBuilder()
                .append("<HTML><HEAD><TITLE>404 Not Found</TITLE></HEAD>\r\n")
                .append("<BODY><H1>I can't find any resource of the name \r\n")
                .append(String.format("[%s] on this server.\r\n", request.getPath()))
                .append("</BODY></HTML>\r\n")
                .toString();

        StringBuilder response = new StringBuilder()
                .append("HTTP/1.1 404 Not Found\r\n")
                .append("Content-Type: text/html; charset=UTF-8\r\n")
                .append("Connection: keep-alive\r\n")
                .append(String.format("Content-Length: %d\r\n", responseBody.length()));
        if (request.getType() == HTTPRequest.Command.GET) {
            response.append(String.format("\r\n%s\r\n", responseBody));
        }
        try {
            toClientStream.writeBytes(response.toString());
        } catch (IOException e) {
            System.out.println("Client closed the socket before we finished the whole message.");
        }
    }

    private void send403(HTTPRequest request, String errorDetail) throws IOException {
        StringBuilder response = new StringBuilder()
                .append("HTTP/1.1 403 Forbidden\r\n")
                .append("Connection: keep-alive\r\n")
                .append(String.format("Context-Length: %d\r\n", errorDetail.length()));
        if (request.getType() == HTTPRequest.Command.GET) {
            response.append(String.format("\r\n%s\r\n", errorDetail));
        }
        toClientStream.writeBytes(response.toString());
    }

    private void send200(HTTPRequest request, byte[] content) throws IOException {
        StringBuilder response = new StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: text/html; charset=utf-8\r\n")
                .append("Server: project1\r\n")
                .append("Connection: keep-alive\r\n")
                .append(String.format("Content-Length: %d\r\n", content.length));
        toClientStream.writeBytes(response.toString());
        if (request.getType() == HTTPRequest.Command.GET) {
            toClientStream.writeBytes("\r\n");
            ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
            outByteStream.write(content, 0, content.length);
            outByteStream.writeTo(toClientStream);
        }
    }

    public static void main(String argv[]) {
        Map<String, String> flags = Utils.parseCmdlineFlags(argv);
        if (!flags.containsKey("--serverPort")) {
            System.out.println("usage: Server --serverPort=12345");
            System.exit(-1);
        }

        int serverPort = -1;
        try {
            serverPort = Integer.parseInt(flags.get("--serverPort"));
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number! Must be an integer.");
            System.exit(-1);
        }

        Server server = new Server(serverPort);
        try {
            server.loadResources();

            //server.bindSSL();
            server.bind();


            while(true) {

                System.out.println("pop");
                Socket clientSocket = server.acceptFromClient();
                //clientSocket.setKeepAlive(true);
                //clientSocket.setSoTimeout(2000000);
                System.out.println("hop");

                if (clientSocket != null && clientSocket.isConnected()) {
                    try {
                        server.handleRequest();
                    } catch (IOException e) {
                        System.out.println("IO exception handling request, continuing.");
                    }

                    System.out.println("slop");
                    try {
                        server.handleRequest();
                    } catch (IOException e) {
                        System.out.println("IO exception handling request, continuing.");
                    }
                    System.out.println("flop");
//                    try {
//                        clientSocket.close();
//                    } catch (IOException e) {
//                        System.out.println("it's ok; the server already closed the connection.");
//                    }
                }
//
//              while (clientSocket.getSoTimeout() > 0){
//                  server.handleRequest();
//              }
                //server.handleRequest();

            }
        } catch (IOException e) {
            System.out.println("Error communicating with client. aborting. Details: " + e);
        }
    }
}

