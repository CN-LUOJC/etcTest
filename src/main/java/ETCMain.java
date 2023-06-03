import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import java.io.IOException;
import java.net.Socket;
import java.sql.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

@Configuration
@PropertySource("classpath:/application.yml")
public class ETCMain extends ETCInterface{

    static Connection conn;

    //读取IP与端口号
    @Value("${IP1}")
    private String IP1;
    @Value("${PORT1}")
    private int PORT1;
    @Value("${IP2}")
    private String IP2;
    @Value("${PORT2}")
    private int PORT2;

    //线程锁
    private static final ReentrantLock printLock = new ReentrantLock();//日志输出锁
    private static final Logger LOGGER = Logger.getLogger(testOBU.class.getName());

    public static void main(String[] args) {

        //创建数据库
        try {
            final String DB_URL = "jdbc:mysql://localhost:3306/etc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            final String USER = "root";
            final String PASS = "root";
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

        Thread etc = new Thread(() -> {
            ETCMain test1 = new ETCMain();
            ETCMain test2 = new ETCMain();
            while (true) {
                try {
                    socket = new Socket(test1.IP1, test1.PORT1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //发送线程
                test1.ETCSend();
                //接收线程
                test1.handleData();


                try {
                    socket = new Socket(test1.IP2, test1.PORT2);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //发送线程
                test2.ETCSend();
                //接收线程
                test2.handleData();
            }

        });
        etc.start();
    }
}
