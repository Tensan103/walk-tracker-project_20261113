const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, ScanCommand } = require('@aws-sdk/lib-dynamodb');
const client = new DynamoDBClient({});
const docClient = DynamoDBDocumentClient.from(client);

exports.handler = async () => {
    // テキスト検証用なのでScan（全件取得）で簡易実装
    const params = { TableName: process.env.TABLE_NAME };
    const data = await docClient.send(new ScanCommand(params));

    // 最新順にソート
    const sortedItems = data.Items.sort((a, b) => b.timestamp - a.timestamp);

    return {
        statusCode: 200,
        headers: { "Access-Control-Allow-Origin": "*" },
        body: JSON.stringify(sortedItems),
    };
};