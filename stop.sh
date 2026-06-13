#!/bin/bash

# 停止所有 Docker Compose 服务并保留数据卷，确保下次启动时业务数据不丢失
docker compose down

# 注意：不要随意追加 -v 参数；docker compose down -v 会删除所有数据卷，包括 MySQL、Redis、Milvus、MinIO 等持久化数据

echo "服务已停止（数据卷保留，下次启动数据不丢失）"
echo "如需完全清除数据，执行：docker compose down -v"
