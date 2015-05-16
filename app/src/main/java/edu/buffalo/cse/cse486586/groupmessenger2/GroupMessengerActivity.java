package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static List<String> remotePorts = new ArrayList<>();
    static final int SERVER_PORT = 10000;
    static String clientport;
    static Map<String, Double> processId = new HashMap<>();
    Object lock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        remotePorts.clear();
        remotePorts.add("11108");
        remotePorts.add("11112");
        remotePorts.add("11116");
        remotePorts.add("11120");
        remotePorts.add("11124");

        processId.put("11108", .1);
        processId.put("11112", .2);
        processId.put("11116", .3);
        processId.put("11120", .4);
        processId.put("11124", .5);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        clientport = myPort;

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        final Button send = (Button) findViewById(R.id.button4);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                EditText text = (EditText) findViewById(R.id.editText1);
                String msg = text.getText().toString();

                text.setText("");

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        String URL = "content://edu.buffalo.cse.cse486586.groupmessenger2.provider";
        Uri contentUri = Uri.parse(URL);

        Map<String, Integer> fifo = new HashMap<>();
        Map<String, Double> maxSeq = new HashMap<>();
        Map<String, Integer> fifoQueue = new HashMap<>();
        int seqNumber = 0;
        Double seq = 0.0;

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            PriorityQueue<String> queue = new PriorityQueue<String>(30, new Comparator<String>(){
               public int compare(String a, String b){
                   return a.compareTo(b);
               }
            });

            fifo.put("11108", 0);
            fifo.put("11112", 0);
            fifo.put("11116", 0);
            fifo.put("11120", 0);
            fifo.put("11124", 0);

            while(true) {
                try {
                    Socket socket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    String input = in.readLine();
                    socket.close();

                    if (input != null) {
                        queue.offer(input);

                        int count = 0;
                        /*for(String text: queue) {
                            String[] msg = text.split(":");
                            ContentValues insert = new ContentValues();

                            insert.put("key", count);
                            insert.put("value", msg[2]);

                            Log.e(TAG, "saving file:");
                            Uri newUri = getContentResolver().insert(contentUri, insert);
                            publishProgress(count + " " + msg[2]);
                            count++;
                        }*/

                        List<String> tempList = new ArrayList<>();
                        while(queue.size() != 0){
                            tempList.add(queue.poll());
                        }

                        for(String text: tempList){
                            String[] msg = text.split(":");

                            if (msg.length == 4) {
                                if(fifo.get(msg[1]) == Integer.valueOf(msg[2])) {
                                    //finalSeq = Integer.valueOf(msg[2]) + 1;
                                    commitToProvider(msg);

                                    while (queue.size() != 0){
                                        if(fifo.get(msg[1]) == fifoQueue.get(msg[1])){
                                            String[] arr = {msg[0], msg[1]};
                                            commitToProvider(arr);
                                            queue.remove(msg[1]);
                                        }
                                    }
                                } else {
                                    fifoQueue.put(msg[1],Integer.valueOf(msg[2]));
                                }
                            } else if (msg.length == 2) {
                                Socket client = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(msg[1]));
                                PrintWriter out = new PrintWriter(client.getOutputStream(), true);

                                seq = Double.valueOf(seqNumber++) + processId.get(clientport);
                                out.println(msg[0] + ":" + msg[1] + ":" + String.valueOf(seq));

                                client.close();
                            } else {
                                ContentValues insert = new ContentValues();

                                insert.put("key", count);
                                insert.put("value", msg[2]);

                                Log.e(TAG, "saving file:");
                                Uri newUri = getContentResolver().insert(contentUri, insert);
                                publishProgress(count + " " + msg[2]);

                                count++;

                                if (maxSeq.containsKey(msg[2])) {
                                    if (maxSeq.get(msg[2]) < Double.valueOf(msg[0])) {
                                        maxSeq.put(msg[2], Double.valueOf(msg[0]));
                                    }
                                } else {
                                    maxSeq.put(msg[2], Double.valueOf(msg[0]));
                                }

                                if (seq == 5.0) {
                                    for (String port : remotePorts) {
                                        try {
                                            //Log.e(TAG, port);
                                            Socket tempsocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                                    Integer.parseInt(port));
                                            String msgToSend = msg[0] + ":" + clientport + ":" + maxSeq.get(msg[0]) + ":final";

                                            PrintWriter finish = new PrintWriter(tempsocket.getOutputStream(), true);
                                            finish.println(msgToSend);

                                            tempsocket.close();
                                        } catch (UnknownHostException e) {
                                            Log.e(TAG, "Client Task Unknown Host exception");
                                        } catch (IOException e) {
                                            Log.e(TAG, "Client Task socket IOException");
                                        }
                                    }
                                }
                            }
                        }

                        queue.addAll(tempList);
                    }
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ServerTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ServerTask IOException");
                }
            }
        }

        private void commitToProvider(String[] msg){
            ContentValues insert = new ContentValues();

            Integer number = fifo.get(msg[1]);
            insert.put("key", String.valueOf(number));
            insert.put("value", msg[0]);

            Log.e(TAG, "saving file:" + (number));
            Uri newUri = getContentResolver().insert(contentUri, insert);
            publishProgress(msg[0]);

            Integer temp = fifo.get(msg[1]) + 1;
            fifo.put(msg[1],temp);
        }

        protected void onProgressUpdate(String...strings) {
            final TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(strings[0].trim());
            tv.append("\n");
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {
        int seq = 0;
        @Override
        protected Void doInBackground(String... msgs) {
            for(String port: remotePorts){
                try {
                    //Log.e(TAG, port);
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));
                    String msgToSend = (seq++) + ":" + msgs[1] + ":" + msgs[0];

                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgToSend);

                    socket.close();
                }catch (UnknownHostException e){
                    Log.e(TAG, "Client Task Unknown Host exception");
                }catch(IOException e){
                    Log.e(TAG, "Client Task socket IOException");
                    //e.printStackTrace();
                }
            }
            return null;
        }
    }
}
