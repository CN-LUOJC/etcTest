import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ETCInterface {
    private static final Logger LOGGER = Logger.getLogger(testOBU.class.getName());
    static Socket socket;
    public String carNumber;
    public String OBUId;
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final ReentrantLock dbLock = new ReentrantLock();//数据库添加锁
    public int number;
    public String IP;
    public String port;

    //获取时间
    static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//mysql的时间格式
    static Date date = new Date();
    static String time = formatter.format(date);
    static Connection conn;

    //发送方式
    public static String bytesToHexString(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();//使用StringBuilder动态数组方法
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x", bytes[i]));//使用format将字节格式化十六进制数，控制位数+补0
        }
        return sb.toString();
    }
    //接受方式
    public static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    //发送线程
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

    //接受、储存数据
    public void handleData() {
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
}
