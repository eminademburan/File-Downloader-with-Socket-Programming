package com.company;

import java.io.*;
import java.net.*;
import java.util.*;

public class FileDownloader {
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
                fileWriter.write(line + "\n");
        }
        return range;
    }

    public static void main(String[] args) throws IOException {

        //System.out.println(args.length);

        int lower = 0;
        int upper = 0;
        boolean range_available = false;
        String  range= "";

        if( args.length == 1) {
            System.out.println("No range is given");
            //System.out.println(args[0]);
        }
        else if( args.length == 2) {
            try {

                range = args[1];
                int s = args[1].indexOf("-");
                //System.out.println(args[1].substring(0, s));
                //System.out.println(args[1].substring(s+1, args[1].length()));
                lower = Integer.parseInt(args[1].substring(0, s));
                upper = Integer.parseInt(args[1].substring(s + 1, args[1].length()));

            }
            catch ( NumberFormatException ex){
                ex.printStackTrace();
            }
            System.out.println("upper pound is " + upper + " lower bound is "+ lower);
        }

        if( args.length == 2)
            range_available= true;
        else
            range_available = false;

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
        pw.println("GET "+ path + " HTTP/1.1");
        pw.println("Host: " + hostname + ":" + port);
        pw.println("Connection: Close");
        //System.out.println("range is " + range);
        pw.println();
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
            obj_head_pw.println("HEAD "+ obj_path + " HTTP/1.1");
            obj_head_pw.println("Host: " + obj_hostname + ":" + port);
            obj_head_pw.println("Connection: keep-alive");
            obj_head_pw.println();
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
                //System.out.println("content length " + content_length + "lower is " + lower);

                if(range_available && content_length <= lower) {
                    System.out.println(j+1 + ": The file with url " +  s + " (size = " + content_length + ") is not downloaded.");
                    continue;
                }
                Socket obj_socket = new Socket(obj_address, port);
                PrintWriter obj_pw = new PrintWriter(obj_socket.getOutputStream());
                BufferedReader obj_bfReader = new BufferedReader(
                        new InputStreamReader(obj_socket.getInputStream()));
                File new_file = new File(filename);
                new_file.createNewFile();
                FileWriter n_filewriter = new FileWriter(filename);

                if(range_available) {
                    obj_pw.println("GET "+ obj_path + " HTTP/1.1");
                    obj_pw.println("Host: " + obj_hostname + ":" + port);
                    obj_pw.println("Range: bytes=" + range);
                    obj_pw.println("Connection: Close");
                    obj_pw.println();
                    obj_pw.flush();

                    String downloaded_range = readStreamAndSave(obj_bfReader, n_filewriter);
                    n_filewriter.close();
                    obj_bfReader.close();
                    obj_pw.close();
                    System.out.println(j+1 + ": The file with url " +  s + " (range = " + downloaded_range + ") is downloaded.");
                }
                else {
                    obj_pw.println("GET "+ obj_path + " HTTP/1.1");
                    obj_pw.println("Host: " + obj_hostname + ":" + port);
                    obj_pw.println("Connection: Close");
                    obj_pw.println();
                    obj_pw.flush();

                    readStreamAndSave(obj_bfReader, n_filewriter);

                    System.out.println(j+1 + ": The file with url " +  s + " (size = " + content_length + ") is downloaded.");
                    n_filewriter.close();
                    obj_bfReader.close();
                    obj_pw.close();
                }
            }
            else
                System.out.println(j+1 + ". The file with url " + s + " is not found.");
        }

    }
}

