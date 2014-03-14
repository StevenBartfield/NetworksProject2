/****************
 *Steven Bartfield and Rehan Balagamwala- MPCS 54001 - Project 1
 ****************/

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Scanner;

public class Server {

    public static void main(String[] args) throws Exception {

        //------------------------------------------------------------------------------
        //Setting up the sockets and defining variables
        //------------------------------------------------------------------------------
        String[] arrInput = null;
        String[] arrInputSecure = null;

        //http = 80, https = 443 usually..

        try{
            arrInput = args[0].split("=");             //parse out the regular port number
            //arrInputSecure = args[1].split("=");             //parse out the SSL port number
        }catch(Exception e){
            System.out.println(e.getStackTrace());
        }


        while (true){    //will continue to look for requests

            BufferedReader in = null;
            ServerSocket serverSocket = null;
            DataOutputStream out = null;
            Socket clientSocket = null;

            //if request is https, then do this
            try{
                SSLContext sslc = SSLContext.getInstance("TLS");
                char [] pswd = "testing".toCharArray();
                KeyStore ks = KeyStore.getInstance("JKS");
                FileInputStream fin = new FileInputStream("server.jks");
                ks.load(fin,pswd);
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, pswd);
                sslc.init(kmf.getKeyManagers(), null, null);
                SSLServerSocketFactory sslf = sslc.getServerSocketFactory();
                SSLServerSocket sslServerSocket;
                sslServerSocket = (SSLServerSocket) sslf.createServerSocket(Integer.parseInt(arrInput[1]));

                Socket ssl = sslServerSocket.accept();

                in = new BufferedReader(new InputStreamReader(ssl.getInputStream()));
                out = new DataOutputStream(ssl.getOutputStream());

                System.out.println(ssl.getInputStream());


            }catch (Exception e){

                serverSocket = new ServerSocket(Integer.parseInt(arrInput[1]));
                clientSocket = serverSocket.accept();
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new DataOutputStream(clientSocket.getOutputStream());
            }



            //read in top line, if empty then wait for user request
            String strInput = in.readLine();
            if (strInput.isEmpty()){ continue; }

            System.out.println(strInput);

            //------------------------------------------------------------------------------
            //Parsing out request and building the header file
            //------------------------------------------------------------------------------

            //Parse out the requested file from the client request string
            String strRequestType = strInput.substring(0,4); //pulls the request out
            String strPath = null;

            //check for request type and handle accordingly
            if (strRequestType.equals("GET ")){
                strPath = strInput.substring(5, strInput.indexOf("HTTP") - 1);
            }
            if (strRequestType.equals("HEAD")){
                strPath = strInput.substring(6, strInput.indexOf("HTTP") - 1);
            }

            //FOR TEST PURPOSES ONLY - Too shutdown server without killing port ("http....edu/####/QUIT")
            if (strPath.equals("QUIT")){
                System.err.println("Connection terminated");
                out.close();
                in.close();
                clientSocket.close();
                serverSocket.close();
                return;
            }

            //Create the header and send back to client
            String strHeader = createHeader(strPath, strRequestType);
            out.writeBytes(strHeader + "\r\n");

            //------------------------------------------------------------------------------
            //Handling the rest of the file request -- will fail if there is no file to be transmitted (404 was delivered)
            //------------------------------------------------------------------------------

            //only retrieve if a GET file and not a redirect
            if (strRequestType.equals("GET ") && (!strHeader.substring(9,12).equals("301")))    {
                try{
                    //get the file per the request and input it into the file stream
                    File fileRequested = new File(strPath);
                    FileInputStream fileOutbound = new FileInputStream(fileRequested);

                    //preparing the file to be transmitted
                    int nByteSize = (int) fileRequested.length();  //find the number bytes in the file
                    byte[] bytFile = new byte[nByteSize];  //putting the file into bytes
                    fileOutbound.read(bytFile);  //read the bytes into a new file to be sent out

                    //Transmit the data to client
                    out.write(bytFile, 0, nByteSize);
                }catch(Exception e){
                    out.writeBytes("No File!\r\n"); //for 404 requests //find out what should actually be written in the body for 404s.. if anything?
                }
            }

            //Remove the rest of client's header from the buffer
            while ( (strInput = in.readLine()) != null){
                System.out.println(strInput); //printing out full request for debug purposes
            }

            //------------------------------------------------------------------------------
            //Closing off the current connection
            //------------------------------------------------------------------------------

            out.close();
            in.close();
            serverSocket.close();
        }
    }

    //------------------------------------------------------------------------------
    //Methods to help build appropriate headers
    //------------------------------------------------------------------------------

    //Method creates the header for the inputted path request
    public static String createHeader(String strPathInput, String strRequestType){
        String strHeader; //create string variable

        //if not valid request (i.e. POST) send 403 error
        if (strRequestType.equals("POST")){
            strHeader =  "HTTP/1.1 403 Forbidden \r\n";
            return strHeader;
        }

        //If invalid request, send 501 error
        if (!strRequestType.equals("GET ") && !strRequestType.equals("HEAD")){
            strHeader =  "HTTP/1.1 501 Not Implemented \r\n";
            return strHeader;
        }

        File fileRequested = new File(strPathInput); //gets file requested to find length

        //check if should redirect request
        String strRedirectPath = findRedirect(strPathInput);
        if (!strRedirectPath.isEmpty()){
            strHeader =  "HTTP/1.1 301 Moved Permanetly \r\n";
            strHeader += "Location: " + strRedirectPath + "\r\n";
            return strHeader;
        }

        //check if file exists, if not then send 404
        if (!fileRequested.exists()){
            strHeader =  "HTTP/1.1 404 Not Found \r\n";
            strHeader += "Content-Length: 10 \r\n";   //MUST CORRESPOND TO WHAT EVER IS SENT IN CATCH CLAUSE -- ADJUST ACCORDINGLY
            strHeader += "Content-Type: text/html \r\n";  //does this even matter?
            return strHeader;
        }

        //for paths that exist (redirect to be coded in future)
        strHeader =  "HTTP/1.1 200 OK \r\n";
        strHeader += "Content-Length: " + fileRequested.length() + "\r\n";
        strHeader += "Content-Type: " + findContentType(strPathInput) + "\r\n";
        strHeader += "Connection: Keep-Alive" + "\r\n";
        return strHeader;
    }


    //returns the content type file of the path request
    public static String findContentType(String strPathInput){
        String strContentType = ""; //initialize the variable
        if      (strPathInput.endsWith("html")){strContentType = "text/html";}
        else if (strPathInput.endsWith("txt")){strContentType = "text/plain";}
        else if (strPathInput.endsWith("jpeg")){strContentType = "image/jpeg";}  //do we need to do jpg?
        else if (strPathInput.endsWith("png")){strContentType = "image/html";}
        else if (strPathInput.endsWith("pdf")){strContentType = "application/pdf";}
        else if (strPathInput.endsWith("exe")){strContentType = "application/octet-stream";} //need to look into
        return strContentType;
    }

    //Checks to see if requested path is a redirect, if so returns redirect path
    public static String findRedirect(String strPathInput){
        String strRedirectPath = "";
        Scanner scanRedirect = null;
        try {scanRedirect = new Scanner(new File("www/redirect.defs")); }
        catch(IOException e){System.out.println("redirect.defs file could not be found" + e.getMessage());}   //only works if server in root

        while (scanRedirect.hasNextLine()){
            String strRedirect = scanRedirect.nextLine();
            String[] strSplit = strRedirect.split(" ");
            if (strSplit[0].equals("/" + strPathInput)){    //added "/" as this is how file is formatted
                return strSplit[1];
            }
        }
        return strRedirectPath; //no redirect path
    }

}