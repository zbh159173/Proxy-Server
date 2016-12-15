package proxy;





import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.net.*;


/**
 * Created by Binhe Zhou on 2016/11/29.
 */
public class RequestThread implements Runnable {

    private Map<String, String> header = new HashMap();//存储除请求行外的请求报文

    private BufferedInputStream clientInput;  //用户输入流

    private DataInputStream serverInput;  //服务器输入流

    private Socket clientSocket;       //proxy与client的socket

    private Socket chatSocket;         //proxy与server的socket

    private String url_for_sending = null;    //网页文件路径

    private String requestMethod = null;      //请求头的HTTP方法

    private String HttpVersion = null;        //HTTP版本

    private String fileURL = null;            //完整的URL

    private int flag_cache = 0;        //本地有无缓存的标记

    private int flag_for_interrupt_thread = 0;   //要不要终止线程的标记

    private byte[] response = null;    //从server返回的数据

    private ByteArrayOutputStream bo = new ByteArrayOutputStream(); //用于储存图片、视频的数据流

    private String host = null;          //可用于解析ip等等

    private int flag_list = 0;           //是否访问了黑名单中的网站

    private int flag_socket = 0;         //proxy与server的socket是否关闭

    public RequestThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void clientToProxy() throws IOException {
        //writename将所有的request保存到文件中，可能会有空文件情况，暂未解决
        File writename = new File("C:\\Users\\zbh\\Desktop\\record\\record" + Proxy.count + ".txt");
        //File writename = new File("C:\\Users\\Ningyu He\\Desktop\\Proxy\\src\\Proxy\\record" + Proxy.count + ".txt");
        writename.createNewFile();
        BufferedWriter outrecord = new BufferedWriter(new FileWriter(writename));

        File file = new File("C:\\Users\\zbh\\Desktop\\lalala\\list.txt");
        // 黑名单
        //File file = new File("C:\\Users\\Ningyu He\\Desktop\\Proxy\\src\\Proxy\\list.txt");
        FileInputStream fis = new FileInputStream(file);
        String list = null;
        byte[] bytes = new byte[1024];
        int length = 0;
        while ((length = fis.read(bytes)) != -1) {
            list += new String(bytes, 0, length);
        }
        fis.close();

        int tempReader;
        int lineNumber = 0;
        int isEndOfRequest = 0;
        String key = new String();
        String value = new String();
        URL url = null;

        StringBuilder request = new StringBuilder();
        clientInput = new BufferedInputStream(clientSocket.getInputStream());

        //以字节为单位从流中读入信息
        while ((tempReader = clientInput.read()) != -1) {
            if ((char) (tempReader) == '\r' || (char) (tempReader) == '\n') {
                isEndOfRequest++;
                if (isEndOfRequest == 2) {//连续读到\r\n说明已经到达行末，处理这一行
                    String requestHeadline = request.toString();
                    lineNumber += 1;
                    if (lineNumber == 1) {
                        requestMethod = requestHeadline.split(" ")[0];
                        fileURL = requestHeadline.split(" ")[1];//完整的有文件路径、主机名的url地址
                        if(!fileURL.toLowerCase().startsWith("http://")){
                            fileURL ="http://" + fileURL;
                        }
                        url = new URL(fileURL);
                        host = url.getHost();

                        if (list != null && list.contains(host)) {                //黑名单
                            flag_list = 1;
                            File temp = new File("C:\\Users\\zbh\\Desktop\\sorry.html");
                            //File temp = new File("C:\\Users\\Ningyu He\\Desktop\\Proxy\\src\\Proxy\\sorry.html");
                            FileInputStream fileInputStream = new FileInputStream(temp);
                            byte[] htmlTempByte = new byte[1024];
                            int htmlLength;
                            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(clientSocket.getOutputStream());
                            byte[] tempByte = "HTTP 200 OK\r\n\r\n".getBytes();
                            bufferedOutputStream.write(tempByte);
                            while ((htmlLength = fileInputStream.read(htmlTempByte)) != -1) {
                                bufferedOutputStream.write(htmlTempByte, 0, htmlLength);
                            }
                            bufferedOutputStream.flush();
                            bufferedOutputStream.close();
                            fileInputStream.close();
                            return;
                        }

                        if (!requestMethod.toLowerCase().equals("get") && !requestMethod.toLowerCase().equals("post")) {
                            if (writename.exists()) {
                                writename.delete();
                            }
                            Proxy.count -= 1;
                            flag_for_interrupt_thread = 1;
                            return;
                        }                  //对于请求不是get或者post的数据包，直接舍弃

                                           //有没有缓存
                        if(!Proxy.header_1.isEmpty()) {
                            for (Map.Entry<String, byte[]> entry : Proxy.header_1.entrySet()) {//header_1是一个键值对为fileurl和完整响应报文的映射表
                                if (entry.getKey().equals(fileURL)) {
                                    flag_cache = 1;                      //有缓存的标记
                                    response = entry.getValue();         //这个for循环判断本地有无缓存
                                    break;
                                }
                            }
                        }


                        url_for_sending = url.getFile();//用URL类将文件路径提取出来，成为标准的请求报头，没有主机名
                        HttpVersion = requestHeadline.split(" ")[2];
                    }
                    if (lineNumber > 1) {
                        key = requestHeadline.split(": ")[0].toLowerCase();
                        value = requestHeadline.split(": ")[1].toLowerCase();
                        header.put(key, value);
                        if(header.containsKey("accept-encoding")) header.remove("accept-encoding");
                    }
                    request.delete(0, request.length());//将StringBuilder清空，以便下一行接受
                }
                if (isEndOfRequest == 3) {//如果收到了3个连续的\r或\n，说明已到请求行末尾，可以结束
                    break;
                }
            } else {//未读到行末的换行符，将读到的字节转换成字符添加到动态StringBuilder中
                isEndOfRequest = 0;
                request.append((char) tempReader);
            }
        }
        header.put("connection", "close");           //need to add this "connection: close"

        outrecord.write("HttpVersion" + ":" + HttpVersion + "\r\n");
        for (Map.Entry<String, String> entry : header.entrySet()) {
            if (entry.getKey().toLowerCase().equals("host") ||
                    entry.getKey().toLowerCase().equals("accept-language") ||
                    entry.getKey().toLowerCase().equals("user-agent")) {
                outrecord.write(entry.getKey() + ":" + entry.getValue() + "\r\n");
            }
        }
        //获得服务器ip
        InetAddress address = InetAddress.getByName(host);
        String ip = address.getHostAddress();
        outrecord.write("IP" + ":" + ip + "\r\n");
        outrecord.write("\r\n");
        outrecord.flush();
        outrecord.close();
    }

    private void proxyToServer() {
        try {
            if(header.isEmpty()) return;
            else  chatSocket = new Socket(header.get("host"), 80);
            PrintWriter writer = new PrintWriter(chatSocket.getOutputStream());
            writer.print(requestMethod + " " + url_for_sending + " " + HttpVersion + "\r\n");
            for (Map.Entry<String, String> entry : header.entrySet()) {
                writer.print(entry.getKey() + ":" + entry.getValue() + "\r\n");
            }
            writer.print("\r\n");
            writer.flush();
            //writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void serverToProxy() throws IOException {
        try {
            if (chatSocket.isClosed() && !chatSocket.isConnected()) {//处理socket关闭的异常情况
                System.out.println("Socket is closed");
                flag_socket = 1;
                return;
            }

            File writename = new File("C:\\Users\\zbh\\Desktop\\cache\\response" + Proxy.count + ".txt");
            //File writename = new File("C:\\Users\\Ningyu He\\Desktop\\Proxy\\src\\Proxy\\record" + Proxy.count + ".txt");
            writename.createNewFile();
            BufferedWriter outresponse = new BufferedWriter(new FileWriter(writename));

            serverInput = new DataInputStream(chatSocket.getInputStream());

            int length;
            byte[] tempByteArray = new byte[1024];
            while ((length = serverInput.read(tempByteArray)) != -1) {
                bo.write(tempByteArray, 0, length);
            }//bo是动态比特数组


            response = bo.toByteArray();
            String lalala = new String(response);

            if(lalala.contains("Content-Type: text/html")){
                /*int i;
                int t;
                String[] numberlist = new String[10];
                //String string_1 = "<script>alert(\"This page is relayed via proxy\")</script>";
                int content_length = string_1.length();
                int location_1 = lalala.indexOf("Content-Length: ");
                int location_2;
                for(i = location_1; !lalala_1[i].equals("\n") ;i++)
                    ;
                location_2 = i - 1;
                System.out.println(location_1);
                System.out.println(location_2);
                System.out.println(lalala_1[location_2]);
                for(i = location_1; !lalala_1[i].equals(" ") ;i++)
                    ;
                i+=1;
                for(t = 0;i < location_2;i++,t++){
                    numberlist[t] = lalala_1[i];
                }
                System.out.println(numberlist[0] + numberlist[1]);
                String number = "";            //原来的长度

                number += numberlist[0];

                System.out.println(number);
                int true_number = Integer.parseInt(number);
                true_number += content_length;
                String final_number = true_number + "";           //把这个加回去
                String left_ideal = lalala.split(number)[0];
                String right_ideal = lalala.split(number)[1];
                lalala = left_ideal + final_number + right_ideal;
                lalala_1 = lalala.split("");

                int location = lalala.indexOf("</body");*/

                int i;
                String string_1 = "<script>alert(\"proxy\")</script>";
                String[] lalala_1 = lalala.split("");

                StringBuilder sb = new StringBuilder(lalala);


                for(i = 0;i < lalala_1.length;i++) {
                    if (lalala_1[i].equals("<") && lalala_1[i+1].equals("/") &&
                            lalala_1[i+2].equals("b") && lalala_1[i+3].equals("o") &&
                            lalala_1[i+4].equals("d") && lalala_1[i+5].equals("y")){
                        break;
                    }
                }
                System.out.println(i);
                System.out.println(lalala.length());
                if(i != lalala_1.length) {
                    sb.insert(i , string_1);
                }
                //sb.insert(location,string_1);
                String lalala_3 = sb.toString();
                response = lalala_3.getBytes();
                outresponse.write(lalala_3);
            }
            else {
                outresponse.write(lalala);
            }


            if(response != null && response.length > 0 && fileURL != null){
                Proxy.header_1.put(fileURL, response);
            }
            outresponse.flush();
            outresponse.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void proxyToClient() throws IOException {
        try {
            BufferedOutputStream writeAll = new BufferedOutputStream(clientSocket.getOutputStream());
            writeAll.write(response);//传给浏览器比特数组，可以浏览图片和视频
            writeAll.flush();
            writeAll.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void proxyToClientCache() throws IOException {
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(clientSocket.getOutputStream());

            bufferedOutputStream.write(response);
            System.out.println("网页由缓存转发");
            String lalala = "connection: close";
            bufferedOutputStream.write(lalala.getBytes());
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        try {
            Proxy.count += 1;
            clientToProxy();
            if (flag_for_interrupt_thread == 1) {       //对于不是get和post的数据包，终止线程舍去
                return;
            }
            if(flag_list == 1){                         //如果访问了黑名单中的网站
                return;
            }
            if (flag_cache == 1) {                       //如果本地有缓存
                proxyToClientCache();
            } else if (flag_cache == 0) {                   //如果本地没有缓存
                proxyToServer();
                serverToProxy();
                if(flag_socket == 1){
                    return;
                }
                proxyToClient();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
