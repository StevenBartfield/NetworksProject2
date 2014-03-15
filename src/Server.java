/****************
 *Steven Bartfield and Rehan Balagamwala- MPCS 54001 - Project 1
 ****************/

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {

    public static void main(String[] args) throws Exception {

        //------------------------------------------------------------------------------
        //Setting up the sockets and defining variables
        //------------------------------------------------------------------------------
        String[] arrInput = args[0].split("=");            //parse out the port number

        while (true){    //will continue to look for requests

            ServerSocket serverSocket = new ServerSocket(Integer.parseInt(arrInput[1]));

            boolean persistent = false;
            Socket persist = null;

            Socket clientSocket;
            clientSocket = serverSocket.accept();


            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            //read in top line, if empty then wait for user request
            String strInput = in.readLine();
            if (strInput.isEmpty()){ continue; }

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
                    out.writeBytes("404. That's an Error! The requested URL was not found on this server. That's all we know.\r\n"); //for 404 requests //find out what should actually be written in the body for 404s.. if anything?
                }
            }

            //Remove the rest of client's header from the buffer
            while ( (strInput = in.readLine()) != null){
                //System.out.println(strInput); //printing out full request for debug purposes
                if (strHeader.contains("Connection: keep-alive")){
                    persistent = true;
                    persist = clientSocket;
                    continue;
                }


                if (strInput.equals("") ){ break;} //breaks once the current request has ended
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

}
