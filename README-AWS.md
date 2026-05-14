# Cloud AI Assistant

Standalone Spring Boot + Spring AI + OpenAI RAG assistant.
AWS-ready with Docker, Elastic Beanstalk, and keyword search.

## Architecture

Browser/Client
  -> POST /api/ai/chat        (blocking chat with memory)
  -> GET  /api/ai/chat/stream (streaming SSE chat)
  -> GET  /api/ai/search?q=   (keyword search - no LLM)
  -> POST /api/ai/admin/reindex
  -> GET  /api/ai/health
  -> Spring Boot :8081
  -> Spring AI QuestionAnswerAdvisor (RAG)
  -> OpenAI gpt-4o-mini (chat) + text-embedding-3-small (embeddings)
  -> SimpleVectorStore (in-memory, persisted to vectorstore.json)

## Quick Start (local)

### Option 1 - Docker Compose (recommended)
```
export OPENAI_API_KEY=sk-...
docker-compose up --build
```

### Option 2 - Run directly
```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export OPENAI_API_KEY=sk-...
mvn clean package -DskipTests
java -jar target/cloud-ai-assistant-1.0.0.jar
```

## API Endpoints

### POST /api/ai/chat
Blocking chat with conversation memory.
```json
{ "message": "What is a SNIV?", "sessionId": "abc-123" }
```

### GET /api/ai/chat/stream?message=...&sessionId=...
Streaming SSE chat. Events: sources, token, done.

### GET /api/ai/search?q=SNIV&limit=5
Keyword search - returns matching doc chunks, no LLM call.
```json
{
  "query": "SNIV",
  "total": 3,
  "results": [
    { "source": "sniv-personnel-guide.txt", "snippet": "...A SNIV is a request...", "matchedKeyword": "SNIV" }
  ]
}
```

### DELETE /api/ai/chat/memory/{sessionId}
Clear conversation memory for a session.

### POST /api/ai/admin/reindex
Hot-reload all docs without restart.

### GET /api/ai/health
Health check for AWS load balancer.

## AWS Deployment Guide

### What you will learn
- IAM: create users, roles, and policies
- EC2: virtual machines in the cloud
- Elastic Beanstalk: deploy Java apps without managing servers
- S3: store your vectorstore.json and docs
- ECR: store your Docker image
- ECS (optional): run containers at scale
- CloudWatch: monitor logs and health

### Step 1 - Prerequisites
1. Create an AWS account at https://aws.amazon.com
2. Install AWS CLI: https://aws.amazon.com/cli/
3. Configure CLI: aws configure (enter Access Key, Secret, region us-east-1)
4. Install Elastic Beanstalk CLI: pip install awsebcli

### Step 2 - Set your OpenAI key in AWS
```
aws ssm put-parameter \
  --name "/cloud-ai-assistant/OPENAI_API_KEY" \
  --value "sk-..." \
  --type SecureString
```

### Step 3 - Build the jar
```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn clean package -DskipTests
```

### Step 4 - Deploy to Elastic Beanstalk
```
eb init cloud-ai-assistant --platform java-21 --region us-east-1
eb create cloud-ai-assistant-env
eb deploy
eb open
```

### Step 5 - Deploy with Docker to ECS
```
# Create ECR repository
aws ecr create-repository --repository-name cloud-ai-assistant

# Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <your-account-id>.dkr.ecr.us-east-1.amazonaws.com

# Build and push
docker build -t cloud-ai-assistant .
docker tag cloud-ai-assistant:latest <your-account-id>.dkr.ecr.us-east-1.amazonaws.com/cloud-ai-assistant:latest
docker push <your-account-id>.dkr.ecr.us-east-1.amazonaws.com/cloud-ai-assistant:latest
```

### Step 6 - Monitor with CloudWatch
```
# View live logs
eb logs --all

# Or via AWS Console: CloudWatch > Log Groups > /aws/elasticbeanstalk/...
```

### Estimated AWS Cost (free tier eligible)
- t3.micro EC2: free for 12 months
- Elastic Beanstalk: free (you pay for EC2 only)
- S3: first 5GB free
- Total for learning/demo: ~$0-5/month
