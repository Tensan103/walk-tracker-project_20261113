const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, ScanCommand, PutCommand, UpdateCommand, DeleteCommand } = require('@aws-sdk/lib-dynamodb');

const client = new DynamoDBClient({});
const docClient = DynamoDBDocumentClient.from(client);
const TABLE_NAME = process.env.TABLE_NAME;

const HEADERS = {
    'Access-Control-Allow-Origin': '*',
    'Content-Type': 'application/json',
};

const response = (statusCode, body) => ({
    statusCode,
    headers: HEADERS,
    body: JSON.stringify(body),
});

exports.handler = async (event) => {
    try {
        switch (event.httpMethod) {
            case 'GET':    return await listDevices();
            case 'POST':   return await createDevice(JSON.parse(event.body || '{}'));
            case 'PUT':    return await updateDevice(JSON.parse(event.body || '{}'));
            case 'DELETE': return await deleteDevice(event.pathParameters?.deviceId);
            default:       return response(405, { error: 'Method Not Allowed' });
        }
    } catch (err) {
        console.error('devicesApi error:', err);
        return response(500, { error: err.message });
    }
};

async function listDevices() {
    const data = await docClient.send(new ScanCommand({ TableName: TABLE_NAME }));
    return response(200, data.Items ?? []);
}

async function createDevice(body) {
    const { deviceId, userName, deviceModel, status } = body;
    if (!deviceId) return response(400, { error: 'deviceId is required' });

    const item = {
        deviceId,
        userName: userName ?? '',
        deviceModel: deviceModel ?? '',
        status: status ?? 'ACTIVE',
        createdAt: Date.now(),
    };
    await docClient.send(new PutCommand({ TableName: TABLE_NAME, Item: item }));
    return response(201, item);
}

async function updateDevice(body) {
    const { deviceId, userName, deviceModel, status } = body;
    if (!deviceId) return response(400, { error: 'deviceId is required in request body' });

    const result = await docClient.send(new UpdateCommand({
        TableName: TABLE_NAME,
        Key: { deviceId },
        UpdateExpression: 'SET userName = :userName, deviceModel = :deviceModel, #st = :status, updatedAt = :updatedAt',
        ExpressionAttributeNames: { '#st': 'status' },  // status はDynamoDB予約語
        ExpressionAttributeValues: {
            ':userName': userName ?? '',
            ':deviceModel': deviceModel ?? '',
            ':status': status ?? 'ACTIVE',
            ':updatedAt': Date.now(),
        },
        ReturnValues: 'ALL_NEW',
    }));
    return response(200, result.Attributes);
}

async function deleteDevice(deviceId) {
    if (!deviceId) return response(400, { error: 'deviceId is required in path' });
    await docClient.send(new DeleteCommand({ TableName: TABLE_NAME, Key: { deviceId } }));
    return response(200, { deleted: deviceId });
}
