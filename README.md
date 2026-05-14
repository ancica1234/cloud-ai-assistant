# Cloud AI Assistant

A standalone AI-powered knowledge base assistant built with Spring Boot, Spring AI, and React.
Supports natural language chat with conversation memory, streaming responses, keyword search, and source citations.
Designed to run locally with Ollama or in the cloud with OpenAI.

## Features

- **AI Chat** — ask questions in natural language, get answers grounded in your documentation
- **Streaming responses** — tokens appear as they are generated, no waiting
- **Conversation memory** — follow-up questions work correctly (per session)
- **Keyword search** — instant document lookup with keyword highlighting, no LLM call needed
- **Source citations** — every answer shows which document it came from
- **Hot reindex** — add documents and reload without restarting
- **Docker ready** — Dockerfile and docker-compose included
- **AWS ready** — Elastic Beanstalk config and deployment guide included

## Architecture

```
React UI (port 3000)
  -> Spring Boot API (port 8081)
  -> Spring AI RAG (QuestionAnswerAdvisor)
  -> Ollama / OpenAI (chat + embeddings)
  -> SimpleVectorStore (in-memory, persisted to vectorstore.json)
  -> docs/ folder (your knowledge base)
```

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- Node 18+
- [Ollama](https://ollama.com) with `mistral` and `nomic-embed-text` models

### 1. Pull Ollama models
```bash
ollama pull mistral
ollama pull nomic-embed-text
```

### 2. Start the backend
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn clean package -DskipTests
java -jar target/cloud-ai-assistant-1.0.0.jar
```
Backend runs on http://localhost:8081

### 3. Start the UI
```bash
cd ui
npm install
npm start
```
UI runs on http://localhost:3000

### 4. Add your own documents
Drop `.txt` or `.pdf` files into the `docs/` folder, then call:
```bash
curl -X POST http://localhost:8081/api/ai/admin/reindex
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ai/chat` | Blocking chat with memory |
| GET | `/api/ai/chat/stream` | Streaming SSE chat |
| GET | `/api/ai/search?q=keyword` | Keyword search, no LLM |
| DELETE | `/api/ai/chat/memory/{id}` | Clear session memory |
| POST | `/api/ai/admin/reindex` | Hot reload documents |
| GET | `/api/ai/health` | Health check |

## Docker

```bash
export OPENAI_API_KEY=sk-...
docker-compose up --build
```

## Deploy to AWS

See [README-AWS.md](README-AWS.md) for a step-by-step guide covering:
- Elastic Beanstalk deployment
- Docker image push to ECR
- ECS container deployment
- Secrets management with SSM Parameter Store
- Monitoring with CloudWatch

## Using OpenAI instead of Ollama

```bash
export OPENAI_API_KEY=sk-...
java -jar target/cloud-ai-assistant-1.0.0.jar --spring.profiles.active=openai
```

## Project Structure

```
cloud-ai-assistant/
  src/main/java/com/cloudai/assistant/
    CloudAiAssistantApplication.java
    config/
      ClearableVectorStore.java   # extends SimpleVectorStore with clear() + size()
      RagConfig.java              # loads docs, builds vector store
      CorsConfig.java             # configurable CORS
    controller/
      AiController.java           # all REST endpoints
  src/main/resources/
    application.properties        # Ollama config (local default)
    application-openai.properties # OpenAI config (AWS profile)
  docs/                           # knowledge base documents
  ui/                             # React frontend
  Dockerfile                      # multi-stage build
  docker-compose.yml              # local container run
  .ebextensions/                  # AWS Elastic Beanstalk config
  README-AWS.md                   # AWS deployment guide
```
