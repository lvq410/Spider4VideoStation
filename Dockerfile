# 基础镜像
FROM selenium/standalone-chrome:107.0.5304.87-chromedriver-107.0.5304.62-grid-4.6.0-20221104

# 声明服务端口
EXPOSE 33333/tcp
EXPOSE 7900/tcp

# 关掉没用的selenium监控页面的密码
ENV SE_VNC_NO_PASSWORD=1
# 调大selenium的最大会话数
ENV SE_NODE_MAX_SESSIONS=4
ENV SE_NODE_OVERRIDE_MAX_SESSIONS=true

# 调小虚拟浏览器资源消耗
#ENV SE_SCREEN_WIDTH=852
#ENV SE_SCREEN_HEIGHT=480
ENV SE_SCREEN_DEPTH=16

USER root

RUN apt-get update
RUN apt-get install -y vim
RUN apt-get install -y net-tools
RUN apt-get install -y iputils-ping

# 将打包好的项目添加到镜像中
ADD app.jar /app/app.jar
ADD start.sh /app/start.sh

RUN chmod 777 -R /app

# 在supervisor中添加启动配置
ADD spider4videostation.conf /etc/supervisor/conf.d/spider4videostation.conf
ADD ChromeExtensions /app/ChromeExtensions

#USER seluser