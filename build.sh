#!/bin/sh
#############################
# Spider4VideoStation 桌面版打包脚本
# 依赖：Java 8（编译）、JDK 14+ 的 jpackage（打包）
# 输出：build/package/Spider4VideoStation/Spider4VideoStation.exe（绿色免安装，双击运行）
#############################
set -e

# 切换至项目根目录
shellDir=`dirname $0`
cd "$shellDir"
shellDir=`pwd`
echo "项目路径: $shellDir"

# 准备 JDK 21 的 jpackage 路径
if [ -n "$JPACKAGE_HOME" ]; then
    JPACKAGE="$JPACKAGE_HOME/bin/jpackage"
elif [ -d "/d/Java/openjdk-21.0.2" ]; then
    JPACKAGE="/d/Java/openjdk-21.0.2/bin/jpackage"
elif command -v jpackage >/dev/null 2>&1; then
    JPACKAGE="jpackage"
else
    echo "错误: 找不到 jpackage。请设置 JPACKAGE_HOME 环境变量指向 JDK 14+ 目录"
    exit 1
fi
echo "jpackage: $JPACKAGE"

# 编译 jar
echo ">>> 编译 jar ..."
gradle clean bootJar

# 清理旧包
rm -rf build/package

# 打包 exe（app-image 模式 = 绿色免安装，内嵌 JRE）
echo ">>> 打包 exe ..."
"$JPACKAGE" \
    --name Spider4VideoStation \
    --input build/libs \
    --main-jar Spider4VideoStation.jar \
    --main-class org.springframework.boot.loader.JarLauncher \
    --type app-image \
    --dest build/package

# 输出
EXE_DIR="build/package/Spider4VideoStation"
ZIP_FILE="build/package/Spider4VideoStation.zip"
echo ""
echo "===== 打包完成 ====="
echo "输出目录: $EXE_DIR"

# 打包 zip（用 PowerShell，Windows 上 zip 命令可能不可用）
rm -f "$ZIP_FILE"
powershell.exe -NoProfile -Command "Compress-Archive -Path '$EXE_DIR' -DestinationPath '$ZIP_FILE' -Force"
echo "发布包: $ZIP_FILE"
echo "体积: `du -sh "$ZIP_FILE" | cut -f1`"
