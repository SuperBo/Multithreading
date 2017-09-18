

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class deploy {

    //COORDINATOR PROCESS PARAMS
    static int NUMBER_OF_PROCS;
    static volatile int numberOfProcessRegistered = 0;
    static volatile int numberOfProcessReady = 0;
    static Object coordinatorLock = new Object();
    static final String CONFIG = "/home/013/d/dx/dxc141530/dsConfig";
    static Map<Integer, Set<Integer>> neighbours = new HashMap<>();
    static Map<Integer, Neighbour> pidToHostnameMap = new HashMap<>();
    static int PROCESSID;

    //NON COORDINATOR PROCESS PARAMS
    static volatile boolean canSendHello = false;
    static volatile boolean canSendReady = true;
    static volatile boolean canSendCompute = false;
    static BlockingQueue<Integer> blockingQueue = new ArrayBlockingQueue<>(1);
    static volatile int numberOfNeighbours = 0;
    static volatile int neighbourCounter = 0;
    static Object lock = new Object();
    static Set<Neighbour> localNeighbourSet = new HashSet<>();

    public static void main(String[] args) {

        if (args.length != 0) {
            if (args[0].equalsIgnoreCase("-c")) {
                //Running the coordinator part of the process
                runConfiguration(CONFIG);

                Thread coordinatorThread = new Thread(() -> {
                    System.out.println();
                    System.out.println("********Coordinator process initiated");
                    System.out.println("********Waiting for processes to register...");
                    System.out.println();
                    try {
                        ServerSocket serverSocket = new ServerSocket(5001);
                        while (true) {
                            Socket client = serverSocket.accept();
                            System.out.println("********Register request received from "+client.getInetAddress().getHostName());
                            Thread handlerThread = new Thread(() -> {
                                try {
                                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                                    String line;
                                    int pid;
                                    while ((line = in.readLine()) != null) {
                                        if (line.equalsIgnoreCase("register")) {
                                            synchronized (coordinatorLock) {
                                                numberOfProcessRegistered++;
                                                pid = numberOfProcessRegistered;
                                                pidToHostnameMap.put(pid,
                                                        new Neighbour(String.valueOf(client.getInetAddress().getHostName()),pid));
                                                System.out.println("********number of processes registered with coordinator so far : " + numberOfProcessRegistered);
                                            }
                                            while (true) {
                                                if (numberOfProcessRegistered == NUMBER_OF_PROCS) {
                                                    break;
                                                }
                                            }
                                            System.out.println();
                                            Thread.sleep(200);
                                            //modify below code to apprise the clients about their host id and neighbours
                                            StringBuilder stringBuilder = new StringBuilder();
                                            stringBuilder.append(pid);
                                            Set<Integer> tempSet = neighbours.get(pid);
                                            for (Integer i : tempSet) {
                                                stringBuilder.append(",");
                                                Neighbour neighbour = pidToHostnameMap.get(i);
                                                stringBuilder.append(neighbour.getHostname()+" "+neighbour.getId());
                                            }
                                            System.out.println("********sending registered to " + client.getInetAddress().getHostName());
                                            out.println("registered," + stringBuilder.toString());
                                        }
                                        if (line.equalsIgnoreCase("ready")) {
                                            synchronized (coordinatorLock) {
                                                numberOfProcessReady++;
                                                System.out.println("********number of processes ready so far : " + numberOfProcessReady);
                                            }
                                            while (true) {
                                                if (numberOfProcessReady == NUMBER_OF_PROCS) {
                                                    break;
                                                }
                                            }
                                            System.out.println();
                                            Thread.sleep(200);
                                            //modify below code to apprise the clients about their host id and neighbours
                                            System.out.println("********sending compute to " + client.getInetAddress().getHostName());
                                            out.println("compute");
                                        }
                                    }
                                    out.close();
                                    in.close();
                                    client.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            });
                            handlerThread.start();
                            //handlerThread.join();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                coordinatorThread.start();
            }
        }

        //Running the non coordinator part of the process
        Thread initializingThread = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //register with coordinator
            try {
                BufferedReader reader = new BufferedReader(new FileReader(CONFIG));
                String[] params = reader.readLine().split(" ");
                Socket client = new Socket(params[1], 5001);
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                System.out.println();
                System.out.println("registration initiated");
                System.out.println();
                out.println("register");
                String line;
                while ((line = in.readLine()) != null) {
                    String[] parsedReceivedLine = line.split(",");
                    if (parsedReceivedLine[0].equals("registered")) {
                        System.out.println();
                        System.out.println("registration success");
                        System.out.println();
                        System.out.println("received PID and neighbour list from coordinator");
                        PROCESSID = Integer.parseInt(parsedReceivedLine[1]);
                        System.out.println("PID ====== "+PROCESSID);
                        System.out.println("Neighbours =========PID");
                        for (int i = 2; i < parsedReceivedLine.length; i++) {
                            String[] detailsOfNeighbour = parsedReceivedLine[i].split(" ");
                            localNeighbourSet.add(new Neighbour(detailsOfNeighbour[0]));
                            numberOfNeighbours++;
                            System.out.println(detailsOfNeighbour[0]+"   "+detailsOfNeighbour[1]);
                        }
                        System.out.println("=======================");
                        System.out.println();
                        synchronized (lock) {
                            canSendHello = true;
                        }
                    }
                    while (true) {
                        if (!blockingQueue.isEmpty())
                            break;
                    }
                    if (canSendReady) {
                        System.out.println();
                        System.out.println("sending ready to coordinator");
                        System.out.println();
                        out.println("ready");
                        canSendReady = false;
                    }
                    if (line.equals("compute")) {
                        System.out.println();
                        System.out.println("received compute from coordinator");
                        System.out.println();
                        synchronized (lock) {
                            canSendCompute = true;
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread clientThreadMain = new Thread(() -> {
            while (true) {
                if (canSendHello)
                    break;
                try {
                    Thread.sleep(900);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //for each neighbour, create new thread and establish new socket
            for (Neighbour n : localNeighbourSet) {
                Thread clientThreadAncillary = new Thread(() -> {
                    String line;
                    try {
                        Socket client = new Socket(n.getHostname(), 5000);
                        PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
                        out.println("hello from PID "+PROCESSID);
                        while (true) {
                            if (canSendCompute) {
                                out.println("compute from PID " + PROCESSID);
                                Thread.sleep(10000);
                            }
                            Thread.sleep(900);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                clientThreadAncillary.start();
            }
        });


        Thread serverThreadMain = new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(5000);
                while (true) {
                    Socket client = serverSocket.accept();
                    Thread serverThreadAncillary = new Thread(() -> {
                        try {
                            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                            String line;
                            while ((line = in.readLine()) != null) {
                                String[] parsedLine = line.split(" ");
                                if (parsedLine[0].equalsIgnoreCase("hello")) {
                                    System.out.println(line);
                                    synchronized (lock) {
                                        neighbourCounter++;
                                    }
                                    if (neighbourCounter == numberOfNeighbours)
                                        blockingQueue.add(1);
                                } else {
                                    System.out.println(line);
                                }
                            }
                            out.close();
                            in.close();
                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    serverThreadAncillary.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        initializingThread.start();
        clientThreadMain.start();
        serverThreadMain.start();


        try {
            initializingThread.join();
            clientThreadMain.join();
            serverThreadMain.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void runConfiguration(String fileLocation) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileLocation));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parsedLines = line.split(" ");
                if (parsedLines[0].equalsIgnoreCase("COORDINATOR"))
                    continue;
                if (parsedLines[0].equalsIgnoreCase("NUMBER")) {
                    NUMBER_OF_PROCS = Integer.parseInt(parsedLines[3]);
                }
                if (parsedLines[0].equalsIgnoreCase("INTERVAL"))
                    continue;
                if (parsedLines[0].equalsIgnoreCase("TERMINATE"))
                    continue;
                if (parsedLines[0].equalsIgnoreCase("NEIGHBOR")) {
                    while ((line = reader.readLine()) != null) {
                        if (line.equals("")) {
                            break;
                        }
                        String[] arrayOfProcesses = line.split(" ");
                        Set<Integer> setOfNeighbourProcesses = new HashSet<>();
                        for (int i = 1; i < arrayOfProcesses.length; i++) {
                            setOfNeighbourProcesses.add(Integer.parseInt(arrayOfProcesses[i]));
                        }
                        neighbours.put(Integer.parseInt(arrayOfProcesses[0]), setOfNeighbourProcesses);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/*
class Neighbour {
    private int id;
    private String hostname;
    private String portnum;

    public Neighbour(){}

    public Neighbour(String hostname,int id){
        this.hostname=hostname;
        this.id=id;
    }

    public Neighbour(String hostname){
        this.hostname=hostname;
    }
    public Neighbour(String hostname, String portnum) {
        this.hostname = hostname;
        this.portnum = portnum;
    }

    public Neighbour(int id, String hostname, String portnum) {
        this.id = id;
        this.hostname = hostname;
        this.portnum = portnum;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPortnum() {
        return portnum;
    }

    public void setPortnum(String portnum) {
        this.portnum = portnum;
    }

    public int getId() {
        return id;
    }
}
*/
