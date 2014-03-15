/****************
 *Steven Bartfield and Rehan Balagamwala- MPCS 54001 - Project 1
 ****************/

import java.io.*;
import java.util.Scanner;
import java.net.*;
import java.security.*;
import javax.net.*;
import javax.net.ssl.*;
import java.lang.Exception;


public class HttpsServer extends Thread {

    public static boolean bSecure;

    public static void main(String[] args) throws Exception {

        //------------------------------------------------------------------------------
        //Setting up the sockets and defining variables
        //------------------------------------------------------------------------------
        String[] arrInputOne = args[0].split("=");            //parse out the port number
        String[] arrInputTwo = args[1].split("=");            //parse out the port number

        //SSL = [0], Regular = [1]
        String[] arrPortDetails = findPortNumbers(arrInputOne, arrInputTwo);

        //Runs the HTTPs server stuff.
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
        serverSocket = (SSLServerSocket) sslf.createServerSocket(Integer.parseInt(arrPortDetails[0]));

        ServerSocket httpServerSocket;
        httpServerSocket = new ServerSocket(Integer.parseInt(arrPortDetails[1]));


        //while (true) {
            //create Secure socket
            //System.out.println("first hop");
            //bSecure = true;
        //new HttpsServer(serverSocket).start();
        new HttpsServer(httpServerSocket).start();
        while (true){}

//          Thread s = new Thread(new HttpsServer(httpServerSocket.accept()));
//          s.start();
        //}


    }





    private Socket ssl;
    public HttpsServer(ServerSocket s) {
        //ssl = s;
        serverSocket = s;
    }
    public static ServerSocket serverSocket;

    public void run(){

        //------------------------------------------------------------------------------
        //Setting up the sockets and defining variables
        //------------------------------------------------------------------------------



        while (true){    //will continue to look for requests

            //ServerSocket serverSocket = new ServerSocket(Integer.parseInt(arrInput[1]));
            String strInput = "";
            boolean persistent = false;
            Socket persist = null;
            BufferedReader in = null;
            DataOutputStream out = null;

            Socket clientSocket;
            try{

                if(persistent)
                    //serverSocket.accept();
                    clientSocket = persist;
                else{
                    clientSocket = serverSocket.accept();
                }


                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new DataOutputStream(clientSocket.getOutputStream());

                //read in top line, if empty then wait for user request
                strInput = in.readLine();
            }
            catch(Exception e){}
            if (strInput.isEmpty()){ continue; }

            //------------------------------------------------------------------------------
            //Parsing out request and building the header file
            //------------------------------------------------------------------------------

            //Parse out the requested file from the client request string
            String strRequestType = strInput.substring(0,4); //pulls the request out
            String strPath = "www/";

            //check for request type and handle accordingly
            if (strRequestType.equals("GET ")){
                strPath += strInput.substring(5, strInput.indexOf("HTTP") - 1);
            }
            if (strRequestType.equals("HEAD")){
                strPath += strInput.substring(6, strInput.indexOf("HTTP") - 1);
            }

            //Create the header and send back to client
            String strHeader = createHeader(strPath, strRequestType);
            try{
                out.writeBytes(strHeader + "\r\n");
            }
            catch(Exception e){}
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
                    try{
                    out.writeBytes("404. That's an Error! The requested URL was not found on this server. That's all we know.\r\n"); //for 404 requests //find out what should actually be written in the body for 404s.. if anything?
                    }catch(Exception g){
                    }
            }

            //Remove the rest of client's header from the buffer
             try{
                 while ( (strInput = in.readLine()) != null){
                    //System.out.println(strInput); //printing out full request for debug purposes
    //                if (strHeader.contains("Connection: keep-alive")){
    //                    persistent = true;
    //                    persist = clientSocket;
    //                    continue;   //
                    if (strInput.equals("") ){ break;} //breaks once the current request has ended
                }
             }catch(Exception e){}

            //------------------------------------------------------------------------------
            //Closing off the current connection
            //------------------------------------------------------------------------------
            try{
                out.close();
                in.close();
                serverSocket.close();
            }catch(Exception e){}
        }
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
            strHeader += "Content-Length: 91 \r\n";
            strHeader += "Content-Type: text/html \r\n";
            return strHeader;
        }

        //for paths that exist(redirect to be coded in future)
        strHeader =  "HTTP/1.1 200 OK \r\n";
        strHeader += "Content-Length: " + fileRequested.length() + "\r\n";
        strHeader += "Content-Type: " + findContentType(strPathInput) + "\r\n";
        return strHeader;
    }


    //returns the content type file of the path request
    public static String findContentType(String strPathInput){
        String strContentType = ""; //initialize the variable
        if      (strPathInput.endsWith("html")){strContentType = "text/html";}
        else if (strPathInput.endsWith("txt")){strContentType = "text/plain";}
        else if (strPathInput.endsWith("jpeg")){strContentType = "image/jpeg";}
        else if (strPathInput.endsWith("png")){strContentType = "image/html";}
        else if (strPathInput.endsWith("pdf")){strContentType = "application/pdf";}
        else if (strPathInput.endsWith("exe")){strContentType = "application/octet-stream";} //need to look into
        return strContentType;
    }

    //Checks to see if requested path is a redirect, if so returns redirect path
    public static String findRedirect(String strPathInput){
        String strRedirectPath = "";
        Scanner scanRedirect;
        try {scanRedirect = new Scanner(new File("www/redirect.defs")); }
        catch(IOException e){
            System.out.println("redirect.defs file could not be found" + e.getMessage());
            return strRedirectPath; //send empty string (could not find file)
        }   //only works if server in root

        while (scanRedirect.hasNextLine()){
            String strRedirect = scanRedirect.nextLine();
            String[] strSplit = strRedirect.split(" ");
            if (strSplit[0].equals("/" + strPathInput)){    //added "/" as this is how file is formatted
                return strSplit[1];
            }
        }
        return strRedirectPath; //no redirect path
    }

    //Returns array of [SSLport#, ReguarlPort#]
    public static String[] findPortNumbers(String[] arrInputOne, String[] arrInputTwo){
        String[] arrPortDetails = new String[10];

        if (arrInputOne[0].equals("--sslServerPort")){
            arrPortDetails[0] = arrInputOne[1];
            arrPortDetails[1] = arrInputTwo[1];
        }
        else{  //must be the regular port number
            arrPortDetails[0] = arrInputTwo[1];
            arrPortDetails[1] = arrInputOne[1];
        }

        //if either port is null, assign port 4444, and 5555 accordingly
        if (arrPortDetails[0].equals("")){ arrPortDetails[0] = "4444";} //SSL Port
        if (arrPortDetails[1].equals("")){ arrPortDetails[0] = "5555";} //Regular Port

        return arrPortDetails;
    }


}


