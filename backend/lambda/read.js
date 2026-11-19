const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, ScanCommand } = require('@aws-sdk/lib-dynamodb');
const client = new DynamoDBClient({});
const docClient = DynamoDBDocumentClient.from(client);

exports.handler = async () => {
    const [eventsResult, devicesResult] = await Promise.all([
        docClient.send(new ScanCommand({ TableName: process.env.TABLE_NAME })),
        docClient.send(new ScanCommand({ TableName: process.env.DEVICES_TABLE_NAME })),
    ]);

    const deviceMap = {};
    for (const d of devicesResult.Items ?? []) {
        deviceMap[d.deviceId] = d;
    }

    const sortedItems = (eventsResult.Items ?? [])
        .sort((a, b) => b.timestamp - a.timestamp)
        .map(ev => {
            const device = deviceMap[ev.deviceId];
            return {
                ...ev,
                userName: device?.userName ?? '未登録',
                deviceModel: device?.deviceModel ?? '-',
            };
        });

    return {
        statusCode: 200,
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify(sortedItems),
    };
};
