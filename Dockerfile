# 基础镜像
FROM selenium/standalone-chrome:103.0.5060.53-chromedriver-103.0.5060.53-grid-4.3.0-20220628

#RUN apt install net-tools
#RUN apt install --no-cache nss

# 声明服务端口
EXPOSE 33333/tcp
EXPOSE 7900/tcp

# 关掉没用的selenium监控页面的密码
ENV SE_VNC_NO_PASSWORD=1
# 调大selenium的最大会话数
ENV SE_NODE_MAX_SESSIONS=4
ENV SE_NODE_OVERRIDE_MAX_SESSIONS=true

# 将打包好的项目添加到镜像中
ADD app.jar /app/app.jar
ADD start.sh /app/start.sh

USER root

RUN chmod 777 -R /app

# 在supervisor中添加启动配置
ADD spider4videostation.conf /etc/supervisor/conf.d/spider4videostation.conf

USER seluser