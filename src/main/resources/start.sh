# 1. 启动 MySQL
brew services start mysql
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS agent_system;"

# 2. 启动 Redis
brew services start redis

# 3. 启动 RabbitMQ
brew services start rabbitmq

# 4. 启动 Milvus（Docker）
docker run -d --name milvus-standalone \
  -p 19530:19530 \
  -p 9091:9091 \
  milvusdb/milvus:latest

# 5. 启动 Ollama（可选）
ollama serve &
ollama pull qwen3:7b