import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';

export class BackendStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // 1. DynamoDBテーブル (テスト用なのでリソース削除時にテーブルも削除)
    const table = new dynamodb.Table(this, 'WalkEventsTable', {
      partitionKey: { name: 'deviceId', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'timestamp', type: dynamodb.AttributeType.NUMBER },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // 2. Lambda: データ書き込み用
    const writeLambda = new lambda.Function(this, 'WriteEventLambda', {
      runtime: lambda.Runtime.NODEJS_18_X,
      handler: 'write.handler',
      code: lambda.Code.fromAsset('lambda'), // lambdaディレクトリにコードを配置
      environment: { TABLE_NAME: table.tableName },
    });
    table.grantWriteData(writeLambda);

    // 3. Lambda: データ読み込み用 (ビューワー向け)
    const readLambda = new lambda.Function(this, 'ReadEventLambda', {
      runtime: lambda.Runtime.NODEJS_18_X,
      handler: 'read.handler',
      code: lambda.Code.fromAsset('lambda'),
      environment: { TABLE_NAME: table.tableName },
    });
    table.grantReadData(readLambda);

    // 4. API Gateway (CORS許可: Reactから叩けるようにする)
    const api = new apigateway.RestApi(this, 'WalkEventsApi', {
      defaultCorsPreflightOptions: {
        allowOrigins: apigateway.Cors.ALL_ORIGINS,
        allowMethods: apigateway.Cors.ALL_METHODS,
      },
    });

    // エンドポイント作成 (/events)
    const events = api.root.addResource('events');
    events.addMethod('POST', new apigateway.LambdaIntegration(writeLambda));
    events.addMethod('GET', new apigateway.LambdaIntegration(readLambda));

    // 5. 閲覧用Reactアプリを配置するS3とCloudFront
    const websiteBucket = new s3.Bucket(this, 'ViewerAppBucket', {
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });
    const distribution = new cloudfront.Distribution(this, 'ViewerDistribution', {
      defaultBehavior: {
        origin: new origins.S3Origin(websiteBucket)
      },
      defaultRootObject: 'index.html',
    });

    // --- デプロイ結果の出力 ---
    new cdk.CfnOutput(this, 'ApiUrl', { value: api.url });
    new cdk.CfnOutput(this, 'CloudFrontUrl', { value: `https://${distribution.distributionDomainName}` });
  }
}