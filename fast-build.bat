@echo off
chcp 65001 >nul

REM 加大控制台回滚缓冲区：cmd 默认只留 300 行，Maven 输出一多就会把真正的报错挤掉
mode con: cols=140 lines=9999 >nul 2>&1

cls
echo ==================================================
echo   Lion Resume Review 后端：打包 + 部署
echo ==================================================

REM 1. 指定自定义的 JAVA 环境（本项目 pom.xml 固定 java.version=17，必须用 JDK 17）
SET "JAVA_HOME=C:\Program Files\Java\jdk-17.0.9"

REM 2. 确保系统能立刻找到 mvn 命令
SET "MAVEN_HOME=D:\apache\maven\apache-maven-3.6.3"
SET "PATH=%MAVEN_HOME%\bin;%JAVA_HOME%\bin;%PATH%"

REM 3. 打印当前正在使用的 Java 版本，以防路径搞错
echo 正在使用的 Java 版本信息：
"%JAVA_HOME%\bin\java.exe" -version
echo.

REM 4. 打包：只 package 不 install（本项目是单模块，无需装到本地仓库，更快）
REM    package 会产出 target/resume-review-1.0.0.jar，即 compose 挂载的那个 jar
SET "BUILD_LOG=%~dp0build.log"
echo [正在打包] mvn clean package -DskipTests，日志同步写入：%BUILD_LOG%
echo.
powershell -NoProfile -Command "mvn clean package -DskipTests 2>&1 | Tee-Object -FilePath '%BUILD_LOG%'; exit $LASTEXITCODE"

REM 5. 先判断打包结果：失败就直接中止，绝不能拿旧 jar 去部署
IF %ERRORLEVEL% NEQ 0 (
    echo.
    echo ==================================================
    echo   [失败] 打包出错，已中止部署。
    echo   完整日志已保存到：%BUILD_LOG%
    echo   也可以用鼠标在本窗口向上滚动查看报错。
    echo ==================================================
    pause
    exit /b 1
)

REM 5.1 确认 jar 真的产出了（Dockerfile 不再打 jar，缺了容器会起不来）
IF NOT EXIST "%~dp0target\resume-review-1.0.0.jar" (
    echo.
    echo ==================================================
    echo   [失败] 未找到 target\resume-review-1.0.0.jar，请检查打包产物。
    echo ==================================================
    pause
    exit /b 1
)

echo.
echo ==================================================
echo   [成功] 打包完成，开始构建镜像并部署容器...
echo ==================================================

REM 6. 只重建并启动后端服务（mysql / redis / milvus 等基础设施不受影响）
REM    jar 是挂载进容器的，第一次需要 --build 生成运行环境镜像，之后可只 restart
docker compose up -d --build backend

REM 7. 判断部署结果
IF ERRORLEVEL 1 (
    echo.
    echo ==================================================
    echo   [失败] 部署出错，请检查上面的 Docker 输出。
    echo ==================================================
    pause
    exit /b 1
)

echo.
echo ==================================================
echo   [成功] 全部完成！服务地址：http://localhost:8080
echo   查看状态：docker compose ps
echo   查看日志：docker compose logs -f backend
echo   后续只换 jar 时：mvn -DskipTests package 后执行 docker compose restart backend
echo ==================================================
pause
