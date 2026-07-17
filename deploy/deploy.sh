#!/bin/bash
set -e

# ============================================
# 退运智录 一键部署脚本
# 在服务器上执行，将本地打包好的 jar + dist 部署上线
# ============================================

DEPLOY_DIR="/www/wwwroot/returnvision"
BACKEND_DIR="${DEPLOY_DIR}/returnvision-backend"
FRONTEND_DIR="${DEPLOY_DIR}/returnvision-frontend"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================="
echo "  退运智录 部署脚本"
echo "========================================="

# ---------- 1. 检查 .env 文件 ----------
if [ ! -f "${SCRIPT_DIR}/.env" ]; then
  echo "[错误] 未找到 .env 文件！请先执行："
  echo "  cp ${SCRIPT_DIR}/.env.example ${SCRIPT_DIR}/.env"
  echo "  并填入真实密钥后重新运行本脚本"
  exit 1
fi
echo "[1/5] 加载环境变量..."

# ---------- 2. 检查必需文件 ----------
echo "[2/5] 检查部署文件..."

if [ ! -f "${BACKEND_DIR}/returnvision-backend-1.0.0.jar" ]; then
  echo "[错误] 后端 jar 不存在：${BACKEND_DIR}/returnvision-backend-1.0.0.jar"
  echo "请先在本地执行 mvn clean package -DskipTests，然后上传 jar 到服务器"
  exit 1
fi

if [ ! -d "${FRONTEND_DIR}/dist" ]; then
  echo "[错误] 前端 dist 目录不存在：${FRONTEND_DIR}/dist"
  echo "请先在本地执行 npm run build，然后上传 dist/ 目录到服务器"
  exit 1
fi

# ---------- 3. 复制部署配置到服务器目录 ----------
echo "[3/5] 同步部署配置文件..."
cp "${SCRIPT_DIR}/docker-compose.yml" "${DEPLOY_DIR}/"
cp "${SCRIPT_DIR}/.env" "${DEPLOY_DIR}/"
mkdir -p "${DEPLOY_DIR}/returnvision-backend"
mkdir -p "${DEPLOY_DIR}/nginx/user_conf.d"
cp "${SCRIPT_DIR}/returnvision-backend/Dockerfile" "${DEPLOY_DIR}/returnvision-backend/"
cp "${SCRIPT_DIR}/nginx/user_conf.d/default.conf" "${DEPLOY_DIR}/nginx/user_conf.d/"

echo "  配置文件同步完成"

# ---------- 4. 拉取镜像并重新构建 ----------
echo "[4/5] 重新构建并启动 Docker 容器..."
cd "${DEPLOY_DIR}"
docker compose pull
docker compose up -d --build

# ---------- 5. 检查状态 ----------
echo "[5/5] 检查运行状态..."
sleep 3

echo ""
echo "---------- 容器状态 ----------"
docker compose ps

echo ""
echo "---------- nginx 证书状态 ----------"
docker compose exec frontend certbot certificates 2>/dev/null || echo "  证书尚未签发（Append 启动后自动触发）"

echo ""
echo "========================================="
echo "  部署完成！"
echo "  http://returnvision.jxing.tech"
echo "========================================="
echo ""
echo "[提示] 查看日志："
echo "  docker compose -f ${DEPLOY_DIR}/docker-compose.yml logs -f backend"
echo "  docker compose -f ${DEPLOY_DIR}/docker-compose.yml logs -f frontend"
echo ""
echo "[提示] 如需强制续期证书："
echo "  docker compose exec frontend certbot renew --force-renewal"
