#!/bin/bash

# 使用方式：
# ./logs.sh           查看所有服务日志
# ./logs.sh app       只看app服务日志
# ./logs.sh milvus    只看milvus日志（排查向量库问题）
if [ -z "$1" ]; then
  docker compose logs -f --tail=100
else
  docker compose logs -f --tail=100 "$1"
fi
