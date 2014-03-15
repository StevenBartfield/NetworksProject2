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


        while (true) {
            //create Secure socket
            //System.out.println("first hop");
            //bSecure = true;
            new HttpsServer(serverSocket.accept()).start();
            //System.out.println("second hop");


            //create regular socket
            //bSecure = false;
            //new HttpsServer(httpServerSocket.accept()).start();
  	    }
    }

	private Socket ssl;
    public HttpsServer(Socket s) {
        ssl = s;
    }

	public void run() {

        try{

//            System.out.println("port = " + ssl.getLocalPort());
//            //if secure in incorrect port (7777 is insecure), then return
//            if (ssl.getLocalPort() == 7777 && bSecure ){
//                System.out.println("Exit port 7777");
//                return;
//            }
//            //if insecure in incorrect port (8888 is secure), then return
//            if (ssl.getLocalPort() == 8888 && !bSecure){
//                System.out.println("Exit port 8888");
//                return;
//            }

            BufferedReader in = new BufferedReader(new InputStreamReader(ssl.getInputStream()));
            DataOutputStream out = new DataOutputStream(ssl.getOutputStream());

            //read in top line, if empty then wait for user request
            String strInput = in.readLine();
            //if (strInput.isEmpty()){
	        //	continue; }


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

            //Create the header and send back to client
            String strHeader = createHeader(strPath, strRequestType);
            out.writeBytes(strHeader + "\r\n");


            //------------------------------------------------------------------------------
            //Handling the rest of the file request -- will fail if there is no file to be transmitted (404 was delivered)
            //------------------------------------------------------------------------------
            //only retrieve if a GET file and not a redirect
            System.out.println(strHeader);
            System.out.println(strHeader.substring(9,12));
            if (strRequestType.equals("GET ") && (!strHeader.substring(9,12).equals("301")))    {
                try{
                    //get the file per the request and input it into the file stream
                    File fileRequested = new File(strPath);
                    FileInputStream fileOutbound = new FileInputStream(fileRequested);

                    //preparing the file to be transmitted
                    int nByteSize = (int) fileRequested.length();  //find the number bytes in the file
                    byte[] bytFile = new byte[nByteSize];  //putting the file into bytes
                    fileOutbound.read(bytFile);  //read the bytes into a new file to be sent out

                    if (strHeader.substring(9,12).equals("404")){throw new Exception();}

                    //Transmit the data to client
                    out.write(bytFile, 0, nByteSize);
                }catch(Exception e){
                    out.writeBytes("404. That's an Error! The requested URL was not found on this server. That's all we know.\r\n"); //for 404 requests //find out what should actually be written in the body for 404s.. if anything?
                }
            }

            //Remove the rest of client's header from the buffer
            while ( (strInput = in.readLine()) != null){
                if (strHeader.contains("Connection: keep-alive")){
                    continue;
                }
                if (strInput.equals("") ){ break;} //breaks once the current request has ended
            }

            //------------------------------------------------------------------------------
            //Closing off the current connection
            //------------------------------------------------------------------------------

            out.close();
            in.close();
            ssl.close();
	  } catch (IOException ioe) {
                        //close connection
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
        if (!fileRequested.exists() || strPathInput.equals("www/redirect.defs")){
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
        //System.out.println(strPathInput); //for debugging

        try{strPathInput = strPathInput.subSequence(3,strPathInput.length()).toString();}
        catch  (Exception e){}

        while (scanRedirect.hasNextLine()){
            String strRedirect = scanRedirect.nextLine();
            String[] strSplit = strRedirect.split(" ");

            System.out.println(strPathInput);
            System.out.println(strSplit[0]);
            if (strSplit[0].equals(strPathInput)){    //added "/" as this is how file is formatted
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
