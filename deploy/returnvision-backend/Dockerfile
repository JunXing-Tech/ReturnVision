# 退运智录后端 Dockerfile
# 基于 Eclipse Temurin JDK 21 最小化 JRE 镜像
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# JVM 内存参数（根据服务器配置调整 -Xmx）
ENV JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

# 拷贝后端 jar（用户在本地打包后上传到服务器对应目录）
# 文件名与 pom.xml <artifactId>+<version> 保持一致
COPY returnvision-backend-1.0.0.jar app.jar

EXPOSE 8080

# 启动命令：所有配置通过环境变量注入（对应 application.yml 的 ${VAR} 占位符）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
