package udpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class Server {

    private static final int C = 7919;
    private static final int D = 65536;
    private static final String REQUEST_TAG = "<request>";
    private static final String REQUEST_CLOSED_TAG = "</request>";
    private static final String RESPONSE_TAG = "<response>";
    private static final String RESPONSE_CLOSED_TAG = "</response>";
    private static final String MEASUREMENT_TAG = "<measurement>";
    private static final String MEASUREMENT_CLOSED_TAG = "</measurement>";
    private static final String ID_TAG = "<id>";
    private static final String ID_CLOSED_TAG = "</id>";
    private static final String VALUE_TAG = "<value>";
    private static final String VALUE_CLOSED_TAG = "</value>";
    private static final String CODE_TAG = "<code>";
    private static final String CODE_CLOSED_TAG = "</code>";
    

    public static void main(String[] args) throws IOException {
        final int SERVER_PORT_NUM = 9995; // server port number: it should be the same
                                          // port as the client is sending packets to!
        int MAX_MSG_SIZE = 1000; // maximum message size
        // send and receive data buffers
        byte[] receivedMessage = new byte[MAX_MSG_SIZE];
        byte[] sentMessage = new byte[MAX_MSG_SIZE];
        // send and receive UDP packets
        DatagramPacket receivePacket = new DatagramPacket(receivedMessage, receivedMessage.length);
        // server socket, listening on port SERVER_PORT_NUM
        DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT_NUM);
        // client's IP address object
        InetAddress clientAddress;
        String responseMessage = "", lineValue = "", valueId="";
        boolean doesNotContainMeasurement = true; //
        // Load the file input stream
        String filePath = new File("").getAbsolutePath(); 
        // do this forever until client terminates the process
        while(true) {
            BufferedReader input = new BufferedReader(new FileReader(filePath+"/src/udpServer/data.txt"));
            System.out.println("Receiving the request from the client...");
            // Receiving client's request
            serverSocket.receive(receivePacket);
            byte[] receiveMsgCopy = Arrays.copyOf(receivedMessage, receivePacket.getLength());
            String reqMsg = new String(receiveMsgCopy);
            System.out.println("Message from the client:\n"+reqMsg);
            // the receive() method blocks here (program execution stops)
            // only one way to continue: packet is received
            // Integrity Check Validation And Response Message with Code Values
            responseMessage = constructResponseMessage(reqMsg, responseMessage, input, lineValue, doesNotContainMeasurement, valueId);
            // Calculating Integrity Check field
            int intCheckResp = calculateIntegrityCheck(responseMessage);
            responseMessage = responseMessage + "\n"+Integer.toString(intCheckResp);
            System.out.println("Message to the client:\n"+responseMessage);
            // Creating a byte array of the request message
            byte[] responseMessageBytes = responseMessage.getBytes();
            sentMessage = Arrays.copyOf(responseMessageBytes, responseMessageBytes.length);
            // getting the client info out of the received UDP packet object
            clientAddress = receivePacket.getAddress();
            int clientPort = receivePacket.getPort();
            DatagramPacket sentPacket = new DatagramPacket(sentMessage, responseMessageBytes.length,
                    clientAddress, clientPort);
            // setting up the response UDP packet object
            sentPacket.setAddress(clientAddress); // destination IP address
            sentPacket.setPort(clientPort); // destination port number
            System.out.println("Sending the response to the client...");
            // sending the response to the client
            serverSocket.send(sentPacket);
            // the socket is not closed, as this program is supposed to run "forever".
        } // while block
    } // main Class

    private static String constructResponseMessage(String reqMsg, String responseMessage, BufferedReader input,
            String lineValue, boolean doesNotContainMeasurement, String valueId) throws IOException {
        // Splitting ID from the received message
        String reqId = reqMsg.substring(reqMsg.indexOf(ID_TAG)+ID_TAG.length(), reqMsg.indexOf(ID_CLOSED_TAG));
        String measureId = reqMsg.substring(reqMsg.indexOf(MEASUREMENT_TAG) + MEASUREMENT_TAG.length(), reqMsg.indexOf(MEASUREMENT_CLOSED_TAG));
        if ((reqMsg.contains(REQUEST_TAG) && reqMsg.contains(REQUEST_CLOSED_TAG)
                && reqMsg.contains(ID_TAG) && reqMsg.contains(ID_CLOSED_TAG)
                && reqMsg.contains(MEASUREMENT_TAG) && reqMsg.contains(MEASUREMENT_CLOSED_TAG))) {
            String[] recvdIntCheckStr = reqMsg.split(REQUEST_CLOSED_TAG);
            String requestText = (recvdIntCheckStr[0] + REQUEST_CLOSED_TAG).trim();
            int intCheck = calculateIntegrityCheck(requestText);
            String intCheckStr = Integer.toString(intCheck); //converting the integrity check value to character sequence
            if (!(recvdIntCheckStr[1].trim()).equals(intCheckStr))
                return responseMessage = RESPONSE_TAG + "\n\t" + ID_TAG + reqId + ID_CLOSED_TAG + "\n\t" + CODE_TAG + 1
                        + CODE_CLOSED_TAG + "\n" + RESPONSE_CLOSED_TAG;
            else
            	return responseMessage = responseMessageWithValue(lineValue, input, measureId, doesNotContainMeasurement, valueId, responseMessage, reqId);
        }
        else
            return responseMessage = RESPONSE_TAG + "\n\t" + ID_TAG + reqId + ID_CLOSED_TAG + "\n\t" + CODE_TAG + 2
                    + CODE_CLOSED_TAG + "\n" + RESPONSE_CLOSED_TAG;
    }

    private static String responseMessageWithValue(String lineValue, BufferedReader input, String measureId,
            boolean doesNotContainMeasurement, String valueId, String responseMessage, String reqId)
                    throws IOException {
        while ((lineValue = input.readLine()) != null) {
            if (lineValue.contains(measureId)) {
                doesNotContainMeasurement = false;
                // Extracting Value Id from the Line containing Measurement ID and Value ID
                valueId = lineValue.substring(lineValue.length() - 6, lineValue.length()).trim();
                // Constructing the Response Message with Code 0 and Integrity Check field.
                return responseMessage = RESPONSE_TAG + "\n\t" + ID_TAG + reqId + ID_CLOSED_TAG + "\n\t" + CODE_TAG + 0
                        + CODE_CLOSED_TAG + "\n\t" + MEASUREMENT_TAG + measureId + MEASUREMENT_CLOSED_TAG + "\n\t" + VALUE_TAG + valueId
                        + VALUE_CLOSED_TAG + "\n" + RESPONSE_CLOSED_TAG;
            }
        }
        if (doesNotContainMeasurement)
            return responseMessage = RESPONSE_TAG + "\n\t" + ID_TAG + reqId + ID_CLOSED_TAG + "\n\t" + CODE_TAG + 3
                    + CODE_CLOSED_TAG + "\n" + RESPONSE_CLOSED_TAG;
        return responseMessage;
    }

    public static int calculateIntegrityCheck(String reqMsg) {
        byte[] reqMsgBytes = reqMsg.getBytes();
        byte[] reqMsgBytesCopy = Arrays.copyOf(reqMsgBytes, reqMsgBytes.length + 1);
        if (reqMsgBytes.length % 2 != 0)
            reqMsgBytesCopy[reqMsgBytes.length] = 0;
        else
            reqMsgBytesCopy = reqMsgBytes;
        int S = 0;
        for (int i = 0; i < reqMsgBytesCopy.length; i += 2) {
            byte[] word = new byte[2]; // 16 bit word
            word[0] = reqMsgBytesCopy[i];
            word[1] = reqMsgBytesCopy[i + 1];
            int index;
            int wordInt = ((word[0] & 0xff) << 8) | (word[1] & 0xff); // forming a word of two
                                                    // consecutive bytes
            index = wordInt ^ S;
            S = (C * index) % D;
        }
        return S;
    }

}
