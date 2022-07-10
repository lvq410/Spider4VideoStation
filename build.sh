#!/bin/sh
#############################
#docker镜像构建与推送脚本
#本地构建要求有docker环境，默认jdk18，默认gradle5.0+
#文件夹名为镜像名，分支名为标签名
#############################
set -e

#切换至工作目录
shellDir=`dirname $0`
cd $shellDir
shellDir=`pwd`

#分支名为标签名
#tag=`git branch | grep '*' | awk -F ' ' '{print $2}'`
tag='dev8'
echo "当前分支"$tag
echo "开始构建spider4videostation:"$tag"镜像,项目路径："$shellDir
#用gradle打出jar
gradle clean
gradle bootJar

#整理打镜像用文件
mkdir ./build/docker
mv ./build/libs/*.jar ./build/docker/app.jar
sed 's/^M//g' ./Dockerfile > ./build/docker/Dockerfile
sed 's/^M//g' ./start.sh > ./build/docker/start.sh
sed 's/^M//g' ./spider4videostation.conf > ./build/docker/spider4videostation.conf
cd ./build/docker
#打镜像
docker build -t lvq410/spider4videostation:$tag . 
#推镜像
docker push lvq410/spider4videostation:$tag
