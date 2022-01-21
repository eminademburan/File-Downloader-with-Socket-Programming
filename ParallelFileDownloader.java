package com.company;

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.Thread;




public class ParallelFileDownloader{

    public static String[] parseHostname(String input) {
        int charIndex = input.indexOf("/");
        String hostname = input.substring(0,charIndex);
        String path = input.substring(charIndex, input.length());
        /*String[] str = new String[2];
        str[0] = hostname;
        str[1] = path;*/
        return new String[] {hostname, path};
    }

    public static String getFileName(String input) {
        return input.substring(input.lastIndexOf("/")+1);
    }

    public static String readStreamAndSave(BufferedReader bfReader, FileWriter fileWriter) throws IOException {
        boolean start = false;
        String line;
        char c;
        String range = "";
        while ((line = bfReader.readLine()) != null) {
            //System.out.println(line);
            if(!start){
                if(line.equals(""))
                    start = true;
                if(line.contains("Content-Range"))
                    range = line.substring(20,line.indexOf("/"));
            }
            else
                fileWriter.write(line); //+ "\n");
        }
        return range;
    }

    public static void main(String[] args) throws IOException {
	
	System.clearProperty("http.proxyHost");
        //System.out.println(args.length);

        int connectionNumber = Integer.parseInt(args[1]);



        String str = args[0];
        String[] names = parseHostname(str);
        String hostname = names[0];
        String path = names[1];
        //System.out.println(hostname);
        //System.out.println(path);
        int port = 80;

        InetAddress address = InetAddress.getByName(hostname);
	
        Socket socket = new Socket(address, port);
        PrintWriter pw = new PrintWriter(socket.getOutputStream());


        // Send headers
        BufferedReader bfReader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        pw.print("GET "+ path + " HTTP/1.1\r\n");
        pw.print("Host: " + hostname + ":" + port + "\r\n");
        pw.print("Connection: Close\r\n");
        //System.out.println("range is " + range);
        pw.print("\r\n");
        pw.flush();

        String line;
        int i = 0;

        while ((line = bfReader.readLine()) != null) {

            if( i == 0 && !line.contains("200 OK")) {
                System.out.println("Error: the index file does not exist");
                System.exit(1);
            }
            else if( i == 0 && line.contains("200 OK")) {
                System.out.println("The file does exist. It will be returned.");
                System.out.println("URL of the index file: " + args[0]);
                System.out.println("The number of parallel connections: " + connectionNumber);
                i++;
            }
            if( line.equals(""))
                break;
        }

        System.out.println("The file content:");
        ArrayList<String> list = new ArrayList();
        while ((line = bfReader.readLine()) != null) {
            list.add(line);
            System.out.println(line);
        }
        System.out.println("Index file is downloaded.");
        System.out.println("There are " + list.size() + " files in the index.");
        bfReader.close();
        pw.close();

        int tempConnectionNumber = connectionNumber;

        for (int j = 0; j < list.size(); j++) {


            String s = list.get(j);
            String[] name = parseHostname(s);
            String obj_hostname = name[0];
            String obj_path = name[1];
            String filename = getFileName(s);

            InetAddress obj_address = InetAddress.getByName(obj_hostname);
            Socket obj_head_socket = new Socket(obj_address, port);
            PrintWriter obj_head_pw = new PrintWriter(obj_head_socket.getOutputStream());
            BufferedReader obj_head_bfReader = new BufferedReader(
                    new InputStreamReader(obj_head_socket.getInputStream()));
            obj_head_pw.print("HEAD "+ obj_path + " HTTP/1.1\r\n");
            obj_head_pw.print("Host: " + obj_hostname + ":" + port+"\r\n");
            obj_head_pw.print("Connection: keep-alive\r\n");
            obj_head_pw.print("\r\n");
            obj_head_pw.flush();
            String obj_head_line;
            int content_length = 0;
            boolean found = false;

            while ((obj_head_line = obj_head_bfReader.readLine()) != null) {
                if( (obj_head_line.contains("HTTP")) && ( obj_head_line.contains("200 OK"))) {
                    found = true;
                }
                if(obj_head_line.contains("Content-Length")) {
                    content_length = Integer.parseInt(obj_head_line.substring(obj_head_line.indexOf(":")+2));
                    //System.out.println("content length of this file is: " +content_length);
                    break;
                }
            }

            obj_head_pw.close();
            obj_head_bfReader.close();

            if(found) {
                String result = "";
                ArrayList<ThreadClass> thread_list = new ArrayList<>();
                if( content_length < tempConnectionNumber)
                    tempConnectionNumber = content_length;
                    if( content_length == 0)
                        tempConnectionNumber = 1;

                if (content_length % connectionNumber == 0)
                {
                    try {
                        for(int k = 0; k < tempConnectionNumber; k++) {
                            ThreadClass tc = new ThreadClass( obj_hostname, obj_path, k * ( content_length/tempConnectionNumber), ((k+1) *(content_length / tempConnectionNumber) - 1), k);
                            thread_list.add(tc);
                            tc.start();
                        }

                        for(int k = 0; k < tempConnectionNumber; k++) {
                            thread_list.get(k).join();
                        }

                        for(int k = 0; k < tempConnectionNumber; k++) {
                           result += thread_list.get(k).getString();
                        }

                        System.out.println( j+1 + ". The file with url " + s + "( " + content_length + " )" + "is downloaded.");
                        System.out.print("File parts: ");
                        for( int z = 0; z < tempConnectionNumber; z++)
                        {
                            if( z != tempConnectionNumber - 1)
                                System.out.print( thread_list.get(z).getRange()  + ", ");
                            else
                                System.out.println( thread_list.get(z).getRange() );
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    File new_file = new File(filename);
                    new_file.createNewFile();
                    FileWriter fileWriter = new FileWriter(filename);
                    fileWriter.write(result);
                    fileWriter.close();
                }
                else {
                    int zp = content_length % tempConnectionNumber;
                    int pp = content_length / tempConnectionNumber;

                    int tempLower = 0;
                    try {
                        for (int k = 0; k < tempConnectionNumber; k++) {
                            if (k < zp) {
                                ThreadClass tc = new ThreadClass(obj_hostname, obj_path, tempLower, tempLower + pp, k);
                                thread_list.add(tc);
                                tc.start();
                                tempLower = tempLower + pp + 1;
                            }
                            //son olmayan threadlerin indirme boyutu pp+1
                            else {
                                ThreadClass tc = new ThreadClass(obj_hostname, obj_path, tempLower, tempLower + pp - 1, k);
                                thread_list.add(tc);
                                tc.start();
                                tempLower = tempLower + pp;
                            }
                        }
                        for (int k = 0; k < tempConnectionNumber; k++) {
                            thread_list.get(k).join();
                        }


                        for (int k = 0; k < tempConnectionNumber; k++) {
                            result += thread_list.get(k).getString();
                        }

                        System.out.println( j+1 + ". The file with url " + s + "( " + content_length + " )" + "is downloaded.");
                        System.out.print("File parts: ");
                        for( int z = 0; z < tempConnectionNumber; z++)
                        {
                            if( z != tempConnectionNumber - 1)
                                System.out.print( thread_list.get(z).getRange()  + ", ");
                            else
                                System.out.println( thread_list.get(z).getRange() );
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    File new_file = new File(filename);
                    new_file.createNewFile();
                    FileWriter fileWriter = new FileWriter(filename);
                    fileWriter.write(result);
                    fileWriter.close();
                }
            }
            else
                System.out.println(j+1 + ". The file with url " + s + " is not found.");

                tempConnectionNumber = connectionNumber;
            }

        }

    }

class ThreadClass extends Thread {
    String result;
    int upper;
    String url;
    int lower;
    int id;
    String obj_hostname;
    String obj_path;
    String range;


    public ThreadClass(String obj_hostname, String obj_path, int lower, int upper, int id ){
        this.obj_hostname = obj_hostname;
        this.obj_path = obj_path;
        this.id = id;
        this.url = url;
        this.lower = lower;
        this.upper = upper;
        this.result = "";
        this.range = "";
    }

    public String getString() {
        return this.result;
    }

    public String getRange(){ range = lower + ":" + upper + "(" + (upper - lower + 1) + ")";  return this.range; }

    public void run(){

        int port = 80;
        String line = "";
        String range = "";

        try {
            InetAddress obj_address = InetAddress.getByName(obj_hostname);
            Socket obj_body_socket = new Socket(obj_address, port);
            PrintWriter obj_body_pw = new PrintWriter(obj_body_socket.getOutputStream());
            BufferedReader obj_body_bfReader = new BufferedReader(
                    new InputStreamReader(obj_body_socket.getInputStream()));
            obj_body_pw.print("GET "+ obj_path + " HTTP/1.1\r\n");
            obj_body_pw.print("Host: " + obj_hostname + ":" + port +"\r\n");
            obj_body_pw.print("Range: bytes=" + lower + "-" +upper +"\r\n");
            obj_body_pw.print("Connection: Close\r\n");
            obj_body_pw.print("\r\n");
            obj_body_pw.flush();
            boolean start = false;

            int valChar;
            StringBuilder stringBuilder = new StringBuilder();

            while ((line = obj_body_bfReader.readLine()) != null) {
                //System.out.println(line);
                if (!start) {
                    if (line.equals(""))
                        break;
                    if (line.contains("Content-Range"))
                        range = line.substring(20, line.indexOf("/"));
                }

            }

            while ( (valChar = obj_body_bfReader.read()) != -1 ) {
                stringBuilder.append((char) valChar);
            }

            result = stringBuilder.toString();
            obj_body_pw.close();
            obj_body_bfReader.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


