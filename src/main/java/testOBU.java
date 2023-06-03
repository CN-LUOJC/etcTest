import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

public class testOBU {
    //线程锁
    private static final ReentrantLock dbLock = new ReentrantLock();//数据库添加锁
    private static final ReentrantLock printLock = new ReentrantLock();//日志输出锁
    public static String IP1 = null;
    public static String IP2 = null;
    public static String port1 = null;
    public static String port2 = null;


    static Socket socket;
    static String OBUId;
    static String carNumber;
    private static final Logger LOGGER = Logger.getLogger(testOBU.class.getName());
    static int number = 0;
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

    //获取时间
    static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//mysql的时间格式
    static Date date = new Date();
    static String time = formatter.format(date);
    static Connection conn;
    String IP = null;
    String port = null;

    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException, SQLException {

        Logger logger = Logger.getLogger(testOBU.class.getName());
        FileHandler fileHandler = new FileHandler("D:/桌面/ETC.log");
        fileHandler.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });
        logger.addHandler(fileHandler);
        logger.setLevel(Level.INFO);

        //数据库
        try {
            final String DB_URL = "jdbc:mysql://127.0.0.1:3306/bookstore?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            final String USER = "root";
            final String PASS = "ljc552520";
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            Statement stmt;

            //创建数据表
            ResultSet resultSet;
            resultSet = conn.getMetaData().getTables(null, null, "collect", null);
            if (resultSet.next()) {
                System.out.println("数据库已存在");
                printLock.lock();
                try {
                    LOGGER.log(Level.INFO, "数据库已存在");
                } finally {
                    printLock.unlock();
                }
            } else {
                stmt = conn.createStatement();
                stmt.executeUpdate("create table collect(site int AUTO_INCREMENT not null,IP char(20) not null,port int not null ,OBU号 char(10) not null ,carNumber varchar(200) not null," +
                        "time datetime not null,primary key(site));");//unique
                System.out.println("数据库创建成功");
                printLock.lock();
                try {
                    LOGGER.log(Level.INFO, "数据库创建成功");
                } finally {
                    printLock.unlock();
                    resultSet.close();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        //读取IP地址和端口号
        String separator = System.getProperty("file.separator");
        BufferedReader in = new BufferedReader(new FileReader("D:" + separator + "桌面/ETC配置.txt"));
        String line;
        while ((line = in.readLine()) != null) {
            String[] parts = line.split(";");
            String parts1 = parts[0];
            String ports2 = parts[1];

            String[] part1 = parts1.split(",");
            IP1 = part1[0];
            port1 = part1[1];

            String[] part2 = ports2.split(",");
            IP2 = part2[0];
            port2 = part2[1];


            //设备1
            Thread socket1 = new Thread(() -> {
                testOBU test1 = new testOBU();
                try {
                    socket = new Socket(IP1, Integer.parseInt(port1));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                //接收线程、发送线程
                test1.ETCSend();
                try {
                    test1.handleData("1");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            socket1.start();


            //设备2
            Thread socket2 = new Thread(() -> {
                testOBU test2 = new testOBU();
                try {
                    socket = new Socket(IP2, Integer.parseInt(port2));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                //接收线程、发送线程
                test2.ETCSend();
                try {
                    test2.handleData("2");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            socket2.start();


            System.out.println("请输入请求：");
        }
    }

    public static String bytesToHexString(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();//使用StringBuilder动态数组方法
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x", bytes[i]));//使用format将字节格式化十六进制数，控制位数+补0
        }
        return sb.toString();
    }

    public static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    //发送数据
    public void ETCSend() {
        Thread send = new Thread(() -> {
            while (true) {
                try {
                    OutputStream outputStream;
                    Scanner scanner = new Scanner(System.in);
                    String data = scanner.nextLine();
                    outputStream = socket.getOutputStream();
                    outputStream.write(hexStringToByteArray(data));
                    System.out.println("发送成功");
                    LOGGER.log(Level.INFO,"输入的请求为："+ Arrays.toString(hexStringToByteArray(data)));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        send.start();
    }

    //接受数据、存储数据
    private void handleData(String name) throws IOException {
        Thread thread = new Thread(() -> {
            while (true) {
                InputStream inputStream;
                try {
                    inputStream = socket.getInputStream();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                byte[] bytes = new byte[1024];
                int len;
                try {
                    len = inputStream.read(bytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                String hexStr = bytesToHexString(bytes, len);
                System.out.println("收到的数据是：" + hexStr);
                LOGGER.log(Level.INFO, "收到的数据是：" + hexStr);
                System.out.println("长度为：" + hexStr.length());

                //获取OBU
                byte[] byte1 = new byte[len];
                if (hexStr.length() == 444) {
                    System.arraycopy(bytes, 4, byte1, 0, 4);
                    OBUId = bytesToHexString(byte1, 4);
                    System.out.println("OBU号为：" + OBUId);
                    LOGGER.info("OBU号为：" + OBUId);

                    //获取车牌号
                    byte[] bytes2 = new byte[len];
                    System.arraycopy(bytes, 36, bytes2, 0, 12);
                    carNumber = bytesToHexString(bytes2, 12);
                    System.out.println("车牌号为：" + carNumber);
                    LOGGER.log(Level.INFO, "车牌号为：" + carNumber);
                    System.out.println("请继续发送");

                    //写入数据库
                    judge(name);
                    try {
                        Class.forName(JDBC_DRIVER);
                        System.out.println("连接数据库...");// 打开链接
                        AtomicReference<PreparedStatement> ps = new AtomicReference<>(null);
                        String sql;
                        sql = "insert into collect values(?,?,?,?,?,?)";
                        //写入
                        dbLock.lock();
                        try {
                            assert false;
                            ps.set(conn.prepareStatement(sql));
                            ps.get().setString(1, String.valueOf(number));
                            ps.get().setString(2, IP);
                            ps.get().setString(3, port);
                            ps.get().setString(4, OBUId);
                            ps.get().setString(5, carNumber);
                            ps.get().setString(6, time);
                            ps.get().executeUpdate();
                        } finally {
                            dbLock.unlock();
                        }
                    } catch (SQLException se) {
                        // 处理 JDBC 错误
                        se.printStackTrace();
                    } catch (Exception e) {
                        // 处理 Class.forName 错误
                        e.printStackTrace();
                    }
                    System.out.println("添加成功");
                    LOGGER.log(Level.INFO, "添加数据成功");
                }
            }
        });
        thread.start();
    }
    public void judge(String testName){
        if(Objects.equals(testName, "1")){
            IP = IP1;
            port = port1;
        }
        if(Objects.equals(testName, "2")){
            IP = IP2;
            port = port2;
        }
    }
}