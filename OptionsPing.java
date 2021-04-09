import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;


public class OptionsPing {
    private static long min = Long.MAX_VALUE;
    private static long max = 0L;
    private static String generateCallId() {
        final int leftBoundary = 97;
        final int rightBoundary = 122;
        final int callIdMaxLength = 10;
        Random random = new Random();
        final StringBuilder buffer = new StringBuilder(callIdMaxLength);
        for (int i = 0; i <= callIdMaxLength; i++) {
            int randomLimitedInt = leftBoundary + (int) 
              (random.nextFloat() * (rightBoundary - leftBoundary + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }

    private static Boolean isOptionsResponseOk(final String packet, String callId) {
        final String regexResponse = "^SIP/2\\.0 200 OK$";
        final Pattern p1 = Pattern.compile(regexResponse, Pattern.MULTILINE);
        final Matcher m1 = p1.matcher(packet);

        final String regexCallId = "^Call-ID:\s" +callId+ "@";
        final Pattern p2 = Pattern.compile(regexCallId, Pattern.MULTILINE);
        final Matcher m2 = p2.matcher(packet);

        return m1.find() == m2.find();
    }

    public static void main(String [] args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Exited!");
                if (max > 0 && min > 0) {
                    System.out.println("max: " + max / 1000000 + " ms");
                    System.out.println("min: " + min / 1000000 + " ms");
                } else {
                    System.out.println("No response received.");
                }
            }
        });

        final StringBuilder myString = new StringBuilder("Starting the ping program...");
        System.out.println(myString);
        final InetAddress ip = InetAddress.getLocalHost();
        String destinationIpAddress;
        Short destinationPort = 5060;
        if (args.length > 0) {
            destinationIpAddress = args[0];
            destinationPort = Short.parseShort(args[1]);
        } else {
            System.out.println("For next time, do: java OptionsPing <IP> <PORT>");
            try (Scanner sc = new Scanner(System.in)) {
                System.out.print("Enter the IP: ");
                destinationIpAddress = sc.nextLine();
                System.out.print("Enter the port: ");
                destinationPort = Short.parseShort(sc.nextLine());
            }
        }

        final InetAddress dstIp = InetAddress.getByName(destinationIpAddress);
        byte [] receiveData = new byte[1024];
        while(true) {
            DatagramSocket ds = new DatagramSocket();
            ds.setSoTimeout(2000);
            final String callId = generateCallId();
            String method = "OPTIONS sip:" + dstIp.getHostAddress() + ":" + 5060 + " SIP/2.0\r\n"
            .concat("Via: SIP/2.0/UDP " + ip.getHostAddress() + ":" + ds.getLocalPort() + ";branch=z9hG4bK-323032-callId\r\n")
            .concat("From: <sip:" + ip.getHostAddress() + ":" + ds.getLocalPort() + ">;tag=" + 123456789 + "\r\n")
            .concat("To: <sip:mega@" + dstIp.getHostAddress() + ":" + 5060 + ">\r\n")
            .concat("Max-Forwards: 10\r\n")
            .concat("Call-ID: " + callId + "@" + ip.getHostAddress() +"\r\n")
            .concat("CSeq: 1 OPTIONS\r\n")
            .concat("Content-Length: 0\r\n\r\n");
            DatagramPacket DpSend = new DatagramPacket(method.getBytes(), method.length(), dstIp, 5060);
            final long startTime = System.nanoTime();
            ds.send(DpSend);
            System.out.println("Sent from port: " + ds.getLocalPort());
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                ds.receive(receivePacket);
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout waiting for response: " + e);
                ds.close();
                continue;
            }
            String response = new String(receivePacket.getData());
            long endTime = System.nanoTime();
            if (isOptionsResponseOk(response, callId)) {
                System.out.println("200 OK Response received");
            } else {
                System.out.println("Unknown response received.\nCall-ID: " + callId + "\nResponse: " + response);
            }
            ds.close();
            long timeElapsed = endTime - startTime;
            if (timeElapsed < min) {
                min = timeElapsed;
            }
            if (timeElapsed > max) {
                max = timeElapsed;
            }
            System.out.println("SIP OPTIONS response: " + timeElapsed / 1000000 + " ms");
            TimeUnit.SECONDS.sleep(2);
        }
    }
}