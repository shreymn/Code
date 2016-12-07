package udpClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class Client {

    private static final int C = 7919;
    private static final int D = 65536;
    private static final String REQUEST_TAG = "<request>";
    private static final String REQUEST_CLOSED_TAG = "</request>";
    private static final String RESPONSE_CLOSED_TAG = "</response>";
    private static final String CODE_TAG = "<code>";
    private static final String ID_TAG = "<id>";
    private static final String ID_CLOSED_TAG = "</id>";
    private static final String CODE_CLOSED_TAG = "</code>";
    private static final String MEASUREMENT_TAG = "<measurement>";
    private static final String MEASUREMENT_CLOSED_TAG = "</measurement>";
    private static final String VALUE_TAG = "<value>";
    private static final String VALUE_CLOSED_TAG = "</value>";

    public static void main(String[] args) throws IOException {
        final int SERVER_PORT_NUM = 9995; // server port number: it should be the same
                                            // port as the one the server is listening on!
        int TIME_OUT_VAL = 1000; // timeout value in milliseconds
        int MAX_MSG_SIZE = 1000; // maximum message size
        Scanner input = new Scanner(System.in);
        int retransmit = 1, measureID = 0;
        while(retransmit == 1) {
            // Creating an array of available measurement ID
            int[] availMeasureID = {2851, 111};
            // Selecting one measurement Id from the available IDs
            measureID = availMeasureID[(int) (Math.random() * (availMeasureID.length))];
            // Creating a 16 bit unsigned random request ID
            Random rand = new Random();
            int reqId = rand.nextInt(65536);
            // Creating request message String object
            String requestMessage = REQUEST_TAG + "\n\t" + ID_TAG + reqId + ID_CLOSED_TAG + "\n\t" + MEASUREMENT_TAG + measureID
                    + MEASUREMENT_CLOSED_TAG + "\n" + REQUEST_CLOSED_TAG;
            // Calculating Integrity Check for the request message
            int intCheck = calculateIntegrityCheck(requestMessage);
            String intCheckStr = Integer.toString(intCheck); //converting the integrity check value to character sequence
            // Appending Integrity check with the request message
            requestMessage = requestMessage + "\n" + intCheckStr;
            System.out.println("Message to the server:\n" + 	requestMessage);
            // Creating a byte array of the request message
            byte[] requestMessageBytes = requestMessage.getBytes();
            // Creating UDP Client Socket
            DatagramSocket clientSocket = new DatagramSocket();
            // loop back the request to the same machine
            InetAddress server = InetAddress.getLocalHost();
            // Creating the UDP Packet to be sent
            DatagramPacket sentPacket = new DatagramPacket(requestMessageBytes, requestMessageBytes.length, server,
                    SERVER_PORT_NUM);
            // Creating the receive UDP packet
            byte[] receiveMessage = new byte[MAX_MSG_SIZE];
            // Resending the file in case of an error 
            DatagramPacket receivePacket = new DatagramPacket(receiveMessage, receiveMessage.length);
            for (int timer = 0; timer < 4 && retransmit == 1; timer++) {
                // setting the timeout for the socket
                clientSocket.setSoTimeout(TIME_OUT_VAL);
                // Sending the UDP packet to the server
                clientSocket.send(sentPacket);
                try {
                    clientSocket.receive(receivePacket);
                    TIME_OUT_VAL = 1000; // resetting the timer
                    byte[] receiveMsgCopy = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
                    String respMsg = new String(receiveMsgCopy);
                    System.out.println("Message from the server:\n" + respMsg);
                    // Integrity Check Validation
                    boolean intCheckMatch = validateIntegrityCheck(respMsg);
                    // Integrity Check Validation
                    if (!intCheckMatch)
                        System.out.println("\nIntegrity check of the request failed at the server side! Resend request.");
                    // Splitting Code from the received message
                    String codeStr = respMsg.substring(respMsg.indexOf(CODE_TAG) + 6, respMsg.indexOf(CODE_CLOSED_TAG));
                    int code = Integer.parseInt(codeStr);
                    switch (code) {
                    case 0:
                        System.out.println("OK. The response has been created according to the request.");
                        retransmit = 0;
                        String value = respMsg.substring(respMsg.indexOf(VALUE_TAG) + 7, respMsg.indexOf(VALUE_CLOSED_TAG));
                        System.out.println("The received response from the server is: " + value);
                        break;
                    case 1:
                        System.out.println("Error: integrity check failure. The request has one or more bit errors. Resend?(Yes/No)");
                        String answer = input.nextLine();
                        if (answer.equals("Yes"))
                            timer = 0; // and resend
                        else
                            retransmit = 0; // do not resend
                        break;
                    case 2:
                        System.out.println("Error: malformed request. The syntax of the request message is not correct.");
                        retransmit = 0; // do not resend
                        break;
                    case 3:
                        System.out.println("Error: non-existent measurement. The measurement with the requested measurement ID does not exist.");
                        retransmit = 0; // do not resend
                        break;
                    }
                } catch (Exception e) {
                    // timeout - timer expired before receiving the response from
                    // the server
                    System.out.println("\nTime out for the " + (timer + 1) + ". time!");
                    TIME_OUT_VAL *= 2;
                    if (timer == 3) {
                        System.out.println("Error! Communication failed!");
                        TIME_OUT_VAL = 1000;
                        break;// resend the file
                    }
                }
            }
            // If the user wants to send/receive more request/response message
            System.out.println("Do you wish to send more messages? (Yes/No)");
            String reply = input.nextLine();
            if (reply.equals("Yes"))
                retransmit = 1; // send more message
            else
                System.exit(0); // terminate the process
        }
    }

    private static boolean validateIntegrityCheck(String respMsg) {
        String[] recvdIntCheckStr = respMsg.split(RESPONSE_CLOSED_TAG);
        String requestText = (recvdIntCheckStr[0] + RESPONSE_CLOSED_TAG).trim();
        int intCheckResp = calculateIntegrityCheck(requestText); // calculating Integrity Check Locally
        String intCheckStrResp = Integer.toString(intCheckResp); //converting the integrity check value to character sequence
        return ((recvdIntCheckStr[1].trim()).equals(intCheckStrResp)); // comparing Integrity check from the server to the locally calculated Integrity Check
    }

    public static int calculateIntegrityCheck(String msg) {
        byte[] msgBytes = msg.getBytes();
        byte[] msgBytesCopy = Arrays.copyOf(msgBytes, msgBytes.length + 1);
        if (msgBytes.length % 2 != 0)
            msgBytesCopy[msgBytes.length] = 0;
        else
            msgBytesCopy = msgBytes;
        int S = 0;
        for (int i = 0; i < msgBytesCopy.length; i += 2) {
            byte[] word = new byte[2]; // 16 bit word
            word[0] = msgBytesCopy[i];
            word[1] = msgBytesCopy[i + 1];
            int index=0;
            int wordInt = (word[0] << 8) | (word[1] & 0xFF); // forming a word of two
                                                    // consecutive bytes
            index = wordInt ^ S;
            S = (C * index) % D;
        }
        return S;
    }

}
