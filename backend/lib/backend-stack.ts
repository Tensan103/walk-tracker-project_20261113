import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';

export class BackendStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // 1. DynamoDB: 歩行イベントテーブル
    const table = new dynamodb.Table(this, 'WalkEventsTable', {
      partitionKey: { name: 'deviceId', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'timestamp', type: dynamodb.AttributeType.NUMBER },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // 2. Lambda: データ書き込み用
    const writeLambda = new lambda.Function(this, 'WriteEventLambda', {
      runtime: lambda.Runtime.NODEJS_22_X,
      handler: 'write.handler',
      code: lambda.Code.fromAsset('lambda'),
      environment: { TABLE_NAME: table.tableName },
    });
    table.grantWriteData(writeLambda);

    // 3. Lambda: データ読み込み用
    const readLambda = new lambda.Function(this, 'ReadEventLambda', {
      runtime: lambda.Runtime.NODEJS_22_X,
      handler: 'read.handler',
      code: lambda.Code.fromAsset('lambda'),
      environment: { TABLE_NAME: table.tableName },
    });
    table.grantReadData(readLambda);

    // 4. DynamoDB: デバイステーブル
    const devicesTable = new dynamodb.Table(this, 'DevicesTable', {
      partitionKey: { name: 'deviceId', type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // 5. Lambda: Devices CRUD
    const devicesApiLambda = new lambda.Function(this, 'DevicesApiLambda', {
      runtime: lambda.Runtime.NODEJS_22_X,
      handler: 'devicesApi.handler',
      code: lambda.Code.fromAsset('lambda'),
      environment: { TABLE_NAME: devicesTable.tableName },
    });
    devicesTable.grantReadWriteData(devicesApiLambda);
    devicesTable.grantReadData(readLambda);
    readLambda.addEnvironment('DEVICES_TABLE_NAME', devicesTable.tableName);

    // 6. Cognito User Pool（管理画面用）
    const userPool = new cognito.UserPool(this, 'AdminUserPool', {
      selfSignUpEnabled: false,
      signInAliases: { email: true },
      passwordPolicy: {
        minLength: 8,
        requireUppercase: true,
        requireLowercase: true,
        requireDigits: true,
        requireSymbols: false,
      },
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const userPoolClient = userPool.addClient('AdminWebClient', {
      generateSecret: false,
      authFlows: { userSrp: true, userPassword: true },
    });

    const cognitoAuthorizer = new apigateway.CognitoUserPoolsAuthorizer(this, 'CognitoAuthorizer', {
      cognitoUserPools: [userPool],
    });

    // 7. API Gateway
    const api = new apigateway.RestApi(this, 'WalkEventsApi', {
      defaultCorsPreflightOptions: {
        allowOrigins: apigateway.Cors.ALL_ORIGINS,
        allowMethods: apigateway.Cors.ALL_METHODS,
        allowHeaders: [
          ...apigateway.Cors.DEFAULT_HEADERS,
          'Authorization',
          'x-api-key',
        ],
      },
    });

    // 認証エラー(401/403)でもCORSヘッダーが返るようにゲートウェイレスポンスを設定
    api.addGatewayResponse('Default4xxResponse', {
      type: apigateway.ResponseType.DEFAULT_4XX,
      responseHeaders: { 'Access-Control-Allow-Origin': "'*'" },
    });
    api.addGatewayResponse('Default5xxResponse', {
      type: apigateway.ResponseType.DEFAULT_5XX,
      responseHeaders: { 'Access-Control-Allow-Origin': "'*'" },
    });

    // 8. API Key + Usage Plan（Android用 POST /events）
    const apiKey = api.addApiKey('AndroidApiKey', {
      apiKeyName: 'WalkTrackerAndroidKey',
    });
    const usagePlan = api.addUsagePlan('AndroidUsagePlan', {
      name: 'WalkTrackerAndroidPlan',
      throttle: { rateLimit: 10, burstLimit: 20 },
      quota: {
        limit: 10000,
        period: apigateway.Period.MONTH,
      },
    });
    usagePlan.addApiStage({ stage: api.deploymentStage });
    usagePlan.addApiKey(apiKey);

    // 9. エンドポイント定義
    const events = api.root.addResource('events');

    // POST /events: APIキー必須（Androidアプリ用）
    events.addMethod('POST', new apigateway.LambdaIntegration(writeLambda), {
      apiKeyRequired: true,
    });

    // GET /events: Cognito認証必須（管理画面用）
    events.addMethod('GET', new apigateway.LambdaIntegration(readLambda), {
      authorizer: cognitoAuthorizer,
      authorizationType: apigateway.AuthorizationType.COGNITO,
    });

    const devices = api.root.addResource('devices');

    // GET /devices: Cognito認証必須
    devices.addMethod('GET', new apigateway.LambdaIntegration(devicesApiLambda), {
      authorizer: cognitoAuthorizer,
      authorizationType: apigateway.AuthorizationType.COGNITO,
    });

    // POST /devices: Cognito認証必須
    devices.addMethod('POST', new apigateway.LambdaIntegration(devicesApiLambda), {
      authorizer: cognitoAuthorizer,
      authorizationType: apigateway.AuthorizationType.COGNITO,
    });

    // PUT /devices: Cognito認証必須
    devices.addMethod('PUT', new apigateway.LambdaIntegration(devicesApiLambda), {
      authorizer: cognitoAuthorizer,
      authorizationType: apigateway.AuthorizationType.COGNITO,
    });

    // DELETE /devices/{deviceId}: Cognito認証必須
    const deviceWithId = devices.addResource('{deviceId}');
    deviceWithId.addMethod('DELETE', new apigateway.LambdaIntegration(devicesApiLambda), {
      authorizer: cognitoAuthorizer,
      authorizationType: apigateway.AuthorizationType.COGNITO,
    });

    // 10. S3 + CloudFront（Reactアプリ静的ホスティング）
    const webBucket = new s3.Bucket(this, 'ViewerAppBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });
    const distribution = new cloudfront.Distribution(this, 'ViewerDistribution', {
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(webBucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
      },
      defaultRootObject: 'index.html',
      errorResponses: [
        {
          httpStatus: 403,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
        },
        {
          httpStatus: 404,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
        },
      ],
    });

    // 11. CfnOutput
    new cdk.CfnOutput(this, 'ApiUrl', { value: api.url });
    new cdk.CfnOutput(this, 'UserPoolId', { value: userPool.userPoolId });
    new cdk.CfnOutput(this, 'UserPoolClientId', { value: userPoolClient.userPoolClientId });
    new cdk.CfnOutput(this, 'ApiKeyId', { value: apiKey.keyId });
    new cdk.CfnOutput(this, 'WebBucketName', { value: webBucket.bucketName });
    new cdk.CfnOutput(this, 'CloudFrontDistributionId', { value: distribution.distributionId });
    new cdk.CfnOutput(this, 'WebUrl', { value: `https://${distribution.distributionDomainName}` });
  }
}
