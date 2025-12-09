# Simple AWS ECS Deployment Guide (Testing)

Quick deployment for testing - backend and frontend services communicate directly without load balancer.

## Prerequisites

- AWS Account
- AWS CLI configured
- Docker installed
- Account ID ready: `aws sts get-caller-identity --query Account --output text`

## Step 1: Create ECR Repositories

```bash
# Backend
aws ecr create-repository --repository-name ytdownload-backend --region us-east-1

# Frontend  
aws ecr create-repository --repository-name ytdownload-frontend --region us-east-1
```

## Step 2: Login to ECR and Push Images

```bash
# Login
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  $(aws sts get-caller-identity --query Account --output text).dkr.ecr.us-east-1.amazonaws.com

# Save account ID
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Build and push backend
docker build -f backend/Dockerfile -t ytdownload-backend:latest .
docker tag ytdownload-backend:latest $ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-backend:latest
docker push $ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-backend:latest

# Build and push frontend
docker build -f frontend/Dockerfile -t ytdownload-frontend:latest .
docker tag ytdownload-frontend:latest $ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-frontend:latest
docker push $ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-frontend:latest
```

## Step 3: Create ECS Cluster

```bash
aws ecs create-cluster --cluster-name ytdownload-cluster --region us-east-1
```

## Step 4: Create CloudWatch Log Groups

```bash
aws logs create-log-group --log-group-name /ecs/ytdownload-backend
aws logs create-log-group --log-group-name /ecs/ytdownload-frontend
```

## Step 5: Register Task Definitions

### Backend Task Definition

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

cat > /tmp/backend-task.json << EOF
{
  "family": "ytdownload-backend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "containerDefinitions": [
    {
      "name": "backend",
      "image": "$ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-backend:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SERVER_PORT",
          "value": "8080"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/ytdownload-backend",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ],
  "executionRoleArn": "arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole"
}
EOF

aws ecs register-task-definition --cli-input-json file:///tmp/backend-task.json --region us-east-1
```

### Frontend Task Definition

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

cat > /tmp/frontend-task.json << EOF
{
  "family": "ytdownload-frontend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "containerDefinitions": [
    {
      "name": "frontend",
      "image": "$ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-frontend:latest",
      "portMappings": [
        {
          "containerPort": 80,
          "protocol": "tcp"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/ytdownload-frontend",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ],
  "executionRoleArn": "arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole"
}
EOF

aws ecs register-task-definition --cli-input-json file:///tmp/frontend-task.json --region us-east-1
```

## Step 6: Get VPC and Subnet Info

```bash
# Get default VPC
VPC_ID=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" \
  --query 'Vpcs[0].VpcId' --output text)

# Get subnets
SUBNETS=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" \
  --query 'Subnets[*].SubnetId' --output text | tr ' ' ',')

# Create security group
SG=$(aws ec2 create-security-group \
  --group-name ytdownload-sg \
  --description "Security group for ytdownload" \
  --vpc-id $VPC_ID \
  --query 'GroupId' --output text)

# Allow all traffic within security group
aws ec2 authorize-security-group-ingress \
  --group-id $SG \
  --protocol -1 \
  --source-security-group-id $SG

echo "VPC_ID: $VPC_ID"
echo "SUBNETS: $SUBNETS"
echo "SG: $SG"
```

Save these values for next steps.

## Step 7: Create Backend Service

```bash
# Set variables (from previous step)
VPC_ID="vpc-xxxxx"
SUBNETS="subnet-xxxxx,subnet-yyyyy"
SG="sg-xxxxx"

aws ecs create-service \
  --cluster ytdownload-cluster \
  --service-name ytdownload-backend \
  --task-definition ytdownload-backend:1 \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG],assignPublicIp=ENABLED}" \
  --region us-east-1
```

## Step 8: Get Backend Task IP

```bash
# Wait a few seconds for task to start
sleep 10

# Get task ID
TASK_ID=$(aws ecs list-tasks \
  --cluster ytdownload-cluster \
  --service-name ytdownload-backend \
  --query 'taskArns[0]' \
  --output text | cut -d'/' -f3)

# Get task details
aws ecs describe-tasks \
  --cluster ytdownload-cluster \
  --tasks $TASK_ID \
  --query 'tasks[0].attachments[0].details' \
  --output table

# Extract private IP (look for "privateIPv4Address")
BACKEND_IP=$(aws ecs describe-tasks \
  --cluster ytdownload-cluster \
  --tasks $TASK_ID \
  --query "tasks[0].attachments[0].details[?name=='privateIPv4Address'].value" \
  --output text)

echo "Backend IP: $BACKEND_IP"
echo "Backend URL: http://$BACKEND_IP:8080"
```

## Step 9: Create Frontend Service

Frontend needs to know backend URL. Update frontend to use backend IP:

```bash
# Frontend will connect to backend at: http://<BACKEND_IP>:8080

# If you want to use environment variable, update the frontend Dockerfile or pass as env var:
BACKEND_IP="10.0.x.x"  # From previous step

aws ecs create-service \
  --cluster ytdownload-cluster \
  --service-name ytdownload-frontend \
  --task-definition ytdownload-frontend:1 \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG],assignPublicIp=ENABLED}" \
  --region us-east-1
```

## Step 10: Get Frontend Access

```bash
# Wait a few seconds
sleep 10

# Get frontend task
TASK_ID=$(aws ecs list-tasks \
  --cluster ytdownload-cluster \
  --service-name ytdownload-frontend \
  --query 'taskArns[0]' \
  --output text | cut -d'/' -f3)

# Get public IP
FRONTEND_IP=$(aws ecs describe-tasks \
  --cluster ytdownload-cluster \
  --tasks $TASK_ID \
  --query "tasks[0].attachments[0].details[?name=='publicIPv4Address'].value" \
  --output text)

echo "Frontend URL: http://$FRONTEND_IP"
```

## Step 11: Check Logs

```bash
# Backend logs
aws logs tail /ecs/ytdownload-backend --follow

# Frontend logs  
aws logs tail /ecs/ytdownload-frontend --follow
```

## Step 12: Scale Services

```bash
# Increase backend to 2 instances
aws ecs update-service \
  --cluster ytdownload-cluster \
  --service ytdownload-backend \
  --desired-count 2 \
  --region us-east-1

# Increase frontend to 2 instances
aws ecs update-service \
  --cluster ytdownload-cluster \
  --service ytdownload-frontend \
  --desired-count 2 \
  --region us-east-1
```

## Cleanup

```bash
# Delete services
aws ecs delete-service \
  --cluster ytdownload-cluster \
  --service ytdownload-backend \
  --force --region us-east-1

aws ecs delete-service \
  --cluster ytdownload-cluster \
  --service ytdownload-frontend \
  --force --region us-east-1

# Delete cluster
aws ecs delete-cluster \
  --cluster ytdownload-cluster --region us-east-1

# Delete ECR repos
aws ecr delete-repository \
  --repository-name ytdownload-backend --force --region us-east-1

aws ecr delete-repository \
  --repository-name ytdownload-frontend --force --region us-east-1

# Delete log groups
aws logs delete-log-group --log-group-name /ecs/ytdownload-backend
aws logs delete-log-group --log-group-name /ecs/ytdownload-frontend
```

---

**Notes:**
- Services run in default VPC with public IPs for easy access during testing
- No load balancer - direct IP-based communication
- Services automatically find each other within the same security group
- Check logs in CloudWatch if anything fails
- Each service costs ~$5-10/month while running
