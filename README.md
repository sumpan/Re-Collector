[TOC]

# 项目说明

在日志采集的过程中,难免会碰到AIX、HP-UX这样的主机,这种时候,nxlog其实是非常合适的解决方案
但是当手头上又没有合适的Agent的时候,就可以采用Java这种万金油开发语言所开发出来的Agent了。
挑选了非常多开源的Java日志采集Agent,Graylog的Collector做的是最合适的,采用简单,轻量的模式,CPU和内存占用率都
控制的挺好,可惜Graylog废弃了Java编写的Collector,转而采用Go编写日志采集的Agent,本项目Fork出了Collector的最后的版本,
在此基础上进行问题的修复以及功能的衍生。主要用于解决(一些奇奇怪怪的服务器)日志采集的问题

# 构建方式

```
mvn clean package assembly:single -DskipTests
```

# 启动方式

```
java -jar -Xms12m -Xmx64m  ./target/graylog-collector-0.5.1-SNAPSHOT.jar run -f ./config/collector.conf

#检查内存泄漏的时候用
java -jar -Xms12m -Xmx64m  -Dio.netty.leakDetection.level=advanced ./target/graylog-collector-0.5.1-SNAPSHOT.jar run -f ./config/collector.conf
```

# 配置示例

```
message-buffer-size = 128

inputs {
  local-syslog {
    type = "file"
    path = "/var/log/syslog"
  }
  apache-access {
    type = "file"
    path = "/var/log/apache2/access.log"
    outputs = "gelf-tcp,console"
  }
  test-log {
    type = "file"
    path = "logs/file.log"
  }
}

outputs {
  gelf-tcp {
    type = "gelf"
    host = "127.0.0.1"
    port = 12201
    client-queue-size = 512
    client-connect-timeout = 5000
    client-reconnect-delay = 1000
    client-tcp-no-delay = true
    client-send-buffer-size = 32768
    inputs = "test-log"
  }
  console {
    type = "stdout"
  }
}
```
## 全局配置

| 参数                  | 说明                                       |
| ------------------- | ---------------------------------------- |
| message-buffer-size | 消息处理链的队列长度，默认是128。队列长度越长，输入的性能会越高，不过会带来Agent内存占用的问题 |
| enable-registration | 是否启用sidecar客户端管理，默认为true，请先使用false，没有实现客户端配置管理，只是上报了状态和度量 |
| server-url          | Graylog的SideCarUrl地址                     |
| collector-id        | 采集器唯一标志存放路径                              |
| host-name           | 主机名称，默认不填即可，可复写主机名                       |
| heartbeat-interval  | 心跳检查间隔，单位为秒， 默认5 秒，                      |

## 输入

输入配置在input块内

```
inputs {
....
}
```

| 参数                 | 说明                                       | 默认值     |
| ------------------ | ---------------------------------------- | ------- |
| type               | 输入的类型 必填，file、windows-eventlog、database  |         |
| path               | 文件采集的路径                                  |         |
| charset            | 字符集转换                                    | utf-8   |
| content-splitter   | 单行匹配还是多行匹配，newline 单行匹配，以\r为换行符，pattern，采用正则匹配，正则写在content-splitter-pattern配置里面 | newline |
| outputs            | 输出的路由，指定输出的时候使用，不配置则所有的output都会触发一次，多个输出以逗号分隔 |         |
| path-glob-root     | 是否采用glob模式采集文件，path和path-glob-root仅能用一种  |         |
| path-glob-pattern  | glob模式的通配符，glob采集模式下才可使用                 |         |
| source-name        | WindowsEventlog的事件源，可用的有Application, System, Security |         |
| poll-interval      | WindowsEventlog的采集间隔                     |         |
| reader-interval    | 采集器的采集间隔                                 | 100毫秒   |
| reader-buffer-size | 采集器的读取缓冲大小                               | 102400  |
| message-fields     | 日志消息的附加字段，配置示例                           |         |
| init-sql           | [database专用]从哪条记录开始向后读取                  |         |
| db-driver-path     | [database专用]数据库驱动Jar包的名称，驱动包放在jar包同目录的plugin目录下 |         |
| id-field           | [database专用]数据库自增列的名称                    |         |
| key-type           | [database专用]自增列类型，可选：id、timestamp        |         |
| sql                | [database专用]数据库记录采集的SQL，注意分页，不然一次采集的数量会太多 |         |
| db-connection-url  | [database专用]数据库的连接窜                      |         |
| db-driver          | [database专用]数据库驱动名类型，如com.mysql.jdbc.Driver |         |
| db-user            | [database专用]可把用户配置在这里，也可以配置在连接窜          |         |
| db-password        | [database专用]可把密码配置在这里，也可以配置在连接窜          |         |
| db-sync-time       | [database专用]采集数据间隔，单位为分钟，默认为1            |         |

message-fields 配置示例

```
input {
    message-fields{
            sample= "demo"
            demo= "1234"
    }
}
```



指定文件输入

```
local-syslog {
    type = "file"
    path = "/var/log/syslog"
    charset = "utf-8"
    content-splitter = "newline"
}
```

带路由指定文件输入

```
  apache-access {
    type = "file"
    path = "/var/log/apache2/access.log"
    outputs = "gelf-tcp,console"
  }
```

采集Windows Eventlog

```
win-application {
    type = "windows-eventlog"
    source-name = "Application"
    poll-interval = 1s
  }
```

采集数据库的记录

```
database {
    type = "database"
    db-driver="com.mysql.jdbc.Driver"
    db-connection-url="jdbc:mysql://localhost:3306/guns?user=root"
    sql="select * from login_log dept where createtime>'{id_field}'"
    init-sql="select * from login_log order by id asc limit 1"
    key-type="timestamp"
    id-field="createtime"
    db-driver-path="mysql-connector-java-5.1.28.jar"
  }
```

### 常用JDBC连接方式

#### MySQL

Driver:com.[mysql](http://lib.csdn.net/base/mysql).jdbc.Driver

URL:jdbc:mysql://localhost:3306/test?user=root&password=123456&useUnicode=true&characterEncoding=utf8&autoReconnect=true&failOverReadOnly=false

#### Oracle

Driver: [oracle](http://lib.csdn.net/base/oracle).jdbc.driver.OracleDriver

URL: jdbc:oracle:thin:@127.0.0.1:1521:dbname



## 输出

输出的配置都配在输出块中

```
outputs{

}
```



| 参数                         | 说明                                | 值          |
| -------------------------- | --------------------------------- | ---------- |
| type                       | 输出类型，可选 gelf，stdout ,kafka        |            |
| host                       | 输出的目标主机ip                         |            |
| port                       | 输出的目标主机端口                         | 1024-65535 |
| client-tls                 | [gelf输出专用]是否采用tls进行传输  false/true |            |
| client-tls-cert-chain-file | [gelf输出专用]tls认证文件路径               |            |
| client-tls-verify-cert     | [gelf输出专用]是否校验证书  true/false      |            |
| client-queue-size          | [gelf输出专用]gelf输出队列大小 512          |            |
| client-connect-timeout     | [gelf输出专用]gelf连接的超时时间             |            |
| client-reconnect-delay     | [gelf输出专用]gelf连接延时时间              |            |
| client-tcp-no-delay        | [gelf输出专用]是否启用tcp no-delay模式      |            |
| client-send-buffer-size    | [gelf输出专用]发送的buffer长度             |            |
| protocol                   | [gelf输出专用]发送的协议，默认TCP，可选UDP       |            |
| topic                      | [kafka输出专用] kafka的主题名称，必填         |            |





## 监控

### 吞吐量度量

监控配置在metrics模块内

```
metrics{
  ...
}
```

| 参数             | 说明                   | 默认值   |
| -------------- | -------------------- | ----- |
| enable-logging | 是否启用自监控日志,true/false | false |
| log-duration   | 日志打印的间隔              | 60000 |

启用性能监控日志

```
metrics{
  enable-logging = true
  log-duration = 1000
}
```

### 资源使用监控

资源使用监控配置在debug模块内

```
debug {
	....
}
```

| 参数                       | 说明                   | 默认值    |
| ------------------------ | -------------------- | ------ |
| memory-reporter          | 是否启用内存监控  true/false | false  |
| memory-reporter-interval | 内存监控报告周期             | 1000毫秒 |

配置示例

```
debug {
    memory-reporter= true
    memory-reporter-interval= 5000
}
```