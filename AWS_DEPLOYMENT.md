# AWS ECS Fargate Deployment Guide

This guide provides step-by-step instructions to deploy the YouTube Downloader application to AWS ECS Fargate.

## Prerequisites

Before starting, you need:
- AWS Account with appropriate permissions
- AWS CLI installed and configured
- Docker installed locally
- Access to a Docker registry (AWS ECR recommended)

## Step-by-Step Deployment

### Step 1: Create AWS Resources

#### 1.1 Create ECR Repositories

```bash
# Create ECR repository for backend
aws ecr create-repository \
  --repository-name ytdownload-backend \
  --region us-east-1

# Create ECR repository for frontend
aws ecr create-repository \
  --repository-name ytdownload-frontend \
  --region us-east-1
```

**Expected output:**
```json
{
  "repository": {
    "repositoryUri": "123456789012.dkr.ecr.us-east-1.amazonaws.com/ytdownload-backend"
  }
}
```

Note the URI for later use.

#### 1.2 Create VPC and Security Groups

```bash
# Get default VPC ID
VPC_ID=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" \
  --query 'Vpcs[0].VpcId' --output text)

# Create security group for ALB
ALB_SG=$(aws ec2 create-security-group \
  --group-name ytdownload-alb-sg \
  --description "Security group for ALB" \
  --vpc-id $VPC_ID \
  --query 'GroupId' --output text)

# Create security group for ECS
ECS_SG=$(aws ec2 create-security-group \
  --group-name ytdownload-ecs-sg \
  --description "Security group for ECS tasks" \
  --vpc-id $VPC_ID \
  --query 'GroupId' --output text)

# Allow HTTP from ALB to ECS
aws ec2 authorize-security-group-ingress \
  --group-id $ECS_SG \
  --protocol tcp --port 80 \
  --source-security-group-id $ALB_SG

# Allow HTTPS for ALB
aws ec2 authorize-security-group-ingress \
  --group-id $ALB_SG \
  --protocol tcp --port 443 \
  --cidr 0.0.0.0/0

# Allow HTTP for ALB
aws ec2 authorize-security-group-ingress \
  --group-id $ALB_SG \
  --protocol tcp --port 80 \
  --cidr 0.0.0.0/0
```

#### 1.3 Create CloudWatch Log Groups

```bash
# Backend logs
aws logs create-log-group \
  --log-group-name /ecs/ytdownload-backend

# Frontend logs
aws logs create-log-group \
  --log-group-name /ecs/ytdownload-frontend

# Set retention (7 days)
aws logs put-retention-policy \
  --log-group-name /ecs/ytdownload-backend \
  --retention-in-days 7

aws logs put-retention-policy \
  --log-group-name /ecs/ytdownload-frontend \
  --retention-in-days 7
```

#### 1.4 Create ECS Cluster

```bash
aws ecs create-cluster \
  --cluster-name ytdownload-cluster \
  --region us-east-1
```

### Step 2: Build and Push Docker Images

#### 2.1 Login to ECR

```bash
# Get account ID
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  $ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com
```

#### 2.2 Build and Push Backend Image

```bash
# Set variables
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=us-east-1
BACKEND_URI=$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/ytdownload-backend:latest

# Build backend image
docker build -f backend/Dockerfile -t ytdownload-backend:latest .

# Tag for ECR
docker tag ytdownload-backend:latest $BACKEND_URI

# Push to ECR
docker push $BACKEND_URI

echo "Backend image pushed: $BACKEND_URI"
```

#### 2.3 Build and Push Frontend Image

```bash
# Set variables
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=us-east-1
FRONTEND_URI=$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/ytdownload-frontend:latest

# Build frontend image
docker build -f frontend/Dockerfile -t ytdownload-frontend:latest .

# Tag for ECR
docker tag ytdownload-frontend:latest $FRONTEND_URI

# Push to ECR
docker push $FRONTEND_URI

echo "Frontend image pushed: $FRONTEND_URI"
```

### Step 3: Create ECS Task Definitions

#### 3.1 Create Backend Task Definition

```bash
# Set variables
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Create task definition
cat > /tmp/backend-task-def.json << 'EOF'
{
  "family": "ytdownload-backend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "containerDefinitions": [
    {
      "name": "ytdownload-backend",
      "image": "ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-backend:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/ytdownload-backend",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "environment": [
        {
          "name": "SERVER_PORT",
          "value": "8080"
        },
        {
          "name": "LOG_LEVEL",
          "value": "INFO"
        }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ],
  "executionRoleArn": "arn:aws:iam::ACCOUNT_ID:role/ecsTaskExecutionRole"
}
EOF

# Replace account ID
sed -i "s/ACCOUNT_ID/$ACCOUNT_ID/g" /tmp/backend-task-def.json

# Register task definition
aws ecs register-task-definition \
  --cli-input-json file:///tmp/backend-task-def.json
```

#### 3.2 Create Frontend Task Definition

```bash
# Set variables
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Create task definition
cat > /tmp/frontend-task-def.json << 'EOF'
{
  "family": "ytdownload-frontend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "containerDefinitions": [
    {
      "name": "ytdownload-frontend",
      "image": "ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-frontend:latest",
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
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost/ || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 10
      }
    }
  ],
  "executionRoleArn": "arn:aws:iam::ACCOUNT_ID:role/ecsTaskExecutionRole"
}
EOF

# Replace account ID
sed -i "s/ACCOUNT_ID/$ACCOUNT_ID/g" /tmp/frontend-task-def.json

# Register task definition
aws ecs register-task-definition \
  --cli-input-json file:///tmp/frontend-task-def.json
```

### Step 4: Create Application Load Balancer

#### 4.1 Create Target Groups

```bash
# Get default VPC ID
VPC_ID=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" \
  --query 'Vpcs[0].VpcId' --output text)

# Create backend target group
BACKEND_TG=$(aws elbv2 create-target-group \
  --name ytdownload-backend \
  --protocol HTTP \
  --port 8080 \
  --vpc-id $VPC_ID \
  --health-check-path /health \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)

# Create frontend target group
FRONTEND_TG=$(aws elbv2 create-target-group \
  --name ytdownload-frontend \
  --protocol HTTP \
  --port 80 \
  --vpc-id $VPC_ID \
  --health-check-path / \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)

echo "Backend target group: $BACKEND_TG"
echo "Frontend target group: $FRONTEND_TG"
```

#### 4.2 Create Application Load Balancer

```bash
# Get subnets
SUBNETS=$(aws ec2 describe-subnets \
  --filters "Name=vpc-id,Values=$VPC_ID" \
  --query 'Subnets[*].SubnetId' \
  --output text)

# Get ALB security group
ALB_SG=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=ytdownload-alb-sg" \
  --query 'SecurityGroups[0].GroupId' \
  --output text)

# Create ALB
ALB_ARN=$(aws elbv2 create-load-balancer \
  --name ytdownload-alb \
  --subnets $SUBNETS \
  --security-groups $ALB_SG \
  --query 'LoadBalancers[0].LoadBalancerArn' \
  --output text)

# Get ALB DNS
ALB_DNS=$(aws elbv2 describe-load-balancers \
  --load-balancer-arns $ALB_ARN \
  --query 'LoadBalancers[0].DNSName' \
  --output text)

echo "ALB DNS: $ALB_DNS"
```

#### 4.3 Create Listener Rules

```bash
# Create listener
LISTENER=$(aws elbv2 create-listener \
  --load-balancer-arn $ALB_ARN \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn=$FRONTEND_TG \
  --query 'Listeners[0].ListenerArn' \
  --output text)

# Add rule for /api/* to backend
aws elbv2 create-rule \
  --listener-arn $LISTENER \
  --priority 1 \
  --conditions Field=path-pattern,Values="/api/*" \
  --actions Type=forward,TargetGroupArn=$BACKEND_TG
```

### Step 5: Create ECS Services

#### 5.1 Create Backend Service

```bash
# Get ECS security group
ECS_SG=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=ytdownload-ecs-sg" \
  --query 'SecurityGroups[0].GroupId' \
  --output text)

# Get subnets
SUBNETS=$(aws ec2 describe-subnets \
  --filters "Name=vpc-id,Values=$VPC_ID" \
  --query 'Subnets[*].SubnetId' \
  --output text)

# Create service
aws ecs create-service \
  --cluster ytdownload-cluster \
  --service-name ytdownload-backend-service \
  --task-definition ytdownload-backend:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$ECS_SG],assignPublicIp=ENABLED}" \
  --load-balancers targetGroupArn=$BACKEND_TG,containerName=ytdownload-backend,containerPort=8080 \
  --region us-east-1
```

#### 5.2 Create Frontend Service

```bash
# Create service
aws ecs create-service \
  --cluster ytdownload-cluster \
  --service-name ytdownload-frontend-service \
  --task-definition ytdownload-frontend:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$ECS_SG],assignPublicIp=ENABLED}" \
  --load-balancers targetGroupArn=$FRONTEND_TG,containerName=ytdownload-frontend,containerPort=80 \
  --region us-east-1
```

### Step 6: Verify Deployment

```bash
# Get ALB DNS
ALB_DNS=$(aws elbv2 describe-load-balancers \
  --load-balancer-arns $ALB_ARN \
  --query 'LoadBalancers[0].DNSName' \
  --output text)

# Test backend health
curl http://$ALB_DNS/health

# Test frontend
curl http://$ALB_DNS/

echo "Application is live at: http://$ALB_DNS"
```

### Step 7: Setup Auto-Scaling (Optional but Recommended)

```bash
# Create auto-scaling target for backend
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/ytdownload-cluster/ytdownload-backend-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 1 \
  --max-capacity 4

# Create scaling policy for backend
aws application-autoscaling put-scaling-policy \
  --policy-name ytdownload-backend-scaling \
  --service-namespace ecs \
  --resource-id service/ytdownload-cluster/ytdownload-backend-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration \
    TargetValue=70.0,PredefinedMetricSpecification={PredefinedMetricType=ECSServiceAverageCPUUtilization}
```

## Estimated Costs

```
Component                Monthly Cost
────────────────────────────────────
ECS Fargate CPU         ~$15-20
ECS Fargate Memory      ~$15-20
Data Transfer           ~$5-10
Application LB          ~$15-20
CloudWatch Logs         ~$2-5
────────────────────────────────────
TOTAL ESTIMATE          ~$50-75/month
```

## Monitoring and Logs

### View Logs

```bash
# Backend logs
aws logs tail /ecs/ytdownload-backend --follow

# Frontend logs
aws logs tail /ecs/ytdownload-frontend --follow
```

### Create CloudWatch Dashboard

```bash
# Dashboard already created, view in AWS Console:
# https://console.aws.amazon.com/cloudwatch/
```

### Setup Alarms

```bash
# Backend health alarm
aws cloudwatch put-metric-alarm \
  --alarm-name ytdownload-backend-unhealthy \
  --alarm-description "Alert when backend tasks are unhealthy" \
  --metric-name HealthyHostCount \
  --namespace AWS/ApplicationELB \
  --statistic Average \
  --period 60 \
  --threshold 1 \
  --comparison-operator LessThanThreshold \
  --evaluation-periods 2
```

## Updating Deployment

### Push New Image

```bash
# Build and push new backend image
docker build -f backend/Dockerfile -t ytdownload-backend:v2 .
docker tag ytdownload-backend:v2 $ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-backend:v2
docker push $ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/ytdownload-backend:v2

# Register new task definition
aws ecs register-task-definition \
  --cli-input-json file:///tmp/backend-task-def-v2.json

# Update service
aws ecs update-service \
  --cluster ytdownload-cluster \
  --service ytdownload-backend-service \
  --task-definition ytdownload-backend:2 \
  --force-new-deployment
```

## Troubleshooting

### Service won't start
```bash
# Check task logs
aws ecs describe-tasks \
  --cluster ytdownload-cluster \
  --tasks <task-id> \
  --query 'tasks[0].stoppedReason'

# Check ECS service events
aws ecs describe-services \
  --cluster ytdownload-cluster \
  --services ytdownload-backend-service \
  --query 'services[0].events'
```

### Health check failing
```bash
# SSH into task and test
# Check port is open
# Check application is running
# Review logs in CloudWatch
```

## Security Best Practices

1. **Use IAM Roles**: Already configured with ecsTaskExecutionRole
2. **Enable SSL/TLS**: Use AWS Certificate Manager with ALB
3. **VPC Configuration**: Services in private subnets (optional)
4. **Secrets Management**: Use AWS Secrets Manager for sensitive data
5. **Image Scanning**: Enable ECR image scanning

## Cleanup (When Done)

```bash
# Delete ECS services
aws ecs delete-service \
  --cluster ytdownload-cluster \
  --service ytdownload-backend-service \
  --force

aws ecs delete-service \
  --cluster ytdownload-cluster \
  --service ytdownload-frontend-service \
  --force

# Delete ALB
aws elbv2 delete-load-balancer --load-balancer-arn $ALB_ARN

# Delete ECS cluster
aws ecs delete-cluster --cluster ytdownload-cluster

# Delete ECR repositories
aws ecr delete-repository \
  --repository-name ytdownload-backend \
  --force

aws ecr delete-repository \
  --repository-name ytdownload-frontend \
  --force

# Delete CloudWatch log groups
aws logs delete-log-group --log-group-name /ecs/ytdownload-backend
aws logs delete-log-group --log-group-name /ecs/ytdownload-frontend
```

## Support

For issues, check:
1. CloudWatch Logs for application errors
2. ECS task events for deployment issues
3. ALB target group health
4. Security group rules for port access

---

**Status**: ✅ Ready for AWS deployment  
**Last Updated**: December 9, 2025
