const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, PutCommand } = require('@aws-sdk/lib-dynamodb');
const client = new DynamoDBClient({});
const docClient = DynamoDBDocumentClient.from(client);

exports.handler = async (event) => {
    const body = JSON.parse(event.body ?? '{}');
    const { deviceId, timestamp, category } = body;

    if (!deviceId || !timestamp || !category) {
        return {
            statusCode: 400,
            headers: { 'Access-Control-Allow-Origin': '*' },
            body: JSON.stringify({ error: 'deviceId, timestamp, category are required' }),
        };
    }

    await docClient.send(new PutCommand({
        TableName: process.env.TABLE_NAME,
        Item: { deviceId, timestamp, category },
    }));

    return {
        statusCode: 200,
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify({ ok: true }),
    };
};
