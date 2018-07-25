package DCMSSystem;

import DCMSSystem.Record.Records;
import DCMSSystem.Record.StudentRecord;
import DCMSSystem.Record.TeacherRecord;
import DCMSSystem.UDP.UDPClient;
import DCMSSystem.UDP.UDPServer;
import com.sun.org.apache.bcel.internal.generic.IF_ACMPEQ;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import org.omg.CORBA.PRIVATE_MEMBER;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.Statement;
import java.lang.annotation.Native;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CenterServer {

    private String centerName;
    public int pid;
    public HashMap<Character, ArrayList<Records>> database = new HashMap<>();
    private UDPServer udpServer;
    public HeartBeat heartBeat;
    private final int FEPortNumber = 8150;
    public HashMap<String, ServerProperties> servers = new HashMap<>();

    // desired by TA solution of udp address/port hardcoding, since we hardcoding everything,
    public static int[] hardcodedServerPorts = {8180, 8181, 8182, 8170, 8171, 8172, 8160, 8161, 8162, 8190};
    public static String[] hardcodedServerNames = {"MTL", "LVL", "DDO", "MTL1", "LVL1", "DDO1", "MTL2", "LVL2", "DDO2", "FEServer"};

    private Thread udpServerThread;
    private Thread heartBeatThread;
    private Thread checkThread;

    public Map<String, ServerProperties> leaders;

    public CenterServer() {
    }


    public CenterServer(String centerName, int portNumber, int pid) throws Exception {
        super();
        //form the list of hardcoded servers in the replica group except current one.
        //as a result there should be address map with 2 adjacent servers from the same replica group and frontend server.
        IntStream.rangeClosed(0, 9)
                .filter((v) -> (centerName.substring(0, 3).equals(hardcodedServerNames[v].substring(0, 3)) ||
                        centerName.equals(hardcodedServerNames[v])) || hardcodedServerNames[v].equals("FEServer"))
                .forEach((v) -> servers.put(hardcodedServerNames[v], new ServerProperties(hardcodedServerPorts[v], hardcodedServerNames[v].substring(0, 3))));
        System.out.println(servers.keySet().toString());
        servers.get("FEServer").status = 2;

        checkSum = new HashMap<String,Integer>();
        checkSum.put("MTL",0);
        checkSum.put("LVL",0);
        checkSum.put("DDO",0);
        checkSum.put("MTL1",0);
        checkSum.put("LVL1",0);
        checkSum.put("DDO1",0);
        checkSum.put("MTL2",0);
        checkSum.put("LVL2",0);
        checkSum.put("DDO2",0);

        lastReceived = new HashMap<String,Integer>();
        lastReceived.put("MTL",-1);
        lastReceived.put("LVL",-1);
        lastReceived.put("DDO",-1);
        lastReceived.put("MTL1",-1);
        lastReceived.put("LVL1",-1);
        lastReceived.put("DDO1",-1);
        lastReceived.put("MTL2",-1);
        lastReceived.put("LVL2",-1);
        lastReceived.put("DDO2",-1);


        //predefining first instance as the Lead
        if (servers.get(centerName.substring(0, 3)) != null) {
            servers.get(centerName.substring(0, 3)).status = 1;
        }

        this.pid = pid;
        this.centerName = centerName;
        udpServer = new UDPServer(portNumber, this);
        udpServerThread = new Thread(udpServer);
        udpServerThread.start();
        heartBeat = new HeartBeat(servers, this);
        heartBeatThread = new Thread(heartBeat);
        if (centerName.length() == 3) {
            heartBeat.isLeader = true;
        }
        heartBeatThread.start();
        sentPacket = new HashMap<>();
        CheckSum checkSumThread = new CheckSum(this);
        reSendThread();
        reSendThread.start();
        checkThread = new Thread(checkSumThread);
        checkThread.start();

    }

    public String getCenterName() {
        return centerName;
    }

    /*
      Validates the record ID for existence in localDB, in case of existence - regenerates ID and validates again.
    */
    private void validateRecordId(Records inRecord, char key) {
        String recordId = inRecord.getRecordID();
        if (database.get(key) != null) {
            for (Records record : database.get(key)) {
                if (record.getRecordID().equals(recordId)) {
                    inRecord.regenRecordID();
                    validateRecordId(inRecord, key);
                    break;
                }
            }
        }
    }

    // this is going to be a FIFO method caller, which will iterate through servers hashMap and send calls via udp
    // to the members of replica group. Here I don't make any difference if current instance is leader or not,
    // relying on FE to determine who is lead. So basically it will query adjacent servers with "state 1" get their results
    // (confirming that result is returned and is correct), and finally return result to the calling FE

    private HashMap<String,Integer> checkSum = new HashMap<>();
    private HashMap<String,Integer> lastReceived = new HashMap<>();
    private LinkedList<Integer> handledRequests = new LinkedList<>();
    private HashMap<Integer,LinkedList<String>> sentPacket;
    private HashMap<String,LinkedList<Packet>> waitingList = new HashMap<>();

    private boolean reSend = false;
    private Thread reSendThread;
    private LinkedBlockingQueue<Packet> reSendList = new LinkedBlockingQueue<>();

    public void reSendThread(){
        reSendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (servers.get(centerName).state == 1){
                    if (reSend && !reSendList.isEmpty()){
                        Packet packet = reSendList.poll();
                        String response = UDPClient.request(ByteUtility.toByteArray(packet),servers.get(packet.getReceiver()).udpPort);
                        if (response.split(":")[0].equals("operation")){
                            System.out.println("DONEBEFORE ----------------");
                            sentPacket.get(packet.getSeqNUmber()).remove(packet.getReceiver());
                            reSend = false;
                        }
                    }
                }
            }
        },"reSendCheck");
    }


    public HashMap<String, LinkedList<Packet>> getWaitingList() {
        return waitingList;
    }

    public HashMap<String, Integer> getLastReceived() {
        return lastReceived;
    }

    public HashMap<Integer,LinkedList<String>> getSentPacket() {
        return sentPacket;
    }

    public LinkedList<Integer> getHandledRequests() {
        return handledRequests;
    }

    public String groupMethodCall(int seqNumber, byte[] content) {
        String result = "";
        Packet packet = new Packet(0,seqNumber,content,"","");
//        for (Map.Entry<String, ServerProperties> entry : servers.entrySet()) {
//            if (entry.getValue().replicaGroup.equals(centerName.substring(0,3)) && entry.getValue().state == 1 &&
//                    !entry.getKey().equals(centerName) && entry.getValue().status != 1){
//                packet.setCheckSum(checkSum.get(entry.getKey()));
//                packet.setSender(centerName);
//                byte [] send = ByteUtility.toByteArray(packet);
//                String response = UDPClient.request(send,entry.getValue().udpPort);
//                LinkedList<String> linkedList = new LinkedList<>();
//                linkedList.add(entry.getKey());
//                System.out.println(entry.getKey());
//                sentPacket.put(seqNumber,linkedList);
//                int update = checkSum.get(entry.getKey()) + 1;
//                checkSum.replace(entry.getKey(),update);
//                System.out.println(response + " FROM GROUP");
//                if (response.equals("no reply")){
//                    String confirm = UDPClient.request(ByteUtility.toByteArray(seqNumber),entry.getValue().udpPort);
//                    if (confirm.equals("Done")){
//                        sentPacket.get(seqNumber).remove(entry.getKey());
//                    }
//                }else if (response.split(":")[0].equals("operation")){
//                    sentPacket.get(seqNumber).remove(entry.getKey());
//                }
//            }
//        }
        servers.entrySet().stream().filter((v) -> (v.getValue().replicaGroup.equals(centerName.substring(0,3))) && v.getValue().state == 1 &&
        !v.getKey().equals(centerName) && v.getValue().status != 1).forEach((v) ->
                {       packet.setCheckSum(checkSum.get(v.getKey()));
                    packet.setSender(centerName);
                    packet.setReceiver(v.getKey());
                    byte [] send = ByteUtility.toByteArray(packet);
                    String response = UDPClient.groupCall(send,v.getValue().udpPort);
                    LinkedList<String> linkedList = new LinkedList<>();
                    linkedList.add(v.getKey());
                    System.out.println(v.getKey());
                    sentPacket.put(seqNumber,linkedList);
                    System.out.println(linkedList.size());
                    int update = checkSum.get(v.getKey()) + 1;
                    checkSum.replace(v.getKey(),update);
                    System.out.println(response + " FROM GROUP");
                    if (response.split(":")[0].equals("operation")){
                        sentPacket.get(seqNumber).remove(v.getKey());
                    }else if (response.equals("no reply")){
                        System.out.println("RE SEND !!!!!!");
                        reSend = true;
                        reSendList.add(packet);
                    }
                }

                );
        if (sentPacket.get(seqNumber).size() == 0){
            result = "Done" + ":" + seqNumber;
        }
        System.out.println(result + "-----------");
        return result;
    }

    public String createTRecord(String managerId, String firstName, String lastName, String address, String phone, String specialization, String location) {
        TeacherRecord teacherRecord = new TeacherRecord(firstName, lastName, address, phone, specialization, location);
        char key = lastName.charAt(0);
        synchronized (database) {
            validateRecordId(teacherRecord, key);
            if (database.get(key) == null) {
                ArrayList<Records> value = new ArrayList<>();
                value.add(teacherRecord);
                database.put(key, value);
            } else {
                ArrayList<Records> value = database.get(key);
                value.add(teacherRecord);
                database.put(key, value);
            }
        }
        Log.log(Log.getCurrentTime(), managerId, "createTRecord", "Create successfully! Record ID is " + teacherRecord.getRecordID());
        return teacherRecord.getRecordID();
    }

    public String createSRecord(String managerId, String firstName, String lastName, String[] courseRegistered, String status, String statusDate) {
        StudentRecord studentRecord = new StudentRecord(firstName, lastName, courseRegistered, status, statusDate);
        char key = lastName.charAt(0);
        synchronized (database) {
            validateRecordId(studentRecord, key);
            if (database.get(key) == null) {
                ArrayList<Records> value = new ArrayList<>();
                value.add(studentRecord);
                database.put(key, value);
            } else {
                ArrayList<Records> value = database.get(key);
                value.add(studentRecord);
                database.put(key, value);
            }
        }
        Log.log(Log.getCurrentTime(), managerId, "createSRecord", "Create successfully! Record ID is " + studentRecord.getRecordID());
        return studentRecord.getRecordID();
    }

    /*
      Concurrent implementation of getRecordCounts using Java8 parallel streams.
    */
    public String getRecordCounts(String managerId) {
        String result;

        //generates result querying servers from hardcoded udp ports, as per TA recommendations

        result = IntStream.rangeClosed(0, 8).parallel().mapToObj((v) ->
        {
            byte[] getCount = ByteUtility.toByteArray("getCount");
            String result1 = "";
            if (servers.get(hardcodedServerNames[v]).status == 1) {
                result1 = hardcodedServerNames[v] + ":" + UDPClient.request(getCount, hardcodedServerPorts[v]);
                System.out.printf("\n" + hardcodedServerNames[v] + " on port " + hardcodedServerPorts[v] + " processed\n");
            }
            return result1;

        }).collect(Collectors.joining(" "));

        System.out.printf("\n" + result);
        Log.log(Log.getCurrentTime(), managerId, "getRecordCounts", "Successful");
        return result;
    }

    /*
      Returns local record count for the particular object instance, which is executed by some instance getRecordCount query.
    */
    public int getLocalRecordCount() {
        int sum = 0;
        for (ArrayList<Records> records :
                database.values()) {
            sum += records.size();
        }
        return sum;
    }

    /*
      Edits record using java reflection, to dynamically get the object class (either Student or Teacher) and editable attributes.
    */


    public String editRecord(String managerId, String recordID, String fieldName, String newValue) {
        String result = "";
        Boolean ableModified = true;
        BeanInfo recordInfo;
        synchronized (database) {
            //looks for the recordId in local db
            for (char key : database.keySet()) {
                for (Records record : database.get(key)) {
                    if (record.getRecordID().equals(recordID)) {

                        // following reads information about the object, more precisely of its class, into BeanInfo
                        try {
                            recordInfo = Introspector.getBeanInfo(record.getClass());
                        } catch (Exception e) {
                            return e.getMessage();
                        }

                        //recordPds in this case is the array of leaders available in this class
                        PropertyDescriptor[] recordPds = recordInfo.getPropertyDescriptors();
                        for (PropertyDescriptor prop : recordPds) {
                            if (prop.getName().equals(fieldName)) {
                                if (fieldName.equals("location")) {
                                    ableModified = newValue.equals("MTL") || newValue.equals("LVL") || newValue.equals("DDO");
                                }
                            /*
                            Here we form the statement to execute, in our case, update the field in the object.
                            We rely on property names captured in previous recordPds. There is no need in explicit definition
                            of particular Student of TeacherRecord class, since we can just analyze whatever record found
                            with recordId.
                            prop.getWriteMethod() looks for method which writes to property, which was filtered with previous
                            prop.getName().equals(fieldName). As a result newValue is passed as argument to method found, hopefully,
                            it is the proper setter in the end.
                            * look into java reflection and java beans library.
                             */
                                if (ableModified) {

                                    Statement stmt = new Statement(record, prop.getWriteMethod().getName(), new java.lang.Object[]{newValue});
                                    try {
                                        stmt.execute();
                                    } catch (Exception e) {
                                        return e.getMessage();
                                    }
                                    result = "Record updated";

                                    String operation = "edit: " + prop.getName();
                                    Log.log(Log.getCurrentTime(), managerId, operation, result);
                                    return result;
                                } else {
                                    String operation = "edit: " + prop.getName();
                                    result = "The new value is not valid!";
                                    Log.log(Log.getCurrentTime(), managerId, operation, result);
                                    return result;
                                }
                            }

                        }
                        result = "fieldName doesn't match record type";
                        String operation = "edit: " + fieldName;
                        Log.log(Log.getCurrentTime(), managerId, operation, result);
                        return result;
                    }
                }
            }
            result = "No such record Id for this manager";
            Log.log(Log.getCurrentTime(), managerId, "edit: " + fieldName, result);
        }
        return result;
    }

    /*
       transfer record from the server associated with manager if it is verified to the remotecenter which is given by name
    */
    public String transferRecord(String managerID, String recordID, String remoteCenterServerName) {
        String result = "";
        boolean has = false;
        ArrayList<Records> toBeModified = null;
        Records transferedRecord = null;

        synchronized (database) {
            for (char key : database.keySet()) {
                for (Records record : database.get(key)) {
                    if (record.getRecordID().equals(recordID)) {
                        has = true;
                        transferedRecord = record;
                        toBeModified = database.get(key);
                    }
                }
            }
            /*
               check if the id is existing and the remotecenter is valid
             */
            boolean isValidatedCenter = remoteCenterServerName.equals("MTL") || remoteCenterServerName.equals("LVL") || remoteCenterServerName.equals("DDO");
            boolean ableToTransfer = isValidatedCenter && has && !centerName.equals(remoteCenterServerName);
            byte[] serializedMessage = null;
            /*
             using udp to params the function and parse the object to bytes to do the work.
             */
            if (ableToTransfer) {

                for (Map.Entry<String, ServerProperties> entry : leaders.entrySet()) {
                    System.out.println(entry.getKey());
                    if (entry.getKey().substring(0,3).equals(remoteCenterServerName)){
                        if (entry.getValue().state == 1){
                            int addSeqNumber = ByteUtility.generateSeq();
                            System.out.println(transferedRecord);
                            Wrapper wrapper = new Wrapper("add",managerID,transferedRecord);
                            Packet packet = new Packet(checkSum.get(entry.getKey()),addSeqNumber,ByteUtility.toByteArray(wrapper),centerName,entry.getKey());
                            System.out.println("add to " + entry.getKey() + ":" + entry.getValue().udpPort);
                            serializedMessage = ByteUtility.toByteArray(packet);
                            String response = UDPClient.groupCall(serializedMessage, entry.getValue().udpPort);
                            if (response.split(":")[0].equals("operation") && Integer.valueOf(response.split(":")[3]) ==  addSeqNumber){
                                result += recordID + " is added to " + entry.getKey() + " and ";
                            }
                            System.out.println(result);
                        }
                    }
                }
                  //                result += IntStream.rangeClosed(0, 2).filter((v) -> hardcodedServerNames[v].equals(remoteCenterServerName))
//                        .mapToObj((v) -> UDPClient.request(serializedMessage, hardcodedServerPorts[v])).collect(Collectors.joining());

                if (toBeModified.remove(transferedRecord)) {
                    int deleteSeqNumber = ByteUtility.generateSeq();
                    Wrapper wrapper = new Wrapper("delete",recordID);
                    String response = groupMethodCall(deleteSeqNumber,ByteUtility.toByteArray(wrapper));
                    if (response.equals("Done" + ":" + deleteSeqNumber)){
                        result += recordID + " is removed from " + getCenterName();
                    }

                }

                Log.log(Log.getCurrentTime(), managerID, "transferRecord:" + recordID, result);

            } else {

                if (!has) {
                    result += "No such record Id for this manager";
                }
                if (!isValidatedCenter) {
                    result += " No such Center to transfer";
                }
                if (centerName.equals(remoteCenterServerName)) {
                    result += " The record is already in the Center,you do not need to tranfer!";

                }
                Log.log(Log.getCurrentTime(), managerID, "tranferRecord:" + recordID, result);
            }
        }
        return result;
    }

    //executes election of leader among the replica group members
    // as of now this implementation won't reattempt victory message in case it's not delivered to FE or
    // reply of FE is not delivered to the instance
    public void bullyElect() {
        byte[] elect = ByteUtility.toByteArray("elect");
        byte[] victory = ByteUtility.toByteArray("victory:" + centerName);

        if (servers.keySet().stream().parallel()
                .filter((v) -> servers.get(v).status != 2 && servers.get(v).state == 1 && servers.get(v).pid > this.pid)
                .count() > 0) {
            if (servers.keySet().stream()
                    .filter((v) -> servers.get(v).status != 2 && servers.get(v).state == 1 && servers.get(v).pid > this.pid)
                    .map((v) -> UDPClient.request(elect, servers.get(v).udpPort))
                    .allMatch((v) -> v.equals("no reply"))) {
                //when instance hits the case, when no bigger pid around,it declares to everybody including FE its leadership
                System.out.println("nobody replied, turning into Lead\n");
                servers.keySet().stream()
                        .filter((v) -> servers.get(v).state == 1 || servers.get(v).status == 2)
                        .forEach((v) -> UDPClient.request(victory, servers.get(v).udpPort));
                heartBeat.isLeader = true;
            }
        } else {
            System.out.println("Seems nobody alive bigger then me, turning into lead\n");
            servers.keySet().stream()
                    .filter((v) -> servers.get(v).state == 1 || servers.get(v).status == 2)
                    .forEach((v) -> UDPClient.request(victory, servers.get(v).udpPort));
            heartBeat.isLeader = true;

        }
    }

    public void shutdown() {
        udpServer.stopServer();
    }

}