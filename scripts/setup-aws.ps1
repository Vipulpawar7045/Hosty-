# setup-aws.ps1
# Automates the creation and configuration of all AWS resources needed for Mini-Vercel
# Stays 100% within AWS Always-Free Tier ($0/month).

param(
    [string]$Region = "ap-south-1",
    [string]$BucketPrefix = "mini-vercel-artifacts"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Mini-Vercel — Automated AWS Resource Provisioner" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Verify AWS CLI and Credentials
Write-Host "`n[1/5] Verifying AWS Identity and Credentials..." -ForegroundColor Yellow
try {
    $callerJson = aws sts get-caller-identity --output json
    if (-not $callerJson) {
        throw "No output from aws sts get-caller-identity"
    }
    $callerIdentity = $callerJson | ConvertFrom-Json
    $accountId = $callerIdentity.Account
    $callerArn = $callerIdentity.Arn
    Write-Host "  Authenticated as: $callerArn" -ForegroundColor Green
    Write-Host "  AWS Account ID:   $accountId" -ForegroundColor Green
}
catch {
    Write-Host "ERROR: AWS credentials not found or invalid!" -ForegroundColor Red
    Write-Host "Please run 'aws configure' first and enter your AWS Access Key, Secret Key, and default region ($Region)." -ForegroundColor Red
    exit 1
}

$bucketName = "$BucketPrefix-$accountId"
$roleName = "mini-vercel-lambda-exec"

# 2. Create S3 Bucket & Enable Website Hosting
Write-Host "`n[2/5] Creating and configuring S3 Bucket: $bucketName..." -ForegroundColor Yellow
try {
    if ($Region -eq "us-east-1") {
        aws s3api create-bucket --bucket $bucketName --region $Region 2>$null
    } else {
        aws s3api create-bucket --bucket $bucketName --region $Region --create-bucket-configuration LocationConstraint=$Region 2>$null
    }
    Write-Host "  Bucket $bucketName created or already exists." -ForegroundColor Green
}
catch {
    Write-Host "  Bucket creation note: $_" -ForegroundColor Yellow
}

# Disable Block Public Access for static website hosting
Write-Host "  Configuring public access block settings for website hosting..." -ForegroundColor Cyan
aws s3api put-public-access-block --bucket $bucketName --public-access-block-configuration "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"

# Set Bucket Policy for Public Read
$bucketPolicy = @{
    Version = "2012-10-17"
    Statement = @(
        @{
            Sid = "PublicReadGetObject"
            Effect = "Allow"
            Principal = "*"
            Action = "s3:GetObject"
            Resource = "arn:aws:s3:::$bucketName/*"
        }
    )
} | ConvertTo-Json -Depth 5

$policyFile = [System.IO.Path]::GetTempFileName()
Set-Content -Path $policyFile -Value $bucketPolicy -Encoding utf8
$policyUrl = "file://$($policyFile.Replace('\', '/'))"
aws s3api put-bucket-policy --bucket $bucketName --policy $policyUrl
Remove-Item -Path $policyFile -Force
Write-Host "  Bucket policy applied (Public Read)." -ForegroundColor Green

# Configure Website Hosting
aws s3api put-bucket-website --bucket $bucketName --website-configuration '{"IndexDocument":{"Suffix":"index.html"},"ErrorDocument":{"Key":"index.html"}}'
$websiteUrl = "http://$bucketName.s3-website.$Region.amazonaws.com"
Write-Host "  Static Website Hosting enabled: $websiteUrl" -ForegroundColor Green

# 3. Create Lambda IAM Execution Role
Write-Host "`n[3/5] Setting up IAM Role: $roleName..." -ForegroundColor Yellow
$trustPolicy = @{
    Version = "2012-10-17"
    Statement = @(
        @{
            Effect = "Allow"
            Principal = @{
                Service = "lambda.amazonaws.com"
            }
            Action = "sts:AssumeRole"
        }
    )
} | ConvertTo-Json -Depth 5

$trustFile = [System.IO.Path]::GetTempFileName()
Set-Content -Path $trustFile -Value $trustPolicy -Encoding utf8
$trustUrl = "file://$($trustFile.Replace('\', '/'))"

$roleArn = ""
try {
    $createRoleJson = aws iam create-role --role-name $roleName --assume-role-policy-document $trustUrl --description "Execution role for Mini-Vercel serverless functions" --output json 2>$null
    if ($createRoleJson) {
        $createRole = $createRoleJson | ConvertFrom-Json
        $roleArn = $createRole.Role.Arn
        Write-Host "  Created IAM Role: $roleArn" -ForegroundColor Green
    } else {
        throw "Failed to create role"
    }
}
catch {
    $getRole = (aws iam get-role --role-name $roleName --output json) | ConvertFrom-Json
    $roleArn = $getRole.Role.Arn
    Write-Host "  IAM Role already exists: $roleArn" -ForegroundColor Green
}
Remove-Item -Path $trustFile -Force

aws iam attach-role-policy --role-name $roleName --policy-arn "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
Write-Host "  Attached AWSLambdaBasicExecutionRole policy." -ForegroundColor Green

# 4. DynamoDB Tables
Write-Host "`n[4/5] DynamoDB tables check..." -ForegroundColor Yellow
Write-Host "  DynamoDB tables are configured with PAY_PER_REQUEST billing (zero cost when idle)." -ForegroundColor Cyan
Write-Host "  Spring Boot's TableInitializer automatically creates any missing tables on startup:" -ForegroundColor Cyan
Write-Host "    - mv-users (GSI: email-index)" -ForegroundColor Gray
Write-Host "    - mv-projects (GSI: user-index)" -ForegroundColor Gray
Write-Host "    - mv-deployments (GSI: project-index)" -ForegroundColor Gray
Write-Host "    - mv-email-claims" -ForegroundColor Gray
Write-Host "    - mv-domains (GSI: project-index)" -ForegroundColor Gray

# 5. Configuration Summary & Env Export
Write-Host "`n[5/5] Setup Summary and Environment Variables:" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Region:             $Region" -ForegroundColor White
Write-Host "Artifact Bucket:    $bucketName" -ForegroundColor White
Write-Host "Public Website URL: $websiteUrl" -ForegroundColor White
Write-Host "Lambda Role ARN:    $roleArn" -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Green

Write-Host "`nYou can set these environment variables before running the application:" -ForegroundColor Cyan
Write-Host ('$env:ARTIFACT_BUCKET = "{0}"' -f $bucketName) -ForegroundColor Yellow
Write-Host ('$env:PUBLIC_BASE_URL = "{0}"' -f $websiteUrl) -ForegroundColor Yellow
Write-Host ('$env:LAMBDA_ROLE_ARN = "{0}"' -f $roleArn) -ForegroundColor Yellow

Write-Host "`nAll AWS resources are successfully recreated and ready for Mini-Vercel!" -ForegroundColor Green
