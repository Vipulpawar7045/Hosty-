# test-aws-connection.ps1
# Quickly checks whether AWS credentials, S3, DynamoDB, and IAM are properly accessible.

$ErrorActionPreference = "Continue"

Write-Host "Checking AWS Connectivity and Permissions..." -ForegroundColor Cyan

# 1. Check Caller Identity
$caller = aws sts get-caller-identity --output json 2>$null
if (-not $caller) {
    Write-Host "[-] AWS CLI is NOT authenticated. Run 'aws configure' to set up Access Key & Secret." -ForegroundColor Red
    exit 1
}
$callerObj = $caller | ConvertFrom-Json
Write-Host "[+] Authenticated as: $($callerObj.Arn)" -ForegroundColor Green

# 2. Check S3
$buckets = aws s3 ls 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[+] S3 Access: OK" -ForegroundColor Green
} else {
    Write-Host "[-] S3 Access: Failed (check IAM permissions)" -ForegroundColor Yellow
}

# 3. Check DynamoDB
$tables = aws dynamodb list-tables --region ap-south-1 --output json 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[+] DynamoDB Access (ap-south-1): OK" -ForegroundColor Green
    $tableList = ($tables | ConvertFrom-Json).TableNames
    Write-Host "    Found tables: $($tableList -join ', ')" -ForegroundColor Gray
} else {
    Write-Host "[-] DynamoDB Access: Failed" -ForegroundColor Yellow
}

# 4. Check IAM Role
$role = aws iam get-role --role-name "mini-vercel-lambda-exec" 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "[+] IAM Role 'mini-vercel-lambda-exec': Found" -ForegroundColor Green
} else {
    Write-Host "[-] IAM Role 'mini-vercel-lambda-exec': Not found (run setup-aws.ps1)" -ForegroundColor Yellow
}

Write-Host "`nTest complete." -ForegroundColor Cyan
