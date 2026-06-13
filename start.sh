#!/bin/bash
set -e

# 步骤1：检查 .env 是否存在；不存在时从示例文件创建，避免缺少配置直接启动失败
if [ ! -f .env ]; then
  echo "未找到 .env 文件，正在从 .env.example 创建..."
  cp .env.example .env
  echo "请编辑 .env 文件，填入 DEEPSEEK_API_KEY 和 ZHIPU_API_KEY 后重新运行此脚本"
  exit 1
fi

# 步骤2：检查必填 API Key 是否仍为占位符；防止服务启动后因密钥未配置而调用失败
if grep -q "your-deepseek-key" .env || grep -q "your-zhipu-key" .env; then
  echo "检测到 .env 中的 API Key 仍为占位符，请先填写真实密钥"
  exit 1
fi

# 步骤3：构建并启动所有 Docker Compose 服务；首次构建需要拉取依赖和制作镜像
echo "开始构建镜像（首次构建较慢，请耐心等待）..."
docker compose build

echo "启动服务..."
docker compose up -d

# 步骤4：查看核心服务状态；Milvus 等组件启动较慢，可通过 ps 和日志继续观察健康状态
echo "等待服务就绪（Milvus启动较慢，预计1-2分钟）..."
docker compose ps

echo ""
echo "启动完成！访问地址：http://localhost"
echo "查看日志：./logs.sh [服务名]"
echo "查看状态：docker compose ps"
