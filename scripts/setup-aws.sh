#!/usr/bin/env bash
# setup-aws.sh
# Automates the creation and configuration of all AWS resources needed for Mini-Vercel
# Stays 100% within AWS Always-Free Tier ($0/month).

set -euo pipefail

REGION="${1:-ap-south-1}"
BUCKET_PREFIX="${2:-mini-vercel-artifacts}"

echo "=========================================================="
echo "  Mini-Vercel — Automated AWS Resource Provisioner"
echo "=========================================================="

echo "[1/4] Verifying AWS Identity and Credentials..."
ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text)
CALLER_ARN=$(aws sts get-caller-identity --query "Arn" --output text)
echo "  Authenticated as: ${CALLER_ARN}"
echo "  AWS Account ID:   ${ACCOUNT_ID}"

BUCKET_NAME="${BUCKET_PREFIX}-${ACCOUNT_ID}"
ROLE_NAME="mini-vercel-lambda-exec"

echo "[2/4] Setting up S3 Bucket: ${BUCKET_NAME}..."
if [ "${REGION}" = "us-east-1" ]; then
  aws s3api create-bucket --bucket "${BUCKET_NAME}" --region "${REGION}" 2>/dev/null || true
else
  aws s3api create-bucket --bucket "${BUCKET_NAME}" --region "${REGION}" \
    --create-bucket-configuration LocationConstraint="${REGION}" 2>/dev/null || true
fi

# Disable Block Public Access
aws s3api put-public-access-block --bucket "${BUCKET_NAME}" \
  --public-access-block-configuration "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"

# Set Bucket Policy
cat <<EOF > /tmp/bucket-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadGetObject",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::${BUCKET_NAME}/*"
    }
  ]
}
EOF
aws s3api put-bucket-policy --bucket "${BUCKET_NAME}" --policy file:///tmp/bucket-policy.json
rm -f /tmp/bucket-policy.json

# Enable Website Hosting
aws s3api put-bucket-website --bucket "${BUCKET_NAME}" \
  --website-configuration "IndexDocument={Suffix=index.html},ErrorDocument={Key=index.html}"
WEBSITE_URL="http://${BUCKET_NAME}.s3-website.${REGION}.amazonaws.com"
echo "  S3 Static Website URL: ${WEBSITE_URL}"

echo "[3/4] Setting up IAM Role: ${ROLE_NAME}..."
cat <<EOF > /tmp/trust-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "lambda.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

ROLE_ARN=$(aws iam get-role --role-name "${ROLE_NAME}" --query "Role.Arn" --output text 2>/dev/null || true)
if [ -z "${ROLE_ARN}" ]; then
  ROLE_ARN=$(aws iam create-role --role-name "${ROLE_NAME}" \
    --assume-role-policy-document file:///tmp/trust-policy.json \
    --query "Role.Arn" --output text)
  echo "  Created IAM Role: ${ROLE_ARN}"
else
  echo "  IAM Role already exists: ${ROLE_ARN}"
fi
rm -f /tmp/trust-policy.json

aws iam attach-role-policy --role-name "${ROLE_NAME}" \
  --policy-arn "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"

echo "[4/4] Summary & Environment Variables:"
echo "=========================================================="
echo "export ARTIFACT_BUCKET=\"${BUCKET_NAME}\""
echo "export PUBLIC_BASE_URL=\"${WEBSITE_URL}\""
echo "export LAMBDA_ROLE_ARN=\"${ROLE_ARN}\""
echo "=========================================================="
echo "All AWS resources are provisioned and ready!"
